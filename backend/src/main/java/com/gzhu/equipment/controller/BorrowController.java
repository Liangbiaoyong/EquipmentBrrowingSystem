package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gzhu.equipment.common.R;
import com.gzhu.equipment.dto.ApprovalRequestDTO;
import com.gzhu.equipment.dto.BorrowRequestDTO;
import com.gzhu.equipment.entity.BorrowRecord;
import com.gzhu.equipment.security.JwtUserPrincipal;
import com.gzhu.equipment.entity.Attachment;
import com.gzhu.equipment.mapper.AttachmentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzhu.equipment.entity.ApprovalLog;
import com.gzhu.equipment.mapper.ApprovalLogMapper;
import com.gzhu.equipment.mapper.BorrowRecordMapper;
import com.gzhu.equipment.service.BorrowService;
import com.gzhu.equipment.service.MinioFileService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 借用审批控制器
 *
 * POST   /borrows              → 提交借用申请
 * GET    /borrows/my           → 我的借用列表
 * GET    /borrows/{id}         → 借用详情
 * POST   /borrows/{id}/cancel  → 取消借用
 *
 * GET    /borrows/pending/first   → 一级待审批
 * GET    /borrows/pending/second  → 二级待审批
 * POST   /borrows/approve         → 审批操作
 *
 * POST   /borrows/{id}/return  → 归还登记
 * GET    /borrows/overdue      → 逾期列表
 */
@Slf4j
@RestController
@RequestMapping("/borrows")
@RequiredArgsConstructor
@Api(tags = "借用审批管理")
public class BorrowController {

    private final BorrowService borrowService;
    private final MinioFileService minioFileService;
    private final AttachmentMapper attachmentMapper;
    private final ApprovalLogMapper approvalLogMapper;
    private final BorrowRecordMapper borrowRecordMapper;
    private final com.gzhu.equipment.mapper.DeviceMapper deviceMapper;

    // ==================== 借用申请 ====================

    @PostMapping
    @ApiOperation("提交借用申请（支持多设备）")
    @PreAuthorize("hasAnyAuthority('borrow:create','borrow:my')")
    public R<List<BorrowRecord>> submitBorrow(@Valid @RequestBody BorrowRequestDTO dto) {
        Long userId = getCurrentUserId();
        try {
            List<BorrowRecord> records = borrowService.submitBorrow(dto, userId);
            return R.ok("已提交 " + records.size() + " 条借用申请", records);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (Exception e) {
            log.error("借用申请提交失败", e);
            return R.fail("提交失败: " + (e.getMessage() != null ? e.getMessage() : "服务器内部错误"));
        }
    }

    @GetMapping("/my")
    @ApiOperation("我的借用列表（支持排序，含设备名称/资产编号）")
    @PreAuthorize("hasAuthority('borrow:my')")
    public R<IPage<BorrowRecord>> myBorrows(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, defaultValue = "desc") String order) {
        Long userId = getCurrentUserId();
        var w = new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getUserId, userId);
        if (status != null && !status.isEmpty()) w.eq(BorrowRecord::getStatus, status);
        // 排序
        boolean asc = "asc".equalsIgnoreCase(order);
        if ("id".equals(sort)) w.orderBy(true, asc, BorrowRecord::getId);
        else if ("startTime".equals(sort)) w.orderBy(true, asc, BorrowRecord::getStartTime);
        else if ("endTime".equals(sort)) w.orderBy(true, asc, BorrowRecord::getEndTime);
        else if ("overdueDays".equals(sort)) w.orderBy(true, asc, BorrowRecord::getOverdueDays);
        else w.orderByDesc(BorrowRecord::getId);
        IPage<BorrowRecord> result = borrowService.page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), w);
        // 填充设备名称/资产编号
        for (BorrowRecord r : result.getRecords()) {
            var d = deviceMapper.selectById(r.getDeviceId());
            if (d != null) { r.setDeviceName(d.getName()); r.setDeviceAssetNo(d.getAssetNo()); }
        }
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @ApiOperation("借用详情（含设备名称/资产编号）")
    @PreAuthorize("hasAuthority('borrow:view')")
    public R<BorrowRecord> getDetail(@PathVariable Long id) {
        BorrowRecord record = borrowService.getDetail(id);
        if (record == null) return R.fail(404, "借用单不存在");
        // 填充设备名称/资产编号
        var device = deviceMapper.selectById(record.getDeviceId());
        if (device != null) { record.setDeviceName(device.getName()); record.setDeviceAssetNo(device.getAssetNo()); }
        // 填充用户名
        var user = sysUserMapper.selectById(record.getUserId());
        if (user != null) record.setUserName(user.getRealName() != null ? user.getRealName() : user.getUsername());
        return R.ok(record);
    }

    @PostMapping("/{id}/cancel")
    @ApiOperation("取消借用申请")
    @PreAuthorize("hasAuthority('borrow:create')")
    public R<String> cancelBorrow(@PathVariable Long id) {
        try {
            borrowService.cancelBorrow(id, getCurrentUserId());
            return R.ok("已取消");
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    // ==================== 审批 ====================

    @GetMapping("/pending/first")
    @ApiOperation("初审待审批列表（教师/admin）")
    @PreAuthorize("hasAuthority('approval:first')")
    public R<java.util.Map<String,Object>> pendingFirst(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pg = borrowService.pendingApprovals(getCurrentUserId(), 1, page, size);
        var enriched = enrichNames(pg.getRecords());
        java.util.Map<String,Object> r = new LinkedHashMap<>();
        r.put("records", enriched); r.put("total", pg.getTotal()); r.put("page", page); r.put("size", size);
        return R.ok(r);
    }

    @GetMapping("/pending/second")
    @ApiOperation("终审待审批列表（实验室管理员/admin）")
    @PreAuthorize("hasAuthority('approval:second')")
    public R<java.util.Map<String,Object>> pendingSecond(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pg = borrowService.pendingApprovals(getCurrentUserId(), 2, page, size);
        var enriched = enrichNames(pg.getRecords());
        java.util.Map<String,Object> r = new LinkedHashMap<>();
        r.put("records", enriched); r.put("total", pg.getTotal()); r.put("page", page); r.put("size", size);
        return R.ok(r);
    }

    @PostMapping("/approve")
    @ApiOperation("审批操作（通过/驳回）")
    @PreAuthorize("hasAnyAuthority('approval:first','approval:second')")
    public R<BorrowRecord> approve(@Valid @RequestBody ApprovalRequestDTO dto) {
        try {
            BorrowRecord record = borrowService.approve(dto, getCurrentUserId());
            String msg = Boolean.TRUE.equals(dto.getApproved()) ? "审批通过" : "已驳回";
            return R.ok(msg, record);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/{id}/images")
    @ApiOperation("获取借用相关图片（借用照片+归还照片）")
    @PreAuthorize("hasAuthority('borrow:my')")
    public R<java.util.Map<String, Object>> getImages(@PathVariable Long id) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        BorrowRecord record = borrowService.getById(id);
        if (record != null) {
            result.put("pickupImage", record.getPickupImage());
        }
        // 从附件表查询
        var borrowImgs = attachmentMapper.selectList(
            new LambdaQueryWrapper<Attachment>().eq(Attachment::getBizId, id).eq(Attachment::getBizType, "BORROW_IMG"));
        var returnImgs = attachmentMapper.selectList(
            new LambdaQueryWrapper<Attachment>().eq(Attachment::getBizId, id).eq(Attachment::getBizType, "RETURN_IMG"));
        result.put("borrowImages", borrowImgs.stream().map(Attachment::getFileUrl).collect(java.util.stream.Collectors.toList()));
        result.put("returnImages", returnImgs.stream().map(Attachment::getFileUrl).collect(java.util.stream.Collectors.toList()));
        return R.ok(result);
    }

    // ==================== 取走登记 ====================

    @PostMapping("/{id}/pickup")
    @ApiOperation("登记取走设备（记录取走时间+上传借用照片）")
    @PreAuthorize("hasAuthority('borrow:my')")
    public R<BorrowRecord> pickupDevice(@PathVariable Long id,
                                         @RequestParam(value = "file", required = false) MultipartFile file) {
        BorrowRecord record = borrowService.getById(id);
        if (record == null) return R.fail(404, "借用单不存在");
        if (!"APPROVED".equals(record.getStatus()) && !"BORROWING".equals(record.getStatus()))
            return R.fail("仅已通过或借用中的单据可登记取走");

        record.setPickupTime(LocalDateTime.now());
        if (record.getStatus().equals("APPROVED")) {
            record.setStatus("BORROWING");
        }

        // 上传借用照片（如有）
        if (file != null && !file.isEmpty()) {
            String objectPath = minioFileService.uploadImage(file, "BORROW");
            record.setPickupImage(objectPath);
            // 同时记录到附件表
            Attachment att = new Attachment();
            att.setBizType("BORROW_IMG");
            att.setBizId(record.getId());
            att.setFileUrl(objectPath);
            att.setFileSize(file.getSize());
            att.setUploadTime(LocalDateTime.now());
            att.setExpireTime(LocalDateTime.now().plusMonths(6));
            attachmentMapper.insert(att);
        }

        borrowService.updateById(record);
        log.info("取走登记: borrowId={} pickupTime={}", id, record.getPickupTime());
        return R.ok("取走登记成功", record);
    }

    @PostMapping("/{id}/upload-image")
    @ApiOperation("上传借用/归还照片（压缩至1MB以内）")
    @PreAuthorize("hasAuthority('borrow:my')")
    public R<String> uploadBorrowImage(@PathVariable Long id,
                                        @RequestParam("file") MultipartFile file,
                                        @RequestParam(defaultValue = "BORROW") String bizType) {
        BorrowRecord record = borrowService.getById(id);
        if (record == null) return R.fail(404, "借用单不存在");

        try {
            String objectPath = minioFileService.uploadImage(file, bizType);

            // 根据业务类型更新对应字段
            if ("BORROW".equals(bizType)) {
                record.setPickupImage(objectPath);
                borrowService.updateById(record);
            }

            // 记录附件
            Attachment att = new Attachment();
            att.setBizType(bizType + "_IMG");
            att.setBizId(id);
            att.setFileUrl(objectPath);
            att.setFileSize(file.getSize());
            att.setUploadTime(LocalDateTime.now());
            att.setExpireTime(LocalDateTime.now().plusMonths(6));
            attachmentMapper.insert(att);

            return R.ok(objectPath);
        } catch (Exception e) {
            return R.fail("图片上传失败: " + e.getMessage());
        }
    }

    // ==================== 归还 ====================

    @PostMapping("/{id}/return")
    @ApiOperation("归还登记（支持多张归还照片）")
    @PreAuthorize("hasAuthority('borrow:return')")
    public R<BorrowRecord> returnDevice(@PathVariable Long id,
                                         @RequestParam(required = false) String damageReport,
                                         @RequestParam(value = "file", required = false) MultipartFile file,
                                         @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        try {
            BorrowRecord record = borrowService.returnDevice(id, getCurrentUserId(), damageReport);

            // 上传归还照片（支持单张和多张）
            java.util.List<MultipartFile> allFiles = new java.util.ArrayList<>();
            if (file != null && !file.isEmpty()) allFiles.add(file);
            if (files != null) allFiles.addAll(files.stream().filter(f -> f != null && !f.isEmpty()).collect(java.util.stream.Collectors.toList()));

            for (MultipartFile f : allFiles) {
                try {
                    String objectPath = minioFileService.uploadImage(f, "RETURN");
                    Attachment att = new Attachment();
                    att.setBizType("RETURN_IMG");
                    att.setBizId(record.getId());
                    att.setFileUrl(objectPath);
                    att.setFileSize(f.getSize());
                    att.setUploadTime(LocalDateTime.now());
                    att.setExpireTime(LocalDateTime.now().plusMonths(6));
                    attachmentMapper.insert(att);
                } catch (Exception ex) {
                    log.warn("归还照片上传失败: {}", ex.getMessage());
                }
            }

            return R.ok("归还成功" + (!allFiles.isEmpty() ? "，已上传" + allFiles.size() + "张照片" : ""), record);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (Exception e) {
            return R.fail("归还失败: " + e.getMessage());
        }
    }

    // ==================== 归还申请+审批（学生→设备使用人确认） ====================

    @PostMapping("/{id}/return-request")
    @ApiOperation("学生提交归还申请（必须先上传归还照片，状态→RETURN_PENDING）")
    @PreAuthorize("hasAuthority('borrow:my')")
    public R<BorrowRecord> requestReturn(@PathVariable Long id,
                                          @RequestParam(required = false) String damageReport) {
        try {
            BorrowRecord record = borrowService.requestReturn(id, getCurrentUserId(), damageReport);
            return R.ok("归还申请已提交，等待设备使用人审批", record);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/return-pending")
    @ApiOperation("待审批归还列表（设备使用人/管理员查看）")
    @PreAuthorize("hasAnyAuthority('borrow:view','return:manage')")
    public R<List<Map<String,Object>>> listPendingReturns(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        List<BorrowRecord> records = borrowService.listPendingReturns(userId, page, size);
        var enriched = enrichNames(records);
        return R.ok(enriched);
    }

    @PostMapping("/{id}/approve-return")
    @ApiOperation("审批归还（通过→RETURNED，驳回→BORROWING）")
    @PreAuthorize("hasAnyAuthority('borrow:return','return:manage')")
    public R<String> approveReturn(@PathVariable Long id,
                                    @RequestParam boolean approved,
                                    @RequestParam(required = false) String comment) {
        try {
            borrowService.approveReturn(id, getCurrentUserId(), approved, comment);
            return R.ok(approved ? "归还已确认" : "归还申请已驳回");
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    // ==================== V6 借用浏览 ====================

    @GetMapping("/browse")
    @ApiOperation("借用浏览（综合查询+排序）")
    @PreAuthorize("hasAnyAuthority('borrow:view','return:manage','approval:first','approval:second')")
    public R<Map<String,Object>> browse(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, defaultValue = "desc") String order) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BorrowRecord>();
        if (status != null && !status.isEmpty()) w.eq(BorrowRecord::getStatus, status);
        if (keyword != null && !keyword.isEmpty())
            w.and(wp -> wp.like(BorrowRecord::getPurpose, keyword).or().like(BorrowRecord::getReason, keyword));
        if (startDate != null) w.ge(BorrowRecord::getCreateTime, java.time.LocalDate.parse(startDate).atStartOfDay());
        if (endDate != null) w.le(BorrowRecord::getCreateTime, java.time.LocalDate.parse(endDate).plusDays(1).atStartOfDay());

        boolean asc = "asc".equalsIgnoreCase(order);
        if ("id".equals(sort)) w.orderBy(true, asc, BorrowRecord::getId);
        else if ("startTime".equals(sort)) w.orderBy(true, asc, BorrowRecord::getStartTime);
        else if ("endTime".equals(sort)) w.orderBy(true, asc, BorrowRecord::getEndTime);
        else if ("overdueDays".equals(sort)) w.orderBy(true, asc, BorrowRecord::getOverdueDays);
        else w.orderBy(true, asc, BorrowRecord::getId);

        var pg = borrowService.page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), w);
        // 注入设备名和用户名
        var enriched = enrichNames(pg.getRecords());
        java.util.Map<String,Object> result = new LinkedHashMap<>();
        result.put("records", enriched); result.put("total", pg.getTotal()); result.put("page", page); result.put("size", size);
        return R.ok(result);
    }

    @GetMapping("/browse/export")
    @ApiOperation("导出借用浏览数据")
    @PreAuthorize("hasAnyAuthority('borrow:view','return:manage','approval:first','approval:second')")
    public void exportBrowse(
            @RequestParam(defaultValue="csv") String format,
            @RequestParam(required=false) String keyword,
            @RequestParam(required=false) String status,
            javax.servlet.http.HttpServletResponse response) throws Exception {
        var w = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BorrowRecord>();
        w.select("borrow_record.*, d.name AS deviceName, d.asset_no AS deviceAssetNo, u.real_name AS userName");
        w.apply("LEFT JOIN device d ON borrow_record.device_id = d.id");
        w.apply("LEFT JOIN sys_user u ON borrow_record.user_id = u.id");
        if (status != null && !status.isEmpty()) w.eq("borrow_record.status", status);
        if (keyword != null && !keyword.isEmpty())
            w.and(wp -> wp.like("d.name", keyword).or().like("u.real_name", keyword).or().like("borrow_record.purpose", keyword));
        w.orderByDesc("borrow_record.id").last("LIMIT 5000");
        var rows = borrowRecordMapper.selectMaps(w);

        if ("xlsx".equalsIgnoreCase(format)) {
            LinkedHashMap<String,String> hdrs = new LinkedHashMap<>();
            hdrs.put("id","单号");hdrs.put("deviceName","设备");hdrs.put("userName","借用人");hdrs.put("purpose","目的");
            hdrs.put("purposeCategory","目的分类");hdrs.put("startTime","开始时间");hdrs.put("endTime","结束时间");
            hdrs.put("status","状态");hdrs.put("overdueDays","逾期天数");hdrs.put("createTime","创建时间");
            byte[] xlsx = com.gzhu.equipment.common.ExcelExportUtil.exportToXlsx(rows, hdrs);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=borrow_browse_" + System.currentTimeMillis() + ".xlsx");
            response.setContentLength(xlsx.length);
            response.getOutputStream().write(xlsx); response.flushBuffer(); return;
        }
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=borrow_browse_" + System.currentTimeMillis() + ".csv");
        response.getOutputStream().write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF});
        java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(response.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8);
        osw.write("单号,设备,借用人,目的,目的分类,开始时间,结束时间,状态,逾期天数,创建时间\n");
        for (var r : rows) {
            osw.write((r.get("id")!=null?String.valueOf(r.get("id")):"")+",");
            osw.write(esc((String)r.get("deviceName"))+",");osw.write(esc((String)r.get("userName"))+",");
            osw.write(esc((String)r.get("purpose"))+",");osw.write(esc((String)r.get("purposeCategory"))+",");
            osw.write((r.get("startTime")!=null?String.valueOf(r.get("startTime")):"")+",");
            osw.write((r.get("endTime")!=null?String.valueOf(r.get("endTime")):"")+",");
            osw.write(esc((String)r.get("status"))+",");
            osw.write((r.get("overdueDays")!=null?String.valueOf(r.get("overdueDays")):"")+",");
            osw.write((r.get("createTime")!=null?String.valueOf(r.get("createTime")):"")+"\n");
        }
        osw.flush();osw.close();
    }

    private String esc(String s) { if (s==null||s.isEmpty()) return ""; if (s.contains(",")||s.contains("\"")) return "\""+s.replace("\"","\"\"")+"\""; return s; }

    // ==================== V6 逾期管理 ====================

    @GetMapping("/overdue")
    @ApiOperation("逾期未归还列表（含已标记逾期+到期未归还，支持排序）")
    @PreAuthorize("hasAuthority('return:manage')")
    public R<IPage<BorrowRecord>> overdueList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, defaultValue = "desc") String order) {
        // 查询 OVERDUE 状态 + BORROWING且已过endTime的
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BorrowRecord>()
                .and(w2 -> w2.eq(BorrowRecord::getStatus, "OVERDUE")
                        .or(w3 -> w3.eq(BorrowRecord::getStatus, "BORROWING")
                                .lt(BorrowRecord::getEndTime, java.time.LocalDateTime.now())));
        if (keyword != null && !keyword.trim().isEmpty()) {
            w.and(w2 -> w2.like(BorrowRecord::getPurpose, keyword)
                    .or().like(BorrowRecord::getReason, keyword)
                    .or().apply("device_id IN (SELECT id FROM device WHERE name LIKE {0})", "%" + keyword + "%")
                    .or().apply("user_id IN (SELECT id FROM sys_user WHERE real_name LIKE {0} OR username LIKE {0})", "%" + keyword + "%"));
        }
        // 排序
        boolean asc = "asc".equalsIgnoreCase(order);
        if ("id".equals(sort)) w.orderBy(true, asc, BorrowRecord::getId);
        else if ("overdueDays".equals(sort)) w.orderBy(true, asc, BorrowRecord::getOverdueDays);
        else if ("startTime".equals(sort)) w.orderBy(true, asc, BorrowRecord::getStartTime);
        else if ("endTime".equals(sort)) w.orderBy(true, asc, BorrowRecord::getEndTime);
        else { w.orderByDesc(BorrowRecord::getOverdueDays); w.orderByAsc(BorrowRecord::getEndTime); }
        IPage<BorrowRecord> pg = borrowService.page(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), w);
        return R.ok(pg);
    }

    @PutMapping("/{id}/force-return")
    @ApiOperation("管理员强制归还逾期设备")
    @PreAuthorize("hasAuthority('return:manage')")
    public R<String> forceReturn(@PathVariable Long id,
                                  @RequestParam(required = false) String damageReport,
                                  @RequestParam(required = false) String remark) {
        try {
            borrowService.adminForceReturn(id, getCurrentUserId(), damageReport, remark);
            return R.ok("强制归还完成");
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/{id}/overdue-notify")
    @ApiOperation("发送催还通知")
    @PreAuthorize("hasAuthority('return:manage')")
    public R<String> sendOverdueNotify(@PathVariable Long id) {
        try {
            borrowService.sendOverdueNotify(id, getCurrentUserId());
            return R.ok("催还通知已发送");
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/{id}/overdue-records")
    @ApiOperation("查看逾期记录追踪")
    public R<List<com.gzhu.equipment.entity.OverdueRecord>> getOverdueRecords(@PathVariable Long id) {
        return R.ok(overdueRecordMapper.selectList(
                new LambdaQueryWrapper<com.gzhu.equipment.entity.OverdueRecord>()
                        .eq(com.gzhu.equipment.entity.OverdueRecord::getBorrowId, id)
                        .orderByDesc(com.gzhu.equipment.entity.OverdueRecord::getCreateTime)));
    }

    @PostMapping("/overdue/refresh")
    @ApiOperation("手动刷新逾期状态（检查所有到期未还的借用，并同步设备状态）")
    @PreAuthorize("hasAuthority('return:manage')")
    public R<Integer> refreshOverdue() {
        int count = 0;
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        var borrowing = borrowService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getStatus, "BORROWING")
                        .lt(BorrowRecord::getEndTime, now));
        for (BorrowRecord br : borrowing) {
            long days = java.time.Duration.between(br.getEndTime(), now).toDays();
            br.setStatus("OVERDUE");
            br.setOverdueDays((int) Math.max(days, 1)); // 至少1天
            borrowService.updateById(br);
            // 同步更新设备借还状态为逾期
            var device = deviceMapper.selectById(br.getDeviceId());
            if (device != null && device.getBorrowStatus() != null && device.getBorrowStatus() == 2) {
                device.setBorrowStatus(4); // 逾期
                deviceMapper.updateById(device);
            }
            count++;
        }
        log.info("手动逾期刷新: {}条记录", count);
        return R.ok(count);
    }

    @GetMapping("/overdue/stats")
    @ApiOperation("逾期统计数据")
    @PreAuthorize("hasAuthority('return:manage')")
    public R<java.util.Map<String, Object>> overdueStats() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        // 当前逾期总数：OVERDUE + BORROWING已过期的
        Long overdueTotal = borrowRecordMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BorrowRecord>()
                        .and(q -> q.eq(BorrowRecord::getStatus, "OVERDUE")
                                .or(q2 -> q2.eq(BorrowRecord::getStatus, "BORROWING").lt(BorrowRecord::getEndTime, now))));
        // 用AVG聚合避免加载全部记录
        Double avgDays = 0.0;
        try {
            var avgResult = borrowRecordMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BorrowRecord>()
                    .select("COALESCE(AVG(overdue_days), 0) as avg_days")
                    .and(q -> q.eq("status", "OVERDUE").or(q2 -> q2.eq("status", "BORROWING").lt("end_time", now))));
            if (!avgResult.isEmpty() && avgResult.get(0) != null) {
                Object avg = avgResult.get(0).get("avg_days");
                avgDays = avg instanceof Number ? ((Number) avg).doubleValue() : 0.0;
            }
        } catch (Exception e) { log.warn("平均逾期天数计算失败: {}", e.getMessage()); }
        var notified = overdueRecordMapper.selectCount(
                new LambdaQueryWrapper<com.gzhu.equipment.entity.OverdueRecord>().gt(com.gzhu.equipment.entity.OverdueRecord::getNotifyCount, 0));
        var collected = overdueRecordMapper.selectCount(
                new LambdaQueryWrapper<com.gzhu.equipment.entity.OverdueRecord>().eq(com.gzhu.equipment.entity.OverdueRecord::getCollectionStatus, "COLLECTED"));
        java.util.Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("overdueTotal", overdueTotal != null ? overdueTotal.intValue() : 0);
        stats.put("avgDays", Math.round(avgDays));
        stats.put("notified", notified);
        stats.put("collected", collected);
        return R.ok(stats);
    }

    @GetMapping("/{id}/approval-logs")
    @ApiOperation("审批记录列表")
    public R<List<ApprovalLog>> approvalLogs(@PathVariable Long id) {
        return R.ok(approvalLogMapper.selectList(
                new LambdaQueryWrapper<ApprovalLog>().eq(ApprovalLog::getBorrowId, id).orderByAsc(ApprovalLog::getStep)));
    }

    @PostMapping("/{id}/verify")
    @ApiOperation("管理员核验归还")
    @PreAuthorize("hasAuthority('return:manage')")
    public R<String> verifyReturn(@PathVariable Long id) {
        borrowService.verifyReturn(id, getCurrentUserId()); return R.ok("已核验");
    }

    // ==================== V4 目的与成果 ====================

    @PutMapping("/{id}/outcome")
    @ApiOperation("录入/更新借用成果（管理员或借用人）")
    @PreAuthorize("hasAnyAuthority('borrow:return','return:manage','laboratory:manage')")
    public R<String> recordOutcome(@PathVariable Long id, @RequestParam String outcome) {
        BorrowRecord record = borrowService.getDetail(id);
        if (record == null) return R.fail(404, "借用单不存在");
        record.setOutcome(outcome);
        record.setOutcomeRecordedBy(getCurrentUserId());
        record.setOutcomeRecordedTime(java.time.LocalDateTime.now());
        borrowService.updateById(record);
        return R.ok("成果已记录");
    }

    @GetMapping("/outcomes")
    @ApiOperation("成果列表（按设备/时间范围查询）")
    @PreAuthorize("hasAnyAuthority('statistics:view','laboratory:view')")
    public R<IPage<BorrowRecord>> listOutcomes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BorrowRecord>()
                .isNotNull(BorrowRecord::getOutcome)
                .ne(BorrowRecord::getOutcome, "")
                .orderByDesc(BorrowRecord::getOutcomeRecordedTime);
        if (deviceId != null) w.eq(BorrowRecord::getDeviceId, deviceId);
        if (startDate != null) w.ge(BorrowRecord::getCreateTime, java.time.LocalDate.parse(startDate).atStartOfDay());
        if (endDate != null) w.le(BorrowRecord::getCreateTime, java.time.LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        return R.ok(borrowService.page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), w));
    }

    // ==================== V4 成果管理 ====================

    private final com.gzhu.equipment.mapper.BorrowOutcomeMapper outcomeMapper;
    private final com.gzhu.equipment.mapper.OverdueRecordMapper overdueRecordMapper;

    @PostMapping("/{id}/outcomes")
    @ApiOperation("新增成果记录")
    @PreAuthorize("hasAnyAuthority('borrow:return','return:manage','laboratory:manage')")
    public R<com.gzhu.equipment.entity.BorrowOutcome> addOutcome(@PathVariable Long id,
             @RequestBody com.gzhu.equipment.entity.BorrowOutcome outcome) {
        BorrowRecord record = borrowService.getDetail(id);
        if (record == null) return R.fail(404, "借用单不存在");
        outcome.setBorrowId(id);
        outcome.setDeviceId(record.getDeviceId());
        outcome.setRecordedBy(getCurrentUserId());
        outcomeMapper.insert(outcome);
        // 同时更新 borrow_record.outcome 摘要字段
        if (record.getOutcome() == null) record.setOutcome(outcome.getTitle());
        else record.setOutcome(record.getOutcome() + "；" + outcome.getTitle());
        record.setOutcomeRecordedBy(getCurrentUserId());
        record.setOutcomeRecordedTime(java.time.LocalDateTime.now());
        borrowService.updateById(record);
        return R.ok(outcome);
    }

    @GetMapping("/{id}/outcomes")
    @ApiOperation("查看借用单的成果列表")
    public R<java.util.List<com.gzhu.equipment.entity.BorrowOutcome>> listOutcomes(@PathVariable Long id) {
        return R.ok(outcomeMapper.selectList(
                new LambdaQueryWrapper<com.gzhu.equipment.entity.BorrowOutcome>().eq(com.gzhu.equipment.entity.BorrowOutcome::getBorrowId, id)));
    }

    @DeleteMapping("/outcomes/{outcomeId}")
    @ApiOperation("删除成果记录")
    @PreAuthorize("hasAnyAuthority('return:manage','laboratory:manage')")
    public R<String> deleteOutcome(@PathVariable Long outcomeId) {
        outcomeMapper.deleteById(outcomeId);
        return R.ok("已删除");
    }

    @GetMapping("/device-outcomes")
    @ApiOperation("按设备查询所有成果")
    @PreAuthorize("hasAnyAuthority('statistics:view','laboratory:view')")
    public R<java.util.List<com.gzhu.equipment.entity.BorrowOutcome>> deviceOutcomes(@RequestParam Long deviceId) {
        return R.ok(outcomeMapper.selectList(
                new LambdaQueryWrapper<com.gzhu.equipment.entity.BorrowOutcome>().eq(com.gzhu.equipment.entity.BorrowOutcome::getDeviceId, deviceId).orderByDesc(com.gzhu.equipment.entity.BorrowOutcome::getCreateTime)));
    }

    // ==================== 辅助 ====================

    /** 为借用记录批量填充设备名和用户名 */
    private java.util.List<java.util.Map<String,Object>> enrichNames(java.util.List<BorrowRecord> records) {
        if (records == null || records.isEmpty()) return java.util.Collections.emptyList();
        java.util.Set<Long> devIds = new java.util.HashSet<>(), userIds = new java.util.HashSet<>();
        java.util.List<Long> borrowIds = new java.util.ArrayList<>();
        for (BorrowRecord r : records) { devIds.add(r.getDeviceId()); userIds.add(r.getUserId()); borrowIds.add(r.getId()); }

        // 设备名+资产号+使用人
        java.util.Map<Long,String> devNames = new java.util.HashMap<>(), devAssets = new java.util.HashMap<>(), custodians = new java.util.HashMap<>();
        if (!devIds.isEmpty()) {
            var devs = borrowRecordMapper.selectDeviceNames(new java.util.ArrayList<>(devIds));
            for (var d : devs) {
                Long id = (Long) d.get("id");
                devNames.put(id, (String) d.getOrDefault("name", ""));
                devAssets.put(id, (String) d.getOrDefault("asset_no", ""));
                custodians.put(id, (String) d.getOrDefault("custodian", ""));
            }
        }
        // 用户名
        java.util.Map<Long,String> userNames = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            var users = borrowRecordMapper.selectUserNames(new java.util.ArrayList<>(userIds));
            for (var u : users) {
                Long id = (Long) u.get("id");
                userNames.put(id, (String) u.getOrDefault("real_name", ""));
            }
        }
        // 审批人姓名（初审step=1, 终审step=2）
        java.util.Map<Long,String> approver1Names = new java.util.HashMap<>(), approver2Names = new java.util.HashMap<>();
        if (!borrowIds.isEmpty()) {
            var logs = approvalLogMapper.selectList(
                    new LambdaQueryWrapper<ApprovalLog>().in(ApprovalLog::getBorrowId, borrowIds));
            java.util.Set<Long> approverIds = new java.util.HashSet<>();
            for (var l : logs) if (l.getApproverId() != null) approverIds.add(l.getApproverId());
            java.util.Map<Long,String> approverNameMap = new java.util.HashMap<>();
            if (!approverIds.isEmpty()) {
                var approvers = borrowRecordMapper.selectUserNames(new java.util.ArrayList<>(approverIds));
                for (var a : approvers) {
                    Long id = (Long) a.get("id");
                    approverNameMap.put(id, (String) a.getOrDefault("real_name", ""));
                }
            }
            for (var l : logs) {
                String name = approverNameMap.getOrDefault(l.getApproverId(), l.getApproverId() != null ? "ID:" + l.getApproverId() : "未分配");
                if (l.getStep() != null && l.getStep() == 1) approver1Names.put(l.getBorrowId(), name);
                else if (l.getStep() != null && l.getStep() == 2) approver2Names.put(l.getBorrowId(), name);
            }
        }

        java.util.List<java.util.Map<String,Object>> enriched = new java.util.ArrayList<>();
        for (BorrowRecord r : records) {
            java.util.Map<String,Object> m = new java.util.LinkedHashMap<>();
            m.put("id", r.getId()); m.put("deviceId", r.getDeviceId()); m.put("userId", r.getUserId());
            m.put("status", r.getStatus()); m.put("startTime", r.getStartTime()); m.put("endTime", r.getEndTime());
            m.put("purpose", r.getPurpose()); m.put("purposeCategory", r.getPurposeCategory());
            m.put("reason", r.getReason()); m.put("overdueDays", r.getOverdueDays());
            m.put("createTime", r.getCreateTime()); m.put("realReturnTime", r.getRealReturnTime());
            m.put("deviceName", devNames.getOrDefault(r.getDeviceId(), "设备#"+r.getDeviceId()));
            m.put("deviceAssetNo", devAssets.getOrDefault(r.getDeviceId(), ""));
            m.put("custodian", custodians.getOrDefault(r.getDeviceId(), ""));
            m.put("userName", userNames.getOrDefault(r.getUserId(), "用户#"+r.getUserId()));
            m.put("approver1Name", approver1Names.getOrDefault(r.getId(), ""));
            m.put("approver2Name", approver2Names.getOrDefault(r.getId(), ""));
            enriched.add(m);
        }
        return enriched;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtUserPrincipal p) {
            return p.getUserId();
        }
        throw new IllegalStateException("未登录");
    }
}

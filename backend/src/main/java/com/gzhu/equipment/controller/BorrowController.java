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
import java.util.List;

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
    @ApiOperation("我的借用列表")
    @PreAuthorize("hasAuthority('borrow:my')")
    public R<IPage<BorrowRecord>> myBorrows(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return R.ok(borrowService.myBorrows(getCurrentUserId(), page, size, status));
    }

    @GetMapping("/{id}")
    @ApiOperation("借用详情")
    @PreAuthorize("hasAuthority('borrow:view')")
    public R<BorrowRecord> getDetail(@PathVariable Long id) {
        BorrowRecord record = borrowService.getDetail(id);
        if (record == null) return R.fail(404, "借用单不存在");
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
    public R<IPage<BorrowRecord>> pendingFirst(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(borrowService.pendingApprovals(getCurrentUserId(), 1, page, size));
    }

    @GetMapping("/pending/second")
    @ApiOperation("终审待审批列表（实验室管理员/admin）")
    @PreAuthorize("hasAuthority('approval:second')")
    public R<IPage<BorrowRecord>> pendingSecond(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(borrowService.pendingApprovals(getCurrentUserId(), 2, page, size));
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

    // ==================== 归还 ====================

    @PostMapping("/{id}/return")
    @ApiOperation("归还登记（支持上传归还照片）")
    @PreAuthorize("hasAuthority('borrow:return')")
    public R<BorrowRecord> returnDevice(@PathVariable Long id,
                                         @RequestParam(required = false) String damageReport,
                                         @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            BorrowRecord record = borrowService.returnDevice(id, getCurrentUserId(), damageReport);

            // 上传归还照片（如有）
            if (file != null && !file.isEmpty()) {
                String objectPath = minioFileService.uploadImage(file, "RETURN");
                Attachment att = new Attachment();
                att.setBizType("RETURN_IMG");
                att.setBizId(record.getId());
                att.setFileUrl(objectPath);
                att.setFileSize(file.getSize());
                att.setUploadTime(LocalDateTime.now());
                att.setExpireTime(LocalDateTime.now().plusMonths(6)); // 半年后过期
                attachmentMapper.insert(att);
            }

            return R.ok("归还成功", record);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (Exception e) {
            return R.fail("归还失败: " + e.getMessage());
        }
    }

    // ==================== V6 借用浏览 ====================

    @GetMapping("/browse")
    @ApiOperation("借用浏览（综合查询+排序）")
    @PreAuthorize("hasAnyAuthority('borrow:view','return:manage','approval:first','approval:second')")
    public R<IPage<BorrowRecord>> browse(
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
        if (keyword != null && !keyword.isEmpty()) {
            w.and(wp -> wp.like(BorrowRecord::getPurpose, keyword).or().like(BorrowRecord::getReason, keyword));
        }
        if (startDate != null) w.ge(BorrowRecord::getCreateTime, java.time.LocalDate.parse(startDate).atStartOfDay());
        if (endDate != null) w.le(BorrowRecord::getCreateTime, java.time.LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        if (sort != null && !sort.isEmpty()) {
            boolean asc = "asc".equalsIgnoreCase(order);
            switch (sort) {
                case "id": w.orderBy(true, asc, BorrowRecord::getId); break;
                case "startTime": w.orderBy(true, asc, BorrowRecord::getStartTime); break;
                case "endTime": w.orderBy(true, asc, BorrowRecord::getEndTime); break;
                case "overdueDays": w.orderBy(true, asc, BorrowRecord::getOverdueDays); break;
                default: w.orderByDesc(BorrowRecord::getId);
            }
        } else {
            w.orderByDesc(BorrowRecord::getId);
        }
        return R.ok(borrowService.page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), w));
    }

    // ==================== V6 逾期管理 ====================

    @GetMapping("/overdue")
    @ApiOperation("逾期未归还列表（含已标记逾期+到期未归还）")
    @PreAuthorize("hasAuthority('return:manage')")
    public R<IPage<BorrowRecord>> overdueList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 查询 OVERDUE 状态 + BORROWING且已过endTime的
        IPage<BorrowRecord> pg = borrowService.page(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BorrowRecord>()
                        .and(w -> w.eq(BorrowRecord::getStatus, "OVERDUE")
                                .or(w2 -> w2.eq(BorrowRecord::getStatus, "BORROWING")
                                        .lt(BorrowRecord::getEndTime, java.time.LocalDateTime.now())))
                        .orderByDesc(BorrowRecord::getOverdueDays)
                        .orderByAsc(BorrowRecord::getEndTime));
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

    @GetMapping("/overdue/stats")
    @ApiOperation("逾期统计数据")
    @PreAuthorize("hasAuthority('return:manage')")
    public R<java.util.Map<String, Object>> overdueStats() {
        var overdueTotal = borrowService.count(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getStatus, "OVERDUE"));
        var collectedTotal = borrowService.count(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getStatus, "RETURNED")
                        .isNotNull(BorrowRecord::getDamageReport));
        java.util.Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("overdueTotal", overdueTotal);
        stats.put("collectedTotal", collectedTotal);
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

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtUserPrincipal p) {
            return p.getUserId();
        }
        throw new IllegalStateException("未登录");
    }
}

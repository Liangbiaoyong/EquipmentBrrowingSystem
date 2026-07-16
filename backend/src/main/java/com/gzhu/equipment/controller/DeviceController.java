package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gzhu.equipment.common.R;
import com.gzhu.equipment.dto.BatchInfoDTO;
import com.gzhu.equipment.dto.ImportResultDTO;
import com.gzhu.equipment.entity.*;
import com.gzhu.equipment.mapper.BorrowRecordMapper;
import com.gzhu.equipment.mapper.DeviceCategoryMapper;
import com.gzhu.equipment.mapper.DeviceImageMapper;
import com.gzhu.equipment.mapper.LaboratoryMapper;
import com.gzhu.equipment.service.DeviceImportService;
import com.gzhu.equipment.service.DeviceService;
import com.gzhu.equipment.vo.DeviceDetailVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备管理控制器 — CRUD + 批量导入 + 导出 + 批次管理
 */
@Slf4j
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
@Api(tags = "设备管理")
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceImportService deviceImportService;
    private final DeviceImageMapper imageMapper;
    private final DeviceCategoryMapper categoryMapper;
    private final BorrowRecordMapper borrowMapper;
    private final com.gzhu.equipment.mapper.DeviceMapper deviceMapper;
    private final com.gzhu.equipment.mapper.SysUserMapper sysUserMapper;
    private final LaboratoryMapper laboratoryMapper;

    // ==================== 查询 ====================

    @GetMapping
    @ApiOperation("分页查询设备列表（支持排序）")
    public R<IPage<Device>> listDevices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String assetNo,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer borrowStatus,
            @RequestParam(required = false) Integer deviceStatus,
            @RequestParam(required = false) Integer borrowType,
            @RequestParam(required = false) Long laboratoryId,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String gbCategoryName,
            @RequestParam(required = false) String custodian,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, defaultValue = "desc") String order) {
        // 构建查询（复用service，然后覆盖排序）
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Device>();
        // 应用查询条件
        if (org.springframework.util.StringUtils.hasText(assetNo)) wrapper.like(Device::getAssetNo, assetNo);
        if (org.springframework.util.StringUtils.hasText(name)) wrapper.like(Device::getName, name);
        if (org.springframework.util.StringUtils.hasText(model)) wrapper.like(Device::getModel, model);
        if (org.springframework.util.StringUtils.hasText(custodian)) wrapper.like(Device::getCustodian, custodian);
        if (org.springframework.util.StringUtils.hasText(keyword)) {
            wrapper.and(w -> { w.like(Device::getName, keyword).or().like(Device::getAssetNo, keyword)
                .or().like(Device::getModel, keyword).or().like(Device::getLocation, keyword)
                .or().like(Device::getGbCategoryName, keyword);
                try { w.or().eq(Device::getId, Long.parseLong(keyword)); } catch (NumberFormatException ignored) {} });
        }
        if (categoryId != null) wrapper.eq(Device::getCategoryId, categoryId);
        if (borrowStatus != null) wrapper.eq(Device::getBorrowStatus, borrowStatus);
        if (deviceStatus != null) wrapper.eq(Device::getDeviceStatus, deviceStatus);
        if (borrowType != null) wrapper.eq(Device::getBorrowType, borrowType);
        if (laboratoryId != null) wrapper.eq(Device::getLaboratoryId, laboratoryId);
        if (org.springframework.util.StringUtils.hasText(location)) wrapper.like(Device::getLocation, location);
        if (org.springframework.util.StringUtils.hasText(gbCategoryName)) wrapper.like(Device::getGbCategoryName, gbCategoryName);
        // 排序
        boolean asc = "asc".equalsIgnoreCase(order);
        if ("id".equals(sort)) wrapper.orderBy(true, asc, Device::getId);
        else if ("assetNo".equals(sort)) wrapper.orderBy(true, asc, Device::getAssetNo);
        else if ("name".equals(sort)) wrapper.orderBy(true, asc, Device::getName);
        else if ("location".equals(sort)) wrapper.orderBy(true, asc, Device::getLocation);
        else if ("borrowStatus".equals(sort)) wrapper.orderBy(true, asc, Device::getBorrowStatus);
        else if ("deviceStatus".equals(sort)) wrapper.orderBy(true, asc, Device::getDeviceStatus);
        else if ("createTime".equals(sort)) wrapper.orderBy(true, asc, Device::getCreateTime);
        else wrapper.orderByDesc(Device::getId);
        return R.ok(deviceMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper));
    }

    @GetMapping("/by-status/{type}")
    @ApiOperation("按状态分类查询设备：idle闲置 / borrowing借用中 / unavailable不可借 / repair待维修")
    public R<List<Device>> listByStatus(@PathVariable String type) {
        switch (type) {
            case "idle": return R.ok(deviceService.list(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus, 1)));
            case "borrowing": return R.ok(deviceService.list(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus, 2)));
            case "unavailable": return R.ok(deviceService.list(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus, 3)));
            case "repair": return R.ok(deviceService.list(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus, 3)));
            default: return R.fail("无效类型: idle/borrowing/unavailable/repair");
        }
    }

    @PutMapping("/{id}/default-approver")
    @ApiOperation("设置设备默认审批人")
    @PreAuthorize("hasAnyAuthority('device:manage','admin:user')")
    public R<String> setDefaultApprover(@PathVariable Long id, @RequestParam Long approverId) {
        Device d = deviceService.getById(id);
        if (d == null) return R.fail(404, "设备不存在");
        d.setDefaultApproverId(approverId);
        deviceService.updateById(d);
        return R.ok("已更新");
    }

    @GetMapping("/{id}")
    @ApiOperation("获取设备详情（含图片、借用状态、分类名）")
    public R<DeviceDetailVO> getDevice(@PathVariable Long id) {
        Device device = deviceService.getById(id);
        if (device == null) return R.fail(404, "设备不存在");

        // 图片列表
        List<DeviceImage> images = imageMapper.selectList(
                new LambdaQueryWrapper<DeviceImage>()
                        .eq(DeviceImage::getDeviceId, id)
                        .orderByAsc(DeviceImage::getSort));

        // 分类名
        String categoryName = "";
        if (device.getCategoryId() != null) {
            DeviceCategory cat = categoryMapper.selectById(device.getCategoryId());
            if (cat != null) categoryName = cat.getName();
        }

        // 当前借用状态
        BorrowRecord currentBorrow = borrowMapper.selectOne(
                new LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getDeviceId, id)
                        .in(BorrowRecord::getStatus, "BORROWING", "OVERDUE")
                        .orderByDesc(BorrowRecord::getCreateTime)
                        .last("LIMIT 1"));

        // 历史借用次数
        long borrowCount = borrowMapper.selectCount(
                new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getDeviceId, id));

        String borrower = null;
        String returnTime = null;
        if (currentBorrow != null) {
            var bu = sysUserMapper.selectById(currentBorrow.getUserId());
            borrower = bu != null ? (bu.getRealName() != null ? bu.getRealName() : bu.getUsername()) : "用户ID:" + currentBorrow.getUserId();
            returnTime = currentBorrow.getEndTime() != null
                    ? currentBorrow.getEndTime().toString() : null;
        }

        // 借用类型
        Integer borrowType = device.getBorrowType();

        // 实验室名称
        String laboratoryName = null;
        if (device.getLaboratoryId() != null) {
            var lab = laboratoryMapper.selectById(device.getLaboratoryId());
            laboratoryName = lab != null ? lab.getName() : null;
        }

        // 状态名称（V3新增）
        device.setBorrowStatusName(com.gzhu.equipment.common.DeviceStatusConstants.borrowStatusName(
                device.getBorrowStatus() != null ? device.getBorrowStatus() : 1));
        device.setDeviceStatusName(com.gzhu.equipment.common.DeviceStatusConstants.deviceStatusName(
                device.getDeviceStatus() != null ? device.getDeviceStatus() : 1));

        DeviceDetailVO vo = DeviceDetailVO.builder()
                .device(device)
                .images(images != null ? images : List.of())
                .categoryName(categoryName)
                .isBorrowing(currentBorrow != null)
                .currentBorrower(borrower)
                .expectedReturnTime(returnTime)
                .borrowCount(borrowCount)
                .borrowType(borrowType)
                .laboratoryName(laboratoryName)
                .build();

        return R.ok(vo);
    }

    @GetMapping("/by-asset-no/{assetNo}")
    @ApiOperation("按资产编号查询")
    public R<Device> getByAssetNo(@PathVariable String assetNo) {
        Device device = deviceService.getByAssetNo(assetNo);
        if (device == null) {
            return R.fail(404, "设备不存在");
        }
        return R.ok(device);
    }

    // ==================== 增删改 ====================

    @PutMapping("/{id}")
    @ApiOperation("更新设备信息（管理员/设备使用人可操作）")
    @PreAuthorize("hasAnyAuthority('ROLE_LAB_ADMIN', 'ROLE_SYSTEM_ADMIN', 'device:manage')")
    public R<Device> updateDevice(@PathVariable Long id, @RequestBody Device device) {
        device.setId(id);
        deviceService.updateById(device);
        return R.ok(device);
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除单个设备")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public R<Void> deleteDevice(@PathVariable Long id) {
        deviceService.deleteDevice(id);
        return R.ok();
    }

    // ==================== 批量导入 ====================

    @PostMapping("/import")
    @ApiOperation("批量导入设备资产（CSV/XLSX），mode=append追加 replace替换")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
    public R<ImportResultDTO> importDevices(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "append") String mode) {
        if (file.isEmpty()) return R.fail(400, "请选择文件");
        String fileName = file.getOriginalFilename();
        if (fileName == null || !(fileName.toLowerCase().endsWith(".csv") || fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls"))) {
            return R.fail(400, "仅支持 .csv / .xlsx / .xls 格式");
        }
        if (!"append".equals(mode) && !"replace".equals(mode)) {
            return R.fail(400, "mode 参数必须为 append 或 replace");
        }
        try {
            Long userId = getUserId();
            ImportResultDTO result = deviceImportService.importFromStream(file.getInputStream(), fileName, userId, mode);
            return result.getFailCount() > 0 ? R.ok("导入完成（含错误）", result) : R.ok("导入成功", result);
        } catch (IOException e) {
            return R.fail("文件读取失败: " + e.getMessage());
        }
    }

    @PostMapping("/import/dry-run")
    @ApiOperation("预览导入（不写入数据库，返回前20条+分类统计）")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
    public R<ImportResultDTO> dryRun(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return R.fail(400, "请选择文件");
        String fileName = file.getOriginalFilename();
        if (fileName == null || !(fileName.toLowerCase().endsWith(".csv") || fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls"))) {
            return R.fail(400, "仅支持 .csv / .xlsx / .xls 格式");
        }
        try {
            return R.ok(deviceImportService.dryRun(file.getInputStream(), fileName));
        } catch (IOException e) {
            return R.fail("文件读取失败: " + e.getMessage());
        }
    }

    // ==================== 批次管理 ====================

    @GetMapping("/batches")
    @ApiOperation("获取所有导入批次列表（含时间、行数等元数据）")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
    public R<List<BatchInfoDTO>> listBatches() {
        return R.ok(deviceService.listBatches());
    }

    @DeleteMapping("/batches/{batchId}")
    @ApiOperation("按批次清除已导入设备")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public R<Integer> clearByBatch(@PathVariable String batchId) {
        int count = deviceImportService.clearByBatchId(batchId);
        log.warn("批次清除完成: batchId={} count={}", batchId, count);
        return R.ok("已清除 " + count + " 条设备记录", count);
    }

    @GetMapping("/batches/{batchId}")
    @ApiOperation("按批次查询设备列表")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
    public R<List<Device>> listByBatch(@PathVariable String batchId) {
        return R.ok(deviceService.listByBatchId(batchId));
    }

    // ==================== 设备快速选择（V3新增） ====================

    @GetMapping("/picker")
    @ApiOperation("设备快速选择器：关键词搜索+分类筛选+分页，返回精简字段")
    public R<IPage<Device>> picker(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer borrowType) {
        IPage<Device> result = deviceService.pageQuery(page, size, keyword, null, null, null,
                categoryId, 1, null, null, null, borrowType, null);
        // 精简返回字段：只保留选择器需要的字段
        for (Device d : result.getRecords()) {
            d.setSpecs(null); d.setGbCategoryName(null); d.setGbCategoryCode(null);
            d.setEduCategoryName(null); d.setEduCategoryCode(null);
            d.setManufacturer(null); d.setSupplier(null); d.setInvoiceNo(null);
            d.setContractNo(null); d.setDepartment(null);
        }
        return R.ok(result);
    }

    // ==================== 导入模板下载 ====================

    @GetMapping("/import/template")
    @ApiOperation("下载导入模板（CSV/XLSX）")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN') || hasAuthority('device:manage')")
    public void downloadTemplate(
            @RequestParam(defaultValue = "csv") String format,
            javax.servlet.http.HttpServletResponse response) throws Exception {
        if ("xlsx".equalsIgnoreCase(format)) {
            LinkedHashMap<String, String> headers = new LinkedHashMap<>();
            headers.put("assetNo", "资产编号");
            headers.put("name", "名称");
            headers.put("model", "型号");
            headers.put("specs", "规格");
            headers.put("totalQty", "数量");
            headers.put("unitPrice", "单价(元)");
            headers.put("totalAmount", "总金额(元)");
            headers.put("purchaseDate", "购置日期(YYYY-MM-DD)");
            headers.put("department", "使用单位");
            headers.put("custodian", "使用人");
            headers.put("location", "存放地");
            headers.put("description", "备注");
            headers.put("gbCategoryName", "国标分类名");
            headers.put("gbCategoryCode", "国标分类码");
            headers.put("manufacturer", "厂家");
            headers.put("supplier", "供货商");

            // 构造示例数据行
            List<Map<String, Object>> sampleRows = new ArrayList<>();
            Map<String, Object> row1 = new LinkedHashMap<>();
            row1.put("assetNo", "ZQ2024001"); row1.put("name", "激光打印机-HP1020"); row1.put("model", "HP LaserJet 1020");
            row1.put("specs", "A4黑白激光"); row1.put("totalQty", 2); row1.put("unitPrice", 1200); row1.put("totalAmount", 2400);
            row1.put("purchaseDate", "2024-01-15"); row1.put("department", "建筑学院"); row1.put("custodian", "张三");
            row1.put("location", "工程南501"); row1.put("description", "办公用"); row1.put("gbCategoryName", "激光打印机-A4");
            row1.put("gbCategoryCode", "GB-123456"); row1.put("manufacturer", "惠普(HP)"); row1.put("supplier", "广州办公设备有限公司");
            sampleRows.add(row1);

            Map<String, Object> row2 = new LinkedHashMap<>();
            row2.put("assetNo", "ZQ2024002"); row2.put("name", "台式计算机-联想"); row2.put("model", "ThinkCentre M950t");
            row2.put("specs", "i7/16G/512G"); row2.put("totalQty", 5); row2.put("unitPrice", 5800); row2.put("totalAmount", 29000);
            row2.put("purchaseDate", "2024-02-20"); row2.put("department", "建筑学院"); row2.put("custodian", "李四");
            row2.put("location", "工程南502"); row2.put("description", "教学用"); row2.put("gbCategoryName", "台式计算机");
            row2.put("gbCategoryCode", "GB-123457"); row2.put("manufacturer", "联想(Lenovo)"); row2.put("supplier", "广州电脑城");
            sampleRows.add(row2);

            byte[] xlsx = com.gzhu.equipment.common.ExcelExportUtil.exportToXlsx(sampleRows, headers);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=设备导入模板.xlsx");
            response.setContentLength(xlsx.length);
            response.getOutputStream().write(xlsx);
            response.flushBuffer();
        } else {
            // CSV 模板
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=设备导入模板.csv");
            response.getOutputStream().write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF});
            java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(response.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8);
            osw.write("资产编号,名称,型号,规格,数量,单价(元),总金额(元),购置日期(YYYY-MM-DD),使用单位,使用人,存放地,备注,国标分类名,国标分类码,厂家,供货商\n");
            osw.write("ZQ2024001,激光打印机-HP1020,HP LaserJet 1020,A4黑白激光,2,1200,2400,2024-01-15,建筑学院,张三,工程南501,办公用,激光打印机-A4,GB-123456,惠普(HP),广州办公设备有限公司\n");
            osw.write("ZQ2024002,台式计算机-联想,ThinkCentre M950t,i7/16G/512G,5,5800,29000,2024-02-20,建筑学院,李四,工程南502,教学用,台式计算机,GB-123457,联想(Lenovo),广州电脑城\n");
            osw.flush(); osw.close();
        }
    }

    // ==================== 导出 ====================

    @GetMapping("/export/csv")
    @ApiOperation("导出设备为CSV（支持筛选条件）")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String assetNo,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Integer borrowStatus,
            @RequestParam(required = false) Integer deviceStatus,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String gbCategoryName,
            @RequestParam(required = false) Integer borrowType,
            @RequestParam(required = false) Long laboratoryId,
            @RequestParam(required = false) String custodian) throws IOException {

        List<Device> devices;
        if (batchId != null) {
            devices = deviceService.listByBatchId(batchId);
        } else {
            devices = deviceService.pageQuery(1, 10000, keyword, assetNo, name, model,
                    categoryId, borrowStatus, deviceStatus, location, gbCategoryName,
                    borrowType, laboratoryId).getRecords();
            // 后过滤使用人（pageQuery 不支持 custodian 参数）
            if (custodian != null && !custodian.isEmpty()) {
                devices = devices.stream().filter(d -> custodian.equals(d.getCustodian())).collect(java.util.stream.Collectors.toList());
            }
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        // BOM for Excel UTF-8 识别
        bos.write(0xEF);
        bos.write(0xBB);
        bos.write(0xBF);

        try (OutputStreamWriter writer = new OutputStreamWriter(bos, StandardCharsets.UTF_8)) {
            writer.write("资产编号,名称,型号,规格,业务分类ID,国标分类名,存放地,使用单位,使用人,数量,单价,金额,购置日期,厂家,供货商\n");
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (Device d : devices) {
                writer.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,%s,%s,%s,%s,%s\n",
                        csv(d.getAssetNo()), csv(d.getName()), csv(d.getModel()), csv(d.getSpecs()),
                        d.getCategoryId(), csv(d.getGbCategoryName()), csv(d.getLocation()),
                        csv(d.getDepartment()), csv(d.getCustodian()),
                        d.getTotalQty() != null ? d.getTotalQty() : 0,
                        d.getUnitPrice(), d.getTotalAmount(),
                        d.getPurchaseDate() != null ? d.getPurchaseDate().format(dtf) : "",
                        csv(d.getManufacturer()), csv(d.getSupplier())));
            }
        }

        byte[] bytes = bos.toByteArray();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=devices_export_" + System.currentTimeMillis() + ".csv");

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    // ==================== 辅助 ====================

    private String csv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private Long getUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.gzhu.equipment.security.JwtUserPrincipal p) {
            return p.getUserId();
        }
        return 1L;
    }
}

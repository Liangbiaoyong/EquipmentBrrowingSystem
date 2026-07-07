package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gzhu.equipment.common.R;
import com.gzhu.equipment.dto.ImportResultDTO;
import com.gzhu.equipment.entity.*;
import com.gzhu.equipment.mapper.BorrowRecordMapper;
import com.gzhu.equipment.mapper.DeviceCategoryMapper;
import com.gzhu.equipment.mapper.DeviceImageMapper;
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
import java.util.List;

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

    // ==================== 查询 ====================

    @GetMapping
    @ApiOperation("分页查询设备列表")
    public R<IPage<Device>> listDevices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String gbCategoryName) {
        return R.ok(deviceService.pageQuery(page, size, keyword, categoryId, status, location, gbCategoryName));
    }

    @GetMapping("/by-status/{type}")
    @ApiOperation("按状态分类查询设备：idle闲置 / borrowing借用中 / unavailable不可借 / repair待维修")
    public R<List<Device>> listByStatus(@PathVariable String type) {
        List<Device> all = deviceMapper.selectList(new LambdaQueryWrapper<Device>().eq(Device::getStatus, 1));
        switch (type) {
            case "idle": return R.ok(all.stream().filter(d -> d.getAvailableQty() != null && d.getAvailableQty() > 0).collect(java.util.stream.Collectors.toList()));
            case "borrowing": {
                var borrowingIds = borrowMapper.selectList(new LambdaQueryWrapper<com.gzhu.equipment.entity.BorrowRecord>().eq(com.gzhu.equipment.entity.BorrowRecord::getStatus, "BORROWING")).stream().map(com.gzhu.equipment.entity.BorrowRecord::getDeviceId).collect(java.util.stream.Collectors.toSet());
                return R.ok(all.stream().filter(d -> borrowingIds.contains(d.getId())).collect(java.util.stream.Collectors.toList()));
            }
            case "unavailable": return R.ok(all.stream().filter(d -> d.getAvailableQty() == null || d.getAvailableQty() == 0).collect(java.util.stream.Collectors.toList()));
            case "repair": return R.ok(deviceService.list(new LambdaQueryWrapper<Device>().eq(Device::getStatus, 2)));
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
            borrower = "用户ID:" + currentBorrow.getUserId();
            returnTime = currentBorrow.getEndTime() != null
                    ? currentBorrow.getEndTime().toString() : null;
        }

        DeviceDetailVO vo = DeviceDetailVO.builder()
                .device(device)
                .images(images != null ? images : List.of())
                .categoryName(categoryName)
                .isBorrowing(currentBorrow != null)
                .currentBorrower(borrower)
                .expectedReturnTime(returnTime)
                .borrowCount(borrowCount)
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
    @ApiOperation("更新设备信息")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
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
    @ApiOperation("批量导入设备资产（CSV/XLSX）")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
    public R<ImportResultDTO> importDevices(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return R.fail(400, "请选择文件");
        String fileName = file.getOriginalFilename();
        if (fileName == null || !(fileName.toLowerCase().endsWith(".csv") || fileName.toLowerCase().endsWith(".xlsx"))) {
            return R.fail(400, "仅支持 .csv 或 .xlsx 格式");
        }
        try {
            Long userId = getUserId();
            ImportResultDTO result = deviceImportService.importFromStream(file.getInputStream(), fileName, userId);
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
        if (fileName == null || !(fileName.toLowerCase().endsWith(".csv") || fileName.toLowerCase().endsWith(".xlsx"))) {
            return R.fail(400, "仅支持 .csv 或 .xlsx 格式");
        }
        try {
            return R.ok(deviceImportService.dryRun(file.getInputStream(), fileName));
        } catch (IOException e) {
            return R.fail("文件读取失败: " + e.getMessage());
        }
    }

    // ==================== 批次管理 ====================

    @GetMapping("/batches")
    @ApiOperation("获取所有导入批次列表")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
    public R<List<String>> listBatches() {
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

    // ==================== 导出 ====================

    @GetMapping("/export/csv")
    @ApiOperation("导出设备为CSV")
    @PreAuthorize("hasAnyRole('LAB_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String batchId) throws IOException {

        List<Device> devices;
        if (batchId != null) {
            devices = deviceService.listByBatchId(batchId);
        } else {
            IPage<Device> page = deviceService.pageQuery(1, 10000, null, categoryId, null, null, null);
            devices = page.getRecords();
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

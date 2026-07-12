package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzhu.equipment.common.R;
import lombok.extern.slf4j.Slf4j;
import com.gzhu.equipment.entity.BorrowRecord;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.SysUser;
import com.gzhu.equipment.mapper.BorrowRecordMapper;
import com.gzhu.equipment.mapper.DeviceCategoryMapper;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.mapper.SysUserMapper;
import com.gzhu.equipment.security.JwtUserPrincipal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/statistics")
@RequiredArgsConstructor
@Api(tags = "数据统计")
public class StatisticsController {

    private final DeviceMapper deviceMapper;
    private final BorrowRecordMapper borrowMapper;
    private final DeviceCategoryMapper categoryMapper;
    private final SysUserMapper sysUserMapper;

    /** 如果是教师角色，返回其姓名用于筛选持有设备；否则返回null */
    private String getTeacherCustodian() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtUserPrincipal p) {
            SysUser user = sysUserMapper.selectById(p.getUserId());
            if (user != null && user.getUserType() != null && user.getUserType() == 1) {
                return user.getRealName();
            }
        }
        return null;
    }

    /** 构建设备查询条件（教师只看自己持有的设备） */
    private LambdaQueryWrapper<Device> deviceWrapper() {
        LambdaQueryWrapper<Device> w = new LambdaQueryWrapper<>();
        String custodian = getTeacherCustodian();
        if (custodian != null) w.eq(Device::getCustodian, custodian);
        return w;
    }

    @GetMapping("/overview")
    @ApiOperation("仪表盘概览")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public R<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>();

        Long totalDevices = deviceMapper.selectCount(deviceWrapper());
        Long availableCount = deviceMapper.selectCount(deviceWrapper().eq(Device::getBorrowStatus, 1));
        Long deviceBorrowingCount = deviceMapper.selectCount(deviceWrapper().eq(Device::getBorrowStatus, 2));
        Long unavailableCount = deviceMapper.selectCount(deviceWrapper().eq(Device::getBorrowStatus, 3));
        Long overdueCount2 = deviceMapper.selectCount(deviceWrapper().eq(Device::getBorrowStatus, 4));
        Long normalCount = deviceMapper.selectCount(deviceWrapper().eq(Device::getDeviceStatus, 1));
        Long repairCount = deviceMapper.selectCount(deviceWrapper().eq(Device::getDeviceStatus, 3));
        Long pendingRepair = deviceMapper.selectCount(deviceWrapper().eq(Device::getDeviceStatus, 2));
        Long pendingScrap = deviceMapper.selectCount(deviceWrapper().eq(Device::getDeviceStatus, 4));
        Long scrappedCount = deviceMapper.selectCount(deviceWrapper().eq(Device::getDeviceStatus, 5));

        Map<String, Long> deviceStats = new LinkedHashMap<>();
        deviceStats.put("total", totalDevices);
        deviceStats.put("borrowAvailable", availableCount);
        deviceStats.put("borrowBorrowing", deviceBorrowingCount);
        deviceStats.put("borrowUnavailable", unavailableCount);
        deviceStats.put("borrowOverdue", overdueCount2);
        deviceStats.put("deviceNormal", normalCount);
        deviceStats.put("devicePendingRepair", pendingRepair);
        deviceStats.put("deviceRepairing", repairCount);
        deviceStats.put("devicePendingScrap", pendingScrap);
        deviceStats.put("deviceScrapped", scrappedCount);
        // 向后兼容旧前端
        deviceStats.put("available", availableCount);
        deviceStats.put("borrowing", deviceBorrowingCount);
        deviceStats.put("repair", repairCount);
        deviceStats.put("scrap", pendingScrap + scrappedCount);
        data.put("deviceStats", deviceStats);

        // 教师只统计持有设备的借用记录
        String custodian = getTeacherCustodian();
        LambdaQueryWrapper<BorrowRecord> bwBase = new LambdaQueryWrapper<>();
        if (custodian != null) {
            bwBase.apply("device_id IN (SELECT id FROM device WHERE custodian = {0})", custodian);
        }
        Long borrowingCount = borrowMapper.selectCount(bwBase.clone().eq(BorrowRecord::getStatus, "BORROWING"));
        Long overdueCount = borrowMapper.selectCount(bwBase.clone().eq(BorrowRecord::getStatus, "OVERDUE"));
        Long pendingCount = borrowMapper.selectCount(bwBase.clone().eq(BorrowRecord::getStatus, "PENDING_APPROVAL"));
        Long totalBorrows = borrowMapper.selectCount(bwBase);

        Map<String, Long> borrowStats = new LinkedHashMap<>();
        borrowStats.put("borrowing", borrowingCount);
        borrowStats.put("overdue", overdueCount);
        borrowStats.put("pendingApproval", pendingCount);
        borrowStats.put("total", totalBorrows);
        data.put("borrowStats", borrowStats);

        return R.ok(data);
    }

    /** 教师角色限制借用查询条件 */
    private void applyTeacherFilter(QueryWrapper<BorrowRecord> w) {
        String custodian = getTeacherCustodian();
        if (custodian != null) {
            w.apply("device_id IN (SELECT id FROM device WHERE custodian = {0})", custodian);
        }
    }

    @GetMapping("/trend")
    @ApiOperation("本月借用趋势（按天）")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<List<Map<String, Object>>> trend() {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = LocalDate.now();

        var w = new QueryWrapper<BorrowRecord>()
                .select("DATE(create_time) as date", "COUNT(*) as count")
                .ge("create_time", start.atStartOfDay())
                .le("create_time", end.plusDays(1).atStartOfDay())
                .groupBy("DATE(create_time)")
                .orderByAsc("date");
        applyTeacherFilter(w);
        var rows = borrowMapper.selectMaps(w);

        return R.ok(rows);
    }

    @GetMapping("/top-devices")
    @ApiOperation("热门设备 TOP10")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<List<Map<String, Object>>> topDevices() {
        try {
            var w = new QueryWrapper<BorrowRecord>()
                    .select("d.name as deviceName", "COUNT(*) as borrowCount")
                    .apply("LEFT JOIN device d ON borrow_record.device_id = d.id")
                    .groupBy("d.name")
                    .orderByDesc("borrowCount")
                    .last("LIMIT 10");
            applyTeacherFilter(w);
            var rows = borrowMapper.selectMaps(w);
            return R.ok(rows != null ? rows : java.util.Collections.emptyList());
        } catch (Exception e) {
            log.warn("热门设备查询失败: {}", e.getMessage());
            return R.ok(java.util.Collections.emptyList());
        }
    }

    @GetMapping("/top-users")
    @ApiOperation("高频借用用户 TOP10")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<List<Map<String, Object>>> topUsers() {
        try {
            var w = new QueryWrapper<BorrowRecord>()
                    .select("u.real_name as userName", "COUNT(*) as borrowCount")
                    .apply("LEFT JOIN sys_user u ON borrow_record.user_id = u.id")
                    .groupBy("u.real_name")
                    .orderByDesc("borrowCount")
                    .last("LIMIT 10");
            applyTeacherFilter(w);
            var rows = borrowMapper.selectMaps(w);
            return R.ok(rows != null ? rows : java.util.Collections.emptyList());
        } catch (Exception e) {
            log.warn("热门用户查询失败: {}", e.getMessage());
            return R.ok(java.util.Collections.emptyList());
        }
    }

    @GetMapping("/utilization")
    @ApiOperation("设备利用率分析（按分类）")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<List<Map<String, Object>>> utilization() {
        var w = new QueryWrapper<BorrowRecord>()
                .select("dc.name as categoryName", "COUNT(*) as borrowCount")
                .apply("LEFT JOIN device d ON borrow_record.device_id = d.id")
                .apply("LEFT JOIN device_category dc ON d.category_id = dc.id")
                .groupBy("dc.name")
                .orderByDesc("borrowCount")
                .last("LIMIT 10");
        applyTeacherFilter(w);
        var rows = borrowMapper.selectMaps(w);
        return R.ok(rows);
    }

    @GetMapping(value = "/export", produces = "application/octet-stream")
    @ApiOperation("导出统计报表（CSV/XLSX）")
    @PreAuthorize("hasAuthority('statistics:view')")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(defaultValue = "csv") String format) throws Exception {

        if ("xlsx".equalsIgnoreCase(format)) {
            LinkedHashMap<String, String> headers = new LinkedHashMap<>();
            headers.put("name","指标"); headers.put("value","数值");
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(Map.of("name","设备总数","value",deviceMapper.selectCount(null)));
            rows.add(Map.of("name","--借还状态--","value",""));
            rows.add(Map.of("name","可借用","value",deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus, 1))));
            rows.add(Map.of("name","借用中","value",deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus, 2))));
            rows.add(Map.of("name","不可借","value",deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus, 3))));
            rows.add(Map.of("name","逾期","value",deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus, 4))));
            rows.add(Map.of("name","--设备状态--","value",""));
            rows.add(Map.of("name","正常","value",deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus, 1))));
            rows.add(Map.of("name","待维修","value",deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus, 2))));
            rows.add(Map.of("name","维修中","value",deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus, 3))));
            rows.add(Map.of("name","待报废","value",deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus, 4))));
            rows.add(Map.of("name","已报废","value",deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus, 5))));
            rows.add(Map.of("name","借出中","value",borrowMapper.selectCount(new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getStatus, "BORROWING"))));
            rows.add(Map.of("name","逾期未还","value",borrowMapper.selectCount(new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getStatus, "OVERDUE"))));
            rows.add(Map.of("name","待审批","value",borrowMapper.selectCount(new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getStatus, "PENDING_APPROVAL"))));
            rows.add(Map.of("name","总借用次数","value",borrowMapper.selectCount(null)));
            byte[] xlsx = com.gzhu.equipment.common.ExcelExportUtil.exportToXlsx(rows, headers);
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            h.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statistics_export_" + System.currentTimeMillis() + ".xlsx");
            return ResponseEntity.ok().headers(h).body(xlsx);
        }

        // CSV
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0xEF); bos.write(0xBB); bos.write(0xBF); // BOM
        try (OutputStreamWriter w = new OutputStreamWriter(bos, StandardCharsets.UTF_8)) {
            w.write("指标,数值\n");
            w.write("设备总数," + deviceMapper.selectCount(null) + "\n");
            w.write("--借还状态--\n");
            w.write("可借用," + deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus, 1)) + "\n");
            w.write("借用中," + deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus, 2)) + "\n");
            w.write("不可借," + deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus, 3)) + "\n");
            w.write("逾期," + deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getBorrowStatus, 4)) + "\n");
            w.write("--设备状态--\n");
            w.write("正常," + deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus, 1)) + "\n");
            w.write("待维修," + deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus, 2)) + "\n");
            w.write("维修中," + deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus, 3)) + "\n");
            w.write("待报废," + deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus, 4)) + "\n");
            w.write("已报废," + deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceStatus, 5)) + "\n");
            w.write("借出中," + borrowMapper.selectCount(new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getStatus, "BORROWING")) + "\n");
            w.write("逾期未还," + borrowMapper.selectCount(new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getStatus, "OVERDUE")) + "\n");
            w.write("待审批," + borrowMapper.selectCount(new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getStatus, "PENDING_APPROVAL")) + "\n");
            w.write("总借用次数," + borrowMapper.selectCount(null) + "\n");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statistics_export_" + System.currentTimeMillis() + ".csv");
        return ResponseEntity.ok().headers(headers).body(bos.toByteArray());
    }

    // ==================== V6 目的与成果统计（增强版） ====================

    @GetMapping("/purposes")
    @ApiOperation("借用目的分布统计（按大类）")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<List<Map<String, Object>>> purposeStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long categoryId) {
        var w = new QueryWrapper<BorrowRecord>()
                .select("COALESCE(NULLIF(purpose_category,''),'未分类') as name", "COUNT(*) as value")
                .isNotNull("purpose_category").ne("purpose_category", "")
                .groupBy("purpose_category").orderByDesc("value");
        if (startDate != null) w.ge("borrow_record.create_time", startDate + " 00:00:00");
        if (endDate != null) w.le("borrow_record.create_time", endDate + " 23:59:59");
        if (categoryId != null) w.apply("device_id IN (SELECT id FROM device WHERE category_id = {0})", categoryId);
        var rows = borrowMapper.selectMaps(w);
        // 补充未分类统计
        var uncatW = new QueryWrapper<BorrowRecord>()
                .select("'未分类' as name", "COUNT(*) as value")
                .and(qw -> qw.isNull("purpose_category").or().eq("purpose_category", ""));
        if (startDate != null) uncatW.ge("create_time", startDate + " 00:00:00");
        if (endDate != null) uncatW.le("create_time", endDate + " 23:59:59");
        if (categoryId != null) uncatW.apply("device_id IN (SELECT id FROM device WHERE category_id = {0})", categoryId);
        var uncat = borrowMapper.selectMaps(uncatW);
        if (uncat != null && !uncat.isEmpty() && uncat.get(0) != null) {
            Long v = (Long) uncat.get(0).get("value");
            if (v != null && v > 0) {
                Map<String, Object> m = new java.util.LinkedHashMap<>(); m.put("name", "未分类"); m.put("value", v); rows.add(m);
            }
        }
        return R.ok(rows);
    }

    @GetMapping("/purposes/detail")
    @ApiOperation("目的分类详细统计（大类+子分类+设备分类）")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<Map<String, Object>> purposeDetail(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long categoryId) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();

        // 子分类统计
        var subW = new QueryWrapper<BorrowRecord>()
                .select("COALESCE(NULLIF(purpose_subcategory,''),'未指定') as name", "COUNT(*) as value")
                .groupBy("purpose_subcategory").orderByDesc("value");
        if (startDate != null) subW.ge("borrow_record.create_time", startDate + " 00:00:00");
        if (endDate != null) subW.le("borrow_record.create_time", endDate + " 23:59:59");
        if (categoryId != null) subW.apply("device_id IN (SELECT id FROM device WHERE category_id = {0})", categoryId);
        result.put("subcategories", borrowMapper.selectMaps(subW));

        // 按设备分类的目的分布
        var catW = new QueryWrapper<BorrowRecord>()
                .select("COALESCE(dc.name,'未分类设备') as name", "COUNT(*) as value")
                .apply("LEFT JOIN device d ON borrow_record.device_id = d.id")
                .apply("LEFT JOIN device_category dc ON d.category_id = dc.id")
                .groupBy("dc.name").orderByDesc("value");
        if (startDate != null) catW.ge("borrow_record.create_time", startDate + " 00:00:00");
        if (endDate != null) catW.le("borrow_record.create_time", endDate + " 23:59:59");
        if (categoryId != null) catW.apply("d.category_id = {0}", categoryId);
        result.put("byDeviceCategory", borrowMapper.selectMaps(catW));

        return R.ok(result);
    }

    @GetMapping("/outcomes/stats")
    @ApiOperation("成果产出统计（概述+分布+趋势）")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<Map<String, Object>> outcomeStats(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();

        // 成果总数
        var outcomeW = new QueryWrapper<BorrowRecord>()
                .isNotNull("outcome").ne("outcome", "");
        if (deviceId != null) outcomeW.eq("device_id", deviceId);
        if (startDate != null) outcomeW.ge("create_time", startDate + " 00:00:00");
        if (endDate != null) outcomeW.le("create_time", endDate + " 23:59:59");
        Long outcomeTotal = borrowMapper.selectCount(outcomeW);
        result.put("outcomeTotal", outcomeTotal != null ? outcomeTotal : 0);

        // 从 borrow_outcome 表统计成果类型分布
        try {
            var typeW = new QueryWrapper<BorrowRecord>()
                    .select("o.outcome_type as name", "COUNT(*) as value")
                    .apply("LEFT JOIN borrow_outcome o ON borrow_record.id = o.borrow_id")
                    .isNotNull("o.outcome_type").ne("o.outcome_type", "")
                    .groupBy("o.outcome_type").orderByDesc("value");
            if (deviceId != null) typeW.eq("borrow_record.device_id", deviceId);
            if (startDate != null) typeW.ge("borrow_record.create_time", startDate + " 00:00:00");
            if (endDate != null) typeW.le("borrow_record.create_time", endDate + " 23:59:59");
            result.put("distribution", borrowMapper.selectMaps(typeW));
        } catch (Exception e) {
            log.warn("成果类型分布查询失败: {}", e.getMessage());
            result.put("distribution", java.util.Collections.emptyList());
        }

        // 按设备统计 TOP10
        var deviceW = new QueryWrapper<BorrowRecord>()
                .select("d.name as name", "COUNT(*) as value")
                .isNotNull("outcome").ne("outcome", "")
                .apply("LEFT JOIN device d ON borrow_record.device_id = d.id")
                .groupBy("d.name").orderByDesc("value").last("LIMIT 10");
        if (deviceId != null) deviceW.eq("d.id", deviceId);
        if (startDate != null) deviceW.ge("borrow_record.create_time", startDate + " 00:00:00");
        if (endDate != null) deviceW.le("borrow_record.create_time", endDate + " 23:59:59");
        result.put("deviceTop10", borrowMapper.selectMaps(deviceW));

        // 按月趋势
        var monthW = new QueryWrapper<BorrowRecord>()
                .select("DATE_FORMAT(outcome_recorded_time,'%Y-%m') as name", "COUNT(*) as value")
                .isNotNull("outcome").ne("outcome", "")
                .groupBy("DATE_FORMAT(outcome_recorded_time,'%Y-%m')")
                .orderByAsc("name").last("LIMIT 12");
        if (startDate != null) monthW.ge("borrow_record.create_time", startDate + " 00:00:00");
        if (endDate != null) monthW.le("borrow_record.create_time", endDate + " 23:59:59");
        result.put("monthTrend", borrowMapper.selectMaps(monthW));

        return R.ok(result);
    }

    @GetMapping("/device-outcomes")
    @ApiOperation("按设备查看成果列表")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<List<Map<String, Object>>> deviceOutcomes(@RequestParam Long deviceId) {
        var rows = borrowMapper.selectMaps(new QueryWrapper<BorrowRecord>()
                .select("id", "purpose", "purpose_category", "outcome",
                        "outcome_recorded_time", "user_id")
                .eq("device_id", deviceId)
                .isNotNull("outcome").ne("outcome", "")
                .orderByDesc("outcome_recorded_time"));
        return R.ok(rows != null ? rows : java.util.Collections.emptyList());
    }
}

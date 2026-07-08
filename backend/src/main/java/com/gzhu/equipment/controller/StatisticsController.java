package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gzhu.equipment.common.R;
import lombok.extern.slf4j.Slf4j;
import com.gzhu.equipment.entity.BorrowRecord;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.mapper.BorrowRecordMapper;
import com.gzhu.equipment.mapper.DeviceCategoryMapper;
import com.gzhu.equipment.mapper.DeviceMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping("/overview")
    @ApiOperation("仪表盘概览")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public R<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>();

        Long totalDevices = deviceMapper.selectCount(null);
        Long normalCount = deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getStatus, 1));
        Long repairCount = deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getStatus, 2));
        Long scrapCount = deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getStatus, 3));

        Map<String, Long> deviceStats = new LinkedHashMap<>();
        deviceStats.put("total", totalDevices);
        deviceStats.put("normal", normalCount);
        deviceStats.put("repair", repairCount);
        deviceStats.put("scrap", scrapCount);
        data.put("deviceStats", deviceStats);

        Long borrowingCount = borrowMapper.selectCount(new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getStatus, "BORROWING"));
        Long overdueCount = borrowMapper.selectCount(new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getStatus, "OVERDUE"));
        Long pendingCount = borrowMapper.selectCount(new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getStatus, "PENDING_APPROVAL"));
        Long totalBorrows = borrowMapper.selectCount(null);

        Map<String, Long> borrowStats = new LinkedHashMap<>();
        borrowStats.put("borrowing", borrowingCount);
        borrowStats.put("overdue", overdueCount);
        borrowStats.put("pendingApproval", pendingCount);
        borrowStats.put("total", totalBorrows);
        data.put("borrowStats", borrowStats);

        return R.ok(data);
    }

    @GetMapping("/trend")
    @ApiOperation("本月借用趋势（按天）")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<List<Map<String, Object>>> trend() {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = LocalDate.now();

        var rows = borrowMapper.selectMaps(new QueryWrapper<BorrowRecord>()
                .select("DATE(create_time) as date", "COUNT(*) as count")
                .ge("create_time", start.atStartOfDay())
                .le("create_time", end.plusDays(1).atStartOfDay())
                .groupBy("DATE(create_time)")
                .orderByAsc("date"));

        return R.ok(rows);
    }

    @GetMapping("/top-devices")
    @ApiOperation("热门设备 TOP10")
    @PreAuthorize("hasAuthority('statistics:view')")
    public R<List<Map<String, Object>>> topDevices() {
        try {
            var rows = borrowMapper.selectMaps(new QueryWrapper<BorrowRecord>()
                    .select("d.name as deviceName", "COUNT(*) as borrowCount")
                    .apply("LEFT JOIN device d ON borrow_record.device_id = d.id")
                    .groupBy("d.name")
                    .orderByDesc("borrowCount")
                    .last("LIMIT 10"));
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
            var rows = borrowMapper.selectMaps(new QueryWrapper<BorrowRecord>()
                    .select("u.real_name as userName", "COUNT(*) as borrowCount")
                    .apply("LEFT JOIN sys_user u ON borrow_record.user_id = u.id")
                    .groupBy("u.real_name")
                    .orderByDesc("borrowCount")
                    .last("LIMIT 10"));
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
        var rows = borrowMapper.selectMaps(new QueryWrapper<BorrowRecord>()
                .select("dc.name as categoryName", "COUNT(*) as borrowCount")
                .apply("LEFT JOIN device d ON borrow_record.device_id = d.id")
                .apply("LEFT JOIN device_category dc ON d.category_id = dc.id")
                .groupBy("dc.name")
                .orderByDesc("borrowCount")
                .last("LIMIT 10"));
        return R.ok(rows);
    }

    @GetMapping("/export")
    @ApiOperation("导出统计报表（CSV）")
    @PreAuthorize("hasAuthority('statistics:view')")
    public ResponseEntity<byte[]> exportCsv() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0xEF); bos.write(0xBB); bos.write(0xBF); // BOM

        try (OutputStreamWriter w = new OutputStreamWriter(bos, StandardCharsets.UTF_8)) {
            w.write("指标,数值\n");
            w.write("设备总数," + deviceMapper.selectCount(null) + "\n");
            w.write("正常设备," + deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getStatus, 1)) + "\n");
            w.write("维修中设备," + deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getStatus, 2)) + "\n");
            w.write("报废设备," + deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getStatus, 3)) + "\n");
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
}

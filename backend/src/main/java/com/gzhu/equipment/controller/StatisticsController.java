package com.gzhu.equipment.controller;

import com.gzhu.equipment.common.R;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.BorrowRecord;
import com.gzhu.equipment.mapper.BorrowRecordMapper;
import com.gzhu.equipment.mapper.DeviceCategoryMapper;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 数据统计控制器
 */
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

        // 设备统计
        Long totalDevices = deviceMapper.selectCount(null);
        Long normalCount = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>().eq(Device::getStatus, 1));
        Long repairCount = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>().eq(Device::getStatus, 2));
        Long scrapCount = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>().eq(Device::getStatus, 3));

        Map<String, Long> deviceStats = new LinkedHashMap<>();
        deviceStats.put("total", totalDevices);
        deviceStats.put("normal", normalCount);
        deviceStats.put("repair", repairCount);
        deviceStats.put("scrap", scrapCount);
        data.put("deviceStats", deviceStats);

        // 借用统计
        Long borrowingCount = borrowMapper.selectCount(
                new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getStatus, "BORROWING"));
        Long overdueCount = borrowMapper.selectCount(
                new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getStatus, "OVERDUE"));
        Long pendingCount = borrowMapper.selectCount(
                new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getStatus, "PENDING_APPROVAL"));

        Map<String, Long> borrowStats = new LinkedHashMap<>();
        borrowStats.put("borrowing", borrowingCount);
        borrowStats.put("overdue", overdueCount);
        borrowStats.put("pendingApproval", pendingCount);
        data.put("borrowStats", borrowStats);

        return R.ok(data);
    }
}

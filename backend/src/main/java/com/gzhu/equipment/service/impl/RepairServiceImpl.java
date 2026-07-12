package com.gzhu.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.RepairRecord;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.mapper.RepairRecordMapper;
import com.gzhu.equipment.service.RepairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/**
 * V3: 维修管理 — 使用新设备状态 & 完整生命周期
 * 设备状态流转: 正常(1) → 待维修(2) → 维修中(3) → 正常(1) 或 待报废(4)
 */
@Slf4j @Service @RequiredArgsConstructor
public class RepairServiceImpl extends ServiceImpl<RepairRecordMapper, RepairRecord> implements RepairService {
    private final RepairRecordMapper repairMapper;
    private final DeviceMapper deviceMapper;

    @Override @Transactional
    public RepairRecord createFromDamage(Long deviceId, Long borrowId, String faultDescription) {
        Device device = deviceMapper.selectById(deviceId);
        if (device != null) {
            device.setBorrowStatus(3);   // 不可借
            device.setDeviceStatus(2);   // 待维修
            deviceMapper.updateById(device);
        }
        RepairRecord r = new RepairRecord();
        r.setDeviceId(deviceId); r.setBorrowId(borrowId);
        r.setFaultDescription(faultDescription); r.setStatus("PENDING");
        repairMapper.insert(r);
        log.info("维修记录已创建(PENDING): deviceId={} newDeviceStatus=待维修", deviceId);
        return r;
    }

    @Override @Transactional
    public void startRepair(Long id, Long repairBy) {
        RepairRecord r = repairMapper.selectById(id);
        if (r != null) {
            r.setStatus("REPAIRING"); r.setRepairBy(repairBy); repairMapper.updateById(r);
            Device device = deviceMapper.selectById(r.getDeviceId());
            if (device != null) {
                device.setBorrowStatus(3);   // 不可借
                device.setDeviceStatus(3);   // 维修中
                deviceMapper.updateById(device);
            }
            log.info("维修已开始: repairId={} deviceStatus=维修中", id);
        }
    }

    @Override @Transactional
    public void markFixed(Long id, String comment) {
        RepairRecord r = repairMapper.selectById(id);
        if (r != null) {
            r.setStatus("FIXED"); r.setRepairComment(comment);
            r.setFixedTime(LocalDateTime.now()); repairMapper.updateById(r);
            Device device = deviceMapper.selectById(r.getDeviceId());
            if (device != null && device.getDeviceStatus() != null && device.getDeviceStatus() == 3) {
                device.setBorrowStatus(1);   // 可借用
                device.setDeviceStatus(1);   // 正常
                deviceMapper.updateById(device);
                log.info("维修已完成: deviceId={} deviceStatus=正常", r.getDeviceId());
            }
        }
    }

    /** V3新增：无法修复 → 设备状态变为无法维修 */
    @Override @Transactional
    public void markUnrepairable(Long id, String comment) {
        RepairRecord r = repairMapper.selectById(id);
        if (r != null) {
            r.setStatus("UNREPAIRABLE"); r.setRepairComment(comment);
            r.setFixedTime(LocalDateTime.now()); repairMapper.updateById(r);
            Device device = deviceMapper.selectById(r.getDeviceId());
            if (device != null) {
                device.setBorrowStatus(3);   // 不可借
                device.setDeviceStatus(3);   // 无法维修
                deviceMapper.updateById(device);
                log.info("设备标记无法维修: deviceId={}", r.getDeviceId());
            }
        }
    }

    /** V3新增：标记设备待报废 */
    @Override @Transactional
    public void markScrap(Long id, String comment) {
        RepairRecord r = repairMapper.selectById(id);
        if (r != null) {
            r.setStatus("FIXED"); r.setRepairComment("报废: " + (comment != null ? comment : ""));
            r.setFixedTime(LocalDateTime.now()); repairMapper.updateById(r);
            Device device = deviceMapper.selectById(r.getDeviceId());
            if (device != null) {
                device.setBorrowStatus(3);   // 不可借
                device.setDeviceStatus(4);   // 待报废
                deviceMapper.updateById(device);
                log.info("设备标记待报废: deviceId={}", r.getDeviceId());
            }
        }
    }

    /** V3新增：确认报废 */
    @Override @Transactional
    public void confirmScrap(Long deviceId, String comment) {
        Device device = deviceMapper.selectById(deviceId);
        if (device != null) {
            device.setBorrowStatus(3);   // 不可借
            device.setDeviceStatus(5);   // 已报废
            deviceMapper.updateById(device);
            // 创建维修记录留痕
            RepairRecord r = new RepairRecord();
            r.setDeviceId(deviceId); r.setFaultDescription("确认报废: " + (comment != null ? comment : ""));
            r.setStatus("FIXED"); r.setRepairComment(comment);
            r.setFixedTime(LocalDateTime.now());
            repairMapper.insert(r);
            log.info("设备已报废: deviceId={}", deviceId);
        }
    }

    @Override
    public IPage<RepairRecord> list(int page, int size, String status) {
        LambdaQueryWrapper<RepairRecord> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) w.eq(RepairRecord::getStatus, status);
        w.orderByDesc(RepairRecord::getCreateTime);
        return repairMapper.selectPage(new Page<>(page, size), w);
    }

    /** V3新增：按设备状态查询维修设备列表 */
    @Override
    public IPage<Device> listRepairDevices(int page, int size, Integer deviceStatus) {
        LambdaQueryWrapper<Device> w = new LambdaQueryWrapper<Device>()
                .in(Device::getDeviceStatus, deviceStatus != null ? List.of(deviceStatus) :
                        List.of(2, 3, 4))  // 默认查待维修+维修中+待报废
                .orderByAsc(Device::getDeviceStatus)
                .orderByDesc(Device::getUpdateTime);
        return deviceMapper.selectPage(new Page<>(page, size), w);
    }
}

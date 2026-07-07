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

@Slf4j @Service @RequiredArgsConstructor
public class RepairServiceImpl extends ServiceImpl<RepairRecordMapper, RepairRecord> implements RepairService {
    private final RepairRecordMapper repairMapper;
    private final DeviceMapper deviceMapper;

    @Override @Transactional
    public RepairRecord createFromDamage(Long deviceId, Long borrowId, String faultDescription) {
        Device device = deviceMapper.selectById(deviceId);
        if (device != null) { device.setStatus(2); deviceMapper.updateById(device); }
        RepairRecord r = new RepairRecord();
        r.setDeviceId(deviceId); r.setBorrowId(borrowId); r.setFaultDescription(faultDescription); r.setStatus("PENDING");
        repairMapper.insert(r);
        log.info("维修记录已创建: deviceId={}", deviceId);
        return r;
    }

    @Override @Transactional
    public void startRepair(Long id, Long repairBy) {
        RepairRecord r = repairMapper.selectById(id);
        if (r != null) { r.setStatus("REPAIRING"); r.setRepairBy(repairBy); repairMapper.updateById(r); }
    }

    @Override @Transactional
    public void markFixed(Long id, String comment) {
        RepairRecord r = repairMapper.selectById(id);
        if (r != null) {
            r.setStatus("FIXED"); r.setRepairComment(comment); r.setFixedTime(LocalDateTime.now()); repairMapper.updateById(r);
            Device device = deviceMapper.selectById(r.getDeviceId());
            if (device != null && device.getStatus() == 2) { device.setStatus(1); deviceMapper.updateById(device); }
            log.info("设备维修完成: deviceId={}", r.getDeviceId());
        }
    }

    @Override
    public IPage<RepairRecord> list(int page, int size, String status) {
        LambdaQueryWrapper<RepairRecord> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) w.eq(RepairRecord::getStatus, status);
        w.orderByDesc(RepairRecord::getCreateTime);
        return repairMapper.selectPage(new Page<>(page, size), w);
    }
}

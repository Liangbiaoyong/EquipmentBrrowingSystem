package com.gzhu.equipment.service.impl;

import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.RepairRecord;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.mapper.RepairRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepairServiceImplTest {

    @Mock private RepairRecordMapper repairMapper;
    @Mock private DeviceMapper deviceMapper;

    private RepairServiceImpl repairService;

    @BeforeEach
    void setUp() {
        repairService = new RepairServiceImpl(repairMapper, deviceMapper);
    }

    @Test @DisplayName("创建维修记录 → 设备标记维修中")
    void createFromDamage_shouldMarkDeviceStatus() {
        Device device = new Device(); device.setId(1L); device.setBorrowStatus(1); device.setDeviceStatus(1);
        when(deviceMapper.selectById(1L)).thenReturn(device);

        RepairRecord result = repairService.createFromDamage(1L, 100L, "镜头破损");

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getDeviceId()).isEqualTo(1L);
        assertThat(device.getBorrowStatus()).isEqualTo(3);
        assertThat(device.getDeviceStatus()).isEqualTo(2); // createFromDamage → 待维修(2)
        verify(deviceMapper).updateById(device);
        verify(repairMapper).insert(any(RepairRecord.class));
    }

    @Test @DisplayName("开始维修 → 状态变为REPAIRING")
    void startRepair_shouldSetRepairing() {
        RepairRecord r = new RepairRecord(); r.setId(1L); r.setStatus("PENDING");
        when(repairMapper.selectById(1L)).thenReturn(r);

        repairService.startRepair(1L, 5L);

        assertThat(r.getStatus()).isEqualTo("REPAIRING");
        assertThat(r.getRepairBy()).isEqualTo(5L);
        verify(repairMapper).updateById(r);
    }

    @Test @DisplayName("修复完成 → 状态变为FIXED，设备恢复正常")
    void markFixed_shouldSetFixedAndRestoreDevice() {
        RepairRecord r = new RepairRecord(); r.setId(1L); r.setDeviceId(1L); r.setStatus("REPAIRING");
        when(repairMapper.selectById(1L)).thenReturn(r);
        Device device = new Device(); device.setId(1L); device.setBorrowStatus(3); device.setDeviceStatus(3);
        when(deviceMapper.selectById(1L)).thenReturn(device);

        repairService.markFixed(1L, "已修好");

        assertThat(r.getStatus()).isEqualTo("FIXED");
        assertThat(r.getRepairComment()).isEqualTo("已修好");
        assertThat(device.getBorrowStatus()).isEqualTo(1);
        assertThat(device.getDeviceStatus()).isEqualTo(1);
        verify(repairMapper).updateById(r);
        verify(deviceMapper).updateById(device);
    }

    @Test @DisplayName("list → 调用分页查询")
    void list_shouldCallSelectPage() {
        repairService.list(1, 20, "PENDING");
        verify(repairMapper).selectPage(any(), any());
    }

    @Test @DisplayName("list（无状态筛选）→ 同样调用分页")
    void list_noStatus_shouldCallSelectPage() {
        repairService.list(1, 20, null);
        verify(repairMapper).selectPage(any(), any());
    }
}

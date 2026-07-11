package com.gzhu.equipment.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.mapper.DeviceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DeviceServiceImpl 单元测试
 *
 * 覆盖：分页查询（多条件筛选）、资产编号查找、删除、批次管理
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceImplTest {

    @Mock
    private DeviceMapper deviceMapper;

    private DeviceServiceImpl deviceService;

    @BeforeEach
    void setUp() {
        deviceService = new DeviceServiceImpl(deviceMapper);
    }

    // ==================== 分页查询 ====================

    @Test
    @DisplayName("分页查询 → 调用 selectPage")
    void pageQuery_shouldCallSelectPage() {
        deviceService.pageQuery(1, 20, "电脑", 1L, 1, "三楼", null, null, null);

        verify(deviceMapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("分页查询（无筛选条件）→ 调用 selectPage")
    void pageQuery_noFilters_shouldCallSelectPage() {
        deviceService.pageQuery(1, 10, null, null, null, null, null, null, null);

        verify(deviceMapper).selectPage(any(), any());
    }

    // ==================== 资产编号查询 ====================

    @Test
    @DisplayName("按资产编号查找 → 返回设备")
    void getByAssetNo_shouldReturnDevice() {
        Device mockDevice = new Device();
        mockDevice.setAssetNo("ASSET001");
        when(deviceMapper.selectByAssetNo("ASSET001")).thenReturn(mockDevice);

        Device result = deviceService.getByAssetNo("ASSET001");

        assertThat(result).isNotNull();
        assertThat(result.getAssetNo()).isEqualTo("ASSET001");
    }

    @Test
    @DisplayName("按资产编号查找（不存在）→ 返回null")
    void getByAssetNo_notFound_shouldReturnNull() {
        when(deviceMapper.selectByAssetNo("NONEXIST")).thenReturn(null);

        Device result = deviceService.getByAssetNo("NONEXIST");

        assertThat(result).isNull();
    }

    // ==================== 删除 ====================

    @Test
    @DisplayName("删除单个设备 → 调用 deleteById")
    void deleteDevice_shouldDeleteById() {
        deviceService.deleteDevice(1L);

        verify(deviceMapper).deleteById(1L);
    }

    @Test
    @DisplayName("按批次清除设备 → 调用 delete 返回删除数")
    void deleteByBatchId_shouldDeleteByBatch() {
        when(deviceMapper.delete(any())).thenReturn(5);

        int count = deviceService.deleteByBatchId("BATCH001");

        assertThat(count).isEqualTo(5);
        verify(deviceMapper).delete(any());
    }

    // ==================== 批次管理 ====================

    @Test
    @DisplayName("listBatches → 返回去重的批次列表（通过 selectMaps）")
    void listBatches_shouldReturnDistinctBatches() {
        when(deviceMapper.selectMaps(any())).thenReturn(List.of(
                Collections.singletonMap("import_batch_id", "BATCH001"),
                Collections.singletonMap("import_batch_id", "BATCH002")
        ));

        List<String> batches = deviceService.listBatches();

        assertThat(batches).containsExactly("BATCH001", "BATCH002");
    }

    @Test
    @DisplayName("listByBatchId → 返回批次内设备列表")
    void listByBatchId_shouldReturnDevicesInBatch() {
        Device d1 = new Device(); d1.setImportBatchId("B001");
        Device d2 = new Device(); d2.setImportBatchId("B001");
        when(deviceMapper.selectList(any())).thenReturn(List.of(d1, d2));

        List<Device> devices = deviceService.listByBatchId("B001");

        assertThat(devices).hasSize(2);
    }
}

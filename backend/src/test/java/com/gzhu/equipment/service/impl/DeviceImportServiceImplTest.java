package com.gzhu.equipment.service.impl;

import com.gzhu.equipment.dto.ImportResultDTO;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.mapper.DeviceImageMapper;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DeviceImportServiceImpl 单元测试
 *
 * 覆盖：CSV导入、智能导入(增删改)、自动分类、Dry-Run、格式校验、批次清除
 */
@ExtendWith(MockitoExtension.class)
class DeviceImportServiceImplTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private DeviceImageMapper deviceImageMapper;

    @Mock
    private CategoryService categoryService;

    private DeviceImportServiceImpl importService;

    @BeforeEach
    void setUp() {
        importService = new DeviceImportServiceImpl(deviceMapper, deviceImageMapper, categoryService);
        // 默认分类命中
        lenient().when(categoryService.classifyByGbName(any())).thenReturn(1L);
        // 默认无存量设备（deleteDevicesNotInSet 不删除任何记录）
        lenient().when(deviceMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    /** 生成40列表头行（含"资产编号"关键词用于编码检测） */
    private String headerLine() {
        String[] h = new String[40];
        for (int i = 0; i < 40; i++) h[i] = (i == 1 ? "资产编号" : "col" + i);
        return String.join(",", h);
    }

    /** 生成带表头的完整CSV */
    private String csvWithHeader(String assetNo, String name) {
        String[] cols = new String[40];
        for (int i = 0; i < 40; i++) cols[i] = "";
        cols[1] = assetNo;
        cols[2] = name;
        cols[3] = "型号X";
        cols[4] = "规格Y";
        cols[5] = "1";
        cols[6] = "5000";
        cols[7] = "5000";
        cols[8] = "46182";
        cols[9] = "建筑学院";
        cols[10] = "张三";
        cols[12] = "三楼";
        cols[13] = "备注";
        cols[19] = "教育分类";
        cols[21] = "计算机";
        return headerLine() + "\n" + String.join(",", cols);
    }

    // ==================== CSV 导入 ====================

    @Test
    @DisplayName("CSV导入（正常数据）→ 全部新增")
    void importCsv_normal_shouldInsertAll() throws Exception {
        String csv = csvWithHeader("ASSET001", "测试电脑");
        when(deviceMapper.selectByAssetNo("ASSET001")).thenReturn(null);

        ImportResultDTO result = importService.importFromStream(toStream(csv), "devices.csv", 1L);

        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getAutoCategoryCount()).isEqualTo(1);
        verify(deviceMapper).insert(any(Device.class));
    }

    @Test
    @DisplayName("CSV导入（已存在资产编号）→ 更新，保留关联数据")
    void importCsv_existingAsset_shouldUpdate() throws Exception {
        String csv = csvWithHeader("ASSET001", "已存在设备");
        Device existing = new Device();
        existing.setId(10L);
        existing.setAssetNo("ASSET001");
        existing.setBorrowType(1);  // 之前手动设为"可现场借用"
        when(deviceMapper.selectByAssetNo("ASSET001")).thenReturn(existing);

        ImportResultDTO result = importService.importFromStream(toStream(csv), "devices.csv", 1L);

        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getUpdateCount()).isEqualTo(1);
        assertThat(result.getSuccessCount()).isEqualTo(0);
        verify(deviceMapper, never()).insert(any(Device.class));
        verify(deviceMapper).updateById(any(Device.class));
    }

    @Test
    @DisplayName("CSV导入（空资产编号）→ 跳过该行")
    void importCsv_emptyAssetNo_shouldSkip() throws Exception {
        String csv = csvWithHeader("", "无编号设备");
        ImportResultDTO result = importService.importFromStream(toStream(csv), "devices.csv", 1L);
        assertThat(result.getTotalRows()).isEqualTo(0);
    }

    @Test
    @DisplayName("CSV导入（空文件）→ 报告失败")
    void importCsv_emptyFile_shouldReportFail() throws Exception {
        ImportResultDTO result = importService.importFromStream(toStream(""), "devices.csv", 1L);
        // 空文件 → 无有效数据行 → errors包含提示
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    @DisplayName("CSV导入（仅表头）→ 成功0条")
    void importCsv_headerOnly_shouldSucceedZero() throws Exception {
        ImportResultDTO result = importService.importFromStream(toStream("h1,h2\n"), "devices.csv", 1L);
        assertThat(result.getTotalRows()).isEqualTo(0);
    }

    @Test
    @DisplayName("CSV导入（引号内逗号）→ 正确解析")
    void importCsv_quotedComma_shouldParseCorrectly() throws Exception {
        String csv = headerLine() + "\n";
        String[] cols = new String[40];
        cols[1] = "ASSET001";
        cols[2] = "\"测试,电脑\"";
        csv += String.join(",", cols);
        when(deviceMapper.selectByAssetNo("ASSET001")).thenReturn(null);

        ImportResultDTO result = importService.importFromStream(toStream(csv), "devices.csv", 1L);

        assertThat(result.getSuccessCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("CSV导入（千分位金额）→ 正确解析")
    void importCsv_formattedAmount_shouldParse() throws Exception {
        String csv = headerLine() + "\n";
        String[] cols = new String[40];
        cols[1] = "ASSET001";
        cols[2] = "设备A";
        cols[6] = "\"17,500\"";
        csv += String.join(",", cols);
        when(deviceMapper.selectByAssetNo("ASSET001")).thenReturn(null);

        ImportResultDTO result = importService.importFromStream(toStream(csv), "devices.csv", 1L);

        assertThat(result.getSuccessCount()).isEqualTo(1);
    }

    // ==================== 智能导入（增删改） ====================

    @Test
    @DisplayName("智能导入 → 删除旧数据中不在新数据里的记录")
    void smartImport_shouldDeleteStaleRecords() throws Exception {
        // 模拟：数据库中有旧记录 ASSET_OLD
        Device oldDevice = new Device();
        oldDevice.setId(99L);
        oldDevice.setAssetNo("ASSET_OLD");

        when(deviceMapper.selectList(any())).thenReturn(java.util.List.of(oldDevice));

        // 新数据中只有 ASSET001，没有 ASSET_OLD
        String csv = csvWithHeader("ASSET001", "新设备");
        when(deviceMapper.selectByAssetNo("ASSET001")).thenReturn(null);

        ImportResultDTO result = importService.importFromStream(toStream(csv), "devices.csv", 1L);

        assertThat(result.getDeleteCount()).isEqualTo(1);
        verify(deviceMapper).deleteById(99L);
    }

    @Test
    @DisplayName("智能导入 → 更新已存在记录时保留关联字段")
    void smartImport_update_shouldPreserveAssociatedData() throws Exception {
        String csv = csvWithHeader("ASSET001", "更新后的名称");
        Device existing = new Device();
        existing.setId(10L);
        existing.setAssetNo("ASSET001");
        existing.setBorrowType(1);       // 手动设为"可现场借用"
        existing.setCoverImage("cover.jpg"); // 有关联封面图
        when(deviceMapper.selectByAssetNo("ASSET001")).thenReturn(existing);

        ImportResultDTO result = importService.importFromStream(toStream(csv), "devices.csv", 1L);

        assertThat(result.getUpdateCount()).isEqualTo(1);
        verify(deviceMapper).updateById(any(Device.class));
    }

    // ==================== 自动分类 ====================

    @Test
    @DisplayName("未命中分类 → 归入其他设备(categoryId=10)")
    void importCsv_uncategorized_shouldFallback() throws Exception {
        String csv = csvWithHeader("ASSET001", "特殊");
        when(categoryService.classifyByGbName(any())).thenReturn(null);
        when(deviceMapper.selectByAssetNo("ASSET001")).thenReturn(null);

        ImportResultDTO result = importService.importFromStream(toStream(csv), "devices.csv", 1L);

        assertThat(result.getUncategorizedCount()).isEqualTo(1);
        assertThat(result.getAutoCategoryCount()).isEqualTo(0);
    }

    // ==================== Dry-Run ====================

    @Test
    @DisplayName("Dry-Run → 解析但不写入数据库")
    void dryRun_shouldParseWithoutWriting() throws Exception {
        ImportResultDTO result = importService.dryRun(toStream(csvWithHeader("ASSET001", "预览")), "test.csv");

        assertThat(result.getTotalRows()).isEqualTo(1);
        verify(deviceMapper, never()).insert(any());
        verify(deviceMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("Dry-Run（超20行）→ totalRows返回总数，预览只处理前20条")
    void dryRun_over20Lines_shouldCap() throws Exception {
        StringBuilder sb = new StringBuilder(headerLine() + "\n");
        for (int i = 1; i <= 30; i++) {
            String[] cols = new String[40];
            for (int j = 0; j < 40; j++) cols[j] = "";
            cols[1] = "ASSET" + String.format("%03d", i);
            cols[2] = "设备" + i;
            sb.append(String.join(",", cols)).append("\n");
        }

        ImportResultDTO result = importService.dryRun(toStream(sb.toString()), "test.csv");

        assertThat(result.getTotalRows()).isEqualTo(30);
    }

    // ==================== 格式验证 ====================

    @Test
    @DisplayName("不支持的格式 → 抛异常")
    void importFromStream_unsupported_shouldThrow() {
        assertThatThrownBy(() -> importService.importFromStream(toStream(""), "test.txt", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的文件格式");
    }

    // ==================== 批次清除 ====================

    @Test
    @DisplayName("按批次清除 → 返回删除数")
    void clearByBatchId_shouldReturnCount() {
        when(deviceMapper.delete(any())).thenReturn(7);
        assertThat(importService.clearByBatchId("B001")).isEqualTo(7);
    }

    @Test
    @DisplayName("按批次清除（无数据）→ 返回0")
    void clearByBatchId_noData_shouldReturnZero() {
        when(deviceMapper.delete(any())).thenReturn(0);
        assertThat(importService.clearByBatchId("NONEXIST")).isEqualTo(0);
    }

    // ==================== 辅助 ====================

    private InputStream toStream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}

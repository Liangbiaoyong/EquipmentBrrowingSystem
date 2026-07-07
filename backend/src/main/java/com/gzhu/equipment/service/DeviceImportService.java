package com.gzhu.equipment.service;

import com.gzhu.equipment.dto.ImportResultDTO;

import java.io.InputStream;

/**
 * 设备批量导入服务 — CSV / XLSX
 */
public interface DeviceImportService {

    /**
     * 从输入流导入设备数据（实际写入数据库）
     */
    ImportResultDTO importFromStream(InputStream inputStream, String fileName, Long userId);

    /**
     * 从输入流预览导入（dry-run：解析+分类，不写入数据库）
     * 返回前20条预览数据 + 分类统计
     */
    ImportResultDTO dryRun(InputStream inputStream, String fileName);

    /**
     * 按批次号清除已导入的设备
     * @return 删除数量
     */
    int clearByBatchId(String batchId);
}

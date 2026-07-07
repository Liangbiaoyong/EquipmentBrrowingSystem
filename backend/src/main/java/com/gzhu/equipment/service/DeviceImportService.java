package com.gzhu.equipment.service;

import com.gzhu.equipment.dto.ImportResultDTO;

import java.io.InputStream;

/**
 * 设备批量导入服务 — CSV / XLSX
 */
public interface DeviceImportService {

    /**
     * 从输入流导入设备数据
     *
     * @param inputStream 文件输入流
     * @param fileName    文件名（用于判断格式：.csv / .xlsx）
     * @param userId      操作人ID
     * @return 导入结果摘要
     */
    ImportResultDTO importFromStream(InputStream inputStream, String fileName, Long userId);
}

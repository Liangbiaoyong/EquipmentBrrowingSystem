package com.gzhu.equipment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultDTO {

    /** 总行数 */
    private int totalRows;

    /** 成功导入数 */
    private int successCount;

    /** 更新数（资产编号已存在则更新） */
    private int updateCount;

    /** 跳过数 */
    private int skipCount;

    /** 失败数 */
    private int failCount;

    /** 删除数（旧数据中不在新数据里的记录） */
    private int deleteCount;

    /** 自动分类命中数 */
    private int autoCategoryCount;

    /** 未命中分类数（归入"其他设备"） */
    private int uncategorizedCount;

    /** 导入批次号 */
    private String batchId;

    /** 错误详情列表 */
    @Builder.Default
    private List<ImportError> errors = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ImportError {
        /** 行号 */
        private int row;
        /** 资产编号 */
        private String assetNo;
        /** 资产名称 */
        private String name;
        /** 错误原因 */
        private String reason;
    }

    public void addError(int row, String assetNo, String name, String reason) {
        this.errors.add(new ImportError(row, assetNo, name, reason));
    }
}

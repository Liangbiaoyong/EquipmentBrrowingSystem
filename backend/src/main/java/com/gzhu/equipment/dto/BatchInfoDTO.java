package com.gzhu.equipment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导入批次信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchInfoDTO {
    /** 批次号 */
    private String batchId;
    /** 导入时间 */
    private String createTime;
    /** 成功导入数 */
    private int successCount;
    /** 更新数 */
    private int updateCount;
    /** 失败数 */
    private int failCount;
    /** 总行数 */
    private int totalRows;
    /** 删除旧数据数 */
    private int deleteCount;
}

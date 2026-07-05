package com.gzhu.equipment.common;

import lombok.Data;

/**
 * 分页参数
 */
@Data
public class PageParam {
    private long page = 1;
    private long size = 10;
}

package com.gzhu.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备业务分类表 — 树形结构，支持多级分类
 */
@Data
@TableName("device_category")
public class DeviceCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String name;

    /** 分类编码 */
    private String code;

    /** 父分类ID（0=一级） */
    private Long parentId;

    /** 层级 */
    private Integer level;

    /** 排序 */
    private Integer sort;

    /** 1启用 0禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

package com.gzhu.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实验室表
 */
@Data
@TableName("laboratory")
public class Laboratory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 实验室名称 */
    private String name;

    /** 实验室编码 */
    private String code;

    /** 实验室描述 */
    private String description;

    /** 1启用 0禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

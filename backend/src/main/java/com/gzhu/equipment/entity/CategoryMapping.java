package com.gzhu.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 国标分类 → 业务分类 映射规则
 */
@Data
@TableName("category_mapping")
public class CategoryMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 国标分类名 */
    private String gbCategoryName;

    /** 匹配关键词 */
    private String keyword;

    /** 映射到的业务分类ID */
    private Long categoryId;

    /** 优先级（越小越优先） */
    private Integer priority;

    /** 1启用 0禁用 */
    private Integer isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

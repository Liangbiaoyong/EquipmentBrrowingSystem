package com.gzhu.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** V5: 分类描述元数据 — 目的分类和成果类型的说明 */
@Data
@TableName("category_description")
public class CategoryDescription implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类类型: PURPOSE / OUTCOME */
    private String categoryType;

    /** 分类名称 */
    private String categoryName;

    /** 分类说明描述 */
    private String description;

    /** 排序 */
    private Integer sort;

    /** 1启用 0禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

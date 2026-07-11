package com.gzhu.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实验室地点映射表
 */
@Data
@TableName("laboratory_room")
public class LaboratoryRoom implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联实验室ID */
    private Long laboratoryId;

    /** 房间/地点简称（如工程南501） */
    private String roomName;

    /** 完整存放地名称 */
    private String fullLocation;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

package com.gzhu.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("repair_record")
public class RepairRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private Long borrowId;
    private String faultDescription;
    private String status;       // PENDING / REPAIRING / FIXED
    private Long repairBy;
    private String repairComment;
    private LocalDateTime fixedTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

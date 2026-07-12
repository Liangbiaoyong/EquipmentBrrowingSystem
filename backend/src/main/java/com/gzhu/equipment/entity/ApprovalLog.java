package com.gzhu.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("approval_log")
public class ApprovalLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long borrowId;
    private Integer step;
    /** 审批人ID（可为空，待管理员手动分配）。INSERT时必须显式包含NULL值 */
    @TableField(insertStrategy = FieldStrategy.IGNORED)
    private Long approverId;

    /** PENDING / APPROVED / REJECTED */
    private String result;

    private String comment;
    private LocalDateTime operateTime;
}

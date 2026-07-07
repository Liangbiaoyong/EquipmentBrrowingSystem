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
    private Long approverId;

    /** PENDING / APPROVED / REJECTED */
    private String result;

    private String comment;
    private LocalDateTime operateTime;
}

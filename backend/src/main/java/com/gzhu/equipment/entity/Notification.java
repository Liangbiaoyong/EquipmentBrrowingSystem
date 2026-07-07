package com.gzhu.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String type;      // SYSTEM / APPROVAL / REMIND
    private Integer isRead;   // 0未读 1已读

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

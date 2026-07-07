package com.gzhu.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("attachment")
public class Attachment implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private Long bizId;
    private String fileUrl;
    private Long fileSize;
    private LocalDateTime uploadTime;
    private LocalDateTime expireTime;
}

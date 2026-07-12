package com.gzhu.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** V6: 逾期记录表 */
@Data
@TableName("overdue_record")
public class OverdueRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long borrowId;
    private Long deviceId;
    private Long userId;
    private Integer overdueDays;
    private BigDecimal fineAmount;
    /** UNPAID / PAID / WAIVED */
    private String fineStatus;
    /** PENDING / NOTIFIED / COLLECTED */
    private String collectionStatus;
    private Integer notifyCount;
    private LocalDateTime lastNotifyTime;
    private LocalDateTime adminCollectTime;
    private Long collectAdminId;
    private String collectRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

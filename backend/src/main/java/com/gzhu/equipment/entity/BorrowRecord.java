package com.gzhu.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("borrow_record")
public class BorrowRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long deviceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** 取走时间（实际借出开始，用户取设备时记录） */
    private LocalDateTime pickupTime;
    /** 取走/借用时的照片URL */
    private String pickupImage;

    /** PENDING_APPROVAL / APPROVED / REJECTED / BORROWING / RETURNED / OVERDUE / CANCELLED */
    private String status;

    private String reason;
    /** V4: 借用目的（申请时必填） */
    private String purpose;
    /** V4: 目的大类 */
    private String purposeCategory;
    /** V4.1: 目的子分类 */
    private String purposeSubcategory;
    private String approveFlowDef;  // JSON快照
    private Integer currentStep;
    private LocalDateTime realReturnTime;
    private Integer overdueDays;
    private String damageReport;
    /** V4: 借用成果（归还时可选填写/管理员后续补充） */
    private String outcome;
    /** V4: 成果录入人ID */
    private Long outcomeRecordedBy;
    /** V4: 成果录入时间 */
    private LocalDateTime outcomeRecordedTime;

    // ==================== 非数据库字段（查询时动态填充） ====================

    /** 设备名称（关联查询填充） */
    @TableField(exist = false)
    private String deviceName;

    /** 资产编号（关联查询填充） */
    @TableField(exist = false)
    private String deviceAssetNo;

    /** 用户名/借用人姓名（关联查询填充） */
    @TableField(exist = false)
    private String userName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

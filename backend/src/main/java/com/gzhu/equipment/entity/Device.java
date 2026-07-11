package com.gzhu.equipment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备/资产表 — 支持从学校资产系统批量导入
 */
@Data
@TableName("device")
public class Device implements Serializable {

    private static final long serialVersionUID = 1L;

    // === 核心业务字段 ===

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资产编号（学校统一编码，唯一） */
    private String assetNo;

    /** 设备/资产名称 */
    private String name;

    /** 型号/品牌 */
    private String model;

    /** 规格描述 */
    private String specs;

    /** 业务分类ID */
    private Long categoryId;

    /** 存放地名称 */
    private String location;

    /** 所属实验室ID */
    private Long laboratoryId;

    /** 使用单位 */
    private String department;

    /** 使用人姓名 */
    private String custodian;

    /** 默认审批人ID（初审人，LAB_ADMIN可修改） */
    private Long defaultApproverId;

    /** 数量 */
    private Integer totalQty;

    /** 当前可借数量 */
    private Integer availableQty;

    /** 单价(元) */
    private BigDecimal unitPrice;

    /** 金额(元) */
    private BigDecimal totalAmount;

    /** 1可借用 2借用中 3维修中 4待报废 */
    private Integer status;

    /** 借用类型: 1可现场借用 2可借出（默认） */
    private Integer borrowType;

    /** 设备描述/备注 */
    private String description;

    /** 封面图URL */
    private String coverImage;

    /** 创建人ID */
    private Long createBy;

    /** 实验室名称（非数据库字段，用于展示） */
    @TableField(exist = false)
    private String laboratoryName;

    // === 资产导入字段 ===

    /** 国标分类名 */
    private String gbCategoryName;

    /** 国标分类号 */
    private String gbCategoryCode;

    /** 教育分类名 */
    private String eduCategoryName;

    /** 教育分类码 */
    private String eduCategoryCode;

    /** 购置日期 */
    private LocalDate purchaseDate;

    /** 厂家/产地 */
    private String manufacturer;

    /** 供货商及电话 */
    private String supplier;

    /** 发票号 */
    private String invoiceNo;

    /** 合同号 */
    private String contractNo;

    /** 保修期限(月) */
    private Integer warrantyPeriod;

    /** 导入批次号 */
    private String importBatchId;

    // === 审计字段 ===

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

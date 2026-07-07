package com.gzhu.equipment.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ApprovalRequestDTO {
    @NotNull private Long borrowId;
    private String comment;  // 审批意见（通过可选，驳回必填）
    private Boolean approved; // true=通过 false=驳回
}

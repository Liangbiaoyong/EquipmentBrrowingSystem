package com.gzhu.equipment.dto;

import lombok.Data;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BorrowRequestDTO {
    /** 单设备借用（兼容旧接口） */
    private Long deviceId;
    /** 多设备借用（一次申请多台） */
    private List<Long> deviceIds;
    @NotNull private LocalDateTime startTime;
    @NotNull private LocalDateTime endTime;
    private String reason;
    /** V4: 借用目的（必填） */
    @NotEmpty(message = "请填写借用目的")
    private String purpose;
    /** V4: 目的分类 */
    private String purposeCategory;
    private Long approverId;
}

package com.gzhu.equipment.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
public class BorrowRequestDTO {
    @NotNull private Long deviceId;
    @NotNull private LocalDateTime startTime;
    @NotNull private LocalDateTime endTime;
    private String reason;
    private Long approverId; // 指定的审批人（教师）
}

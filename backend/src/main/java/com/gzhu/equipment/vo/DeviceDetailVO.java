package com.gzhu.equipment.vo;

import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.DeviceImage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 设备详情 — 含图片列表 + 当前借用状态 + 业务分类名
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDetailVO {

    /** 设备基本信息 */
    private Device device;

    /** 设备图片列表 */
    @Builder.Default
    private List<DeviceImage> images = List.of();

    /** 业务分类名称 */
    private String categoryName;

    /** 当前是否被借用中 */
    private boolean isBorrowing;

    /** 当前借用人的姓名（如果有） */
    private String currentBorrower;

    /** 预计归还时间（如果有） */
    private String expectedReturnTime;

    /** 该设备的历史借用次数 */
    private long borrowCount;

    /** 借用类型: 1可现场借用 2可借出 */
    private Integer borrowType;

    /** 所属实验室名称 */
    private String laboratoryName;
}

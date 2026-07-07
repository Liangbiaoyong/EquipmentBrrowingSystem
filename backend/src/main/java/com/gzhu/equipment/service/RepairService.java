package com.gzhu.equipment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gzhu.equipment.entity.RepairRecord;

public interface RepairService extends IService<RepairRecord> {
    /** 从损坏报告创建维修记录 */
    RepairRecord createFromDamage(Long deviceId, Long borrowId, String faultDescription);
    /** 开始维修 */
    void startRepair(Long id, Long repairBy);
    /** 修复完成 → 设备恢复正常 */
    void markFixed(Long id, String comment);
    /** 维修列表 */
    IPage<RepairRecord> list(int page, int size, String status);
}

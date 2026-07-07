package com.gzhu.equipment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gzhu.equipment.entity.Device;

import java.util.List;

/**
 * 设备管理服务
 */
public interface DeviceService extends IService<Device> {

    /** 分页查询（含多条件筛选） */
    IPage<Device> pageQuery(int page, int size,
                            String keyword, Long categoryId,
                            Integer status, String location,
                            String gbCategoryName);

    /** 按分类统计设备数量 */
    List<Long> countByCategory();

    /** 按资产编号查找 */
    Device getByAssetNo(String assetNo);

    /** 删除单个设备 */
    void deleteDevice(Long id);

    /** 按导入批次清除设备 */
    int deleteByBatchId(String batchId);

    /** 获取所有导入批次列表 */
    List<String> listBatches();

    /** 按批次查询设备（用于预览/导出） */
    List<Device> listByBatchId(String batchId);
}

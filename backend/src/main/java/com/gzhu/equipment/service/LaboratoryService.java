package com.gzhu.equipment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gzhu.equipment.entity.Laboratory;
import com.gzhu.equipment.entity.LaboratoryRoom;

import java.util.List;

/**
 * 实验室管理服务
 */
public interface LaboratoryService extends IService<Laboratory> {

    /** 分页查询实验室 */
    IPage<Laboratory> pageQuery(int page, int size, String keyword);

    /** 获取所有启用实验室（下拉用） */
    List<Laboratory> listEnabled();

    /** 获取实验室详情 */
    Laboratory getDetail(Long id);

    /** 分页查询地点映射 */
    IPage<LaboratoryRoom> pageRooms(int page, int size, Long laboratoryId, String roomName);

    /** 新增地点映射 */
    void addRoom(LaboratoryRoom room);

    /** 更新地点映射 */
    void updateRoom(Long id, LaboratoryRoom room);

    /** 删除地点映射 */
    void deleteRoom(Long id);

    /** 获取不重复的存放地列表（从 device 表抽取） */
    List<String> listDistinctLocations();

    /** 同步设备的地点→实验室映射，返回更新数量 */
    int syncDeviceLaboratories();

    /** 根据location自动匹配laboratoryId */
    Long matchLaboratoryId(String location);
}

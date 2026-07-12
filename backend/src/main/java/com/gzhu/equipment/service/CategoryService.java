package com.gzhu.equipment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gzhu.equipment.entity.CategoryMapping;
import com.gzhu.equipment.entity.DeviceCategory;

import java.util.List;

/**
 * 分类管理服务 — 业务分类 CRUD + 国标→业务自动映射
 */
public interface CategoryService extends IService<DeviceCategory> {

    // ==================== 业务分类 ====================

    /** 获取所有启用的分类（树形） */
    List<DeviceCategory> listEnabled();

    /** 获取一级分类列表 */
    List<DeviceCategory> listTopLevel();

    // ==================== 自动分类 ====================

    /**
     * 根据国标分类名自动匹配业务分类
     *
     * 算法：
     * 1. 提取关键特征词
     * 2. 遍历 category_mapping 规则表（按 priority 升序）
     * 3. 首次关键词包含匹配命中即返回对应 category_id
     * 4. 未命中返回 null（归入"其他设备"）
     *
     * @param gbCategoryName 国标分类名
     * @return 业务分类ID，未命中返回 null
     */
    Long classifyByGbName(String gbCategoryName);

    // ==================== 映射规则管理 ====================

    /** 获取所有映射规则 */
    List<CategoryMapping> listMappings();

    /** 按分类ID获取映射规则 */
    List<CategoryMapping> listMappingsByCategory(Long categoryId);

    /** 按分类ID + 关键词搜索映射规则 */
    List<CategoryMapping> listMappingsFiltered(Long categoryId, String keyword);

    /** 新增映射规则 */
    CategoryMapping addMapping(CategoryMapping mapping);

    /** 更新映射规则 */
    CategoryMapping updateMapping(CategoryMapping mapping);

    /** 删除映射规则 */
    void deleteMapping(Long id);

    /** 切换映射规则启用/禁用 */
    void toggleMapping(Long id);
}

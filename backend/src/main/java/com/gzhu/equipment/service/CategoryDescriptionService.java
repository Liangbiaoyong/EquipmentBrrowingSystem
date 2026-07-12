package com.gzhu.equipment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gzhu.equipment.entity.CategoryDescription;

import java.util.List;
import java.util.Map;

/** V5: 分类描述元数据服务 */
public interface CategoryDescriptionService extends IService<CategoryDescription> {

    /** 按类型获取所有启用描述 (PURPOSE/OUTCOME) */
    List<CategoryDescription> listByType(String categoryType);

    /** 按类型+名称获取描述 */
    CategoryDescription getByTypeAndName(String categoryType, String categoryName);

    /** 获取所有启用描述，按类型分组 */
    Map<String, List<CategoryDescription>> listAllGrouped();
}

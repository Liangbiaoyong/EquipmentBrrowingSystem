package com.gzhu.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gzhu.equipment.entity.CategoryMapping;
import com.gzhu.equipment.entity.DeviceCategory;
import com.gzhu.equipment.mapper.CategoryMappingMapper;
import com.gzhu.equipment.mapper.DeviceCategoryMapper;
import com.gzhu.equipment.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分类管理服务实现
 *
 * 自动分类算法：
 * 1. 预处理国标名：去掉前缀"其他"（如"其他计算机"→"计算机"）
 * 2. 按 priority 升序遍历规则表
 * 3. 用 String.contains 做关键词匹配
 * 4. 首条命中即返回
 * 5. 未命中返回 null（前端/导入服务归为"其他设备" category_id=10）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<DeviceCategoryMapper, DeviceCategory>
        implements CategoryService {

    private final DeviceCategoryMapper deviceCategoryMapper;
    private final CategoryMappingMapper categoryMappingMapper;

    // ==================== 业务分类 ====================

    @Override
    public List<DeviceCategory> listEnabled() {
        return deviceCategoryMapper.selectList(
                new LambdaQueryWrapper<DeviceCategory>()
                        .eq(DeviceCategory::getStatus, 1)
                        .orderByAsc(DeviceCategory::getSort));
    }

    @Override
    public List<DeviceCategory> listTopLevel() {
        return deviceCategoryMapper.selectList(
                new LambdaQueryWrapper<DeviceCategory>()
                        .eq(DeviceCategory::getStatus, 1)
                        .eq(DeviceCategory::getParentId, 0L)
                        .orderByAsc(DeviceCategory::getSort));
    }

    // ==================== 自动分类 ====================

    @Override
    public Long classifyByGbName(String gbCategoryName) {
        if (gbCategoryName == null || gbCategoryName.trim().isEmpty()) {
            return null;
        }

        String original = gbCategoryName.trim();
        // 预处理：去掉前缀"其他"方便关键词匹配
        String normalized = original.startsWith("其他") ? original.substring(2).trim() : original;

        // 按优先级升序获取所有启用规则
        List<CategoryMapping> mappings = categoryMappingMapper.selectList(
                new LambdaQueryWrapper<CategoryMapping>()
                        .eq(CategoryMapping::getIsActive, 1)
                        .orderByAsc(CategoryMapping::getPriority));

        // 遍历规则，首次命中返回
        for (CategoryMapping mapping : mappings) {
            String keyword = mapping.getKeyword();
            if (original.contains(keyword) || normalized.contains(keyword)) {
                log.debug("分类匹配成功: gbName='{}' → keyword='{}' → categoryId={}",
                        original, keyword, mapping.getCategoryId());
                return mapping.getCategoryId();
            }
        }

        // 未命中 → 调用方归入"其他设备"
        log.debug("分类匹配未命中: gbName='{}'", original);
        return null;
    }

    // ==================== 映射规则管理 ====================

    @Override
    public List<CategoryMapping> listMappings() {
        return categoryMappingMapper.selectList(
                new LambdaQueryWrapper<CategoryMapping>()
                        .orderByAsc(CategoryMapping::getPriority)
                        .orderByAsc(CategoryMapping::getCategoryId));
    }

    @Override
    public List<CategoryMapping> listMappingsByCategory(Long categoryId) {
        return categoryMappingMapper.selectList(
                new LambdaQueryWrapper<CategoryMapping>()
                        .eq(CategoryMapping::getCategoryId, categoryId)
                        .orderByAsc(CategoryMapping::getPriority));
    }

    @Override
    public List<CategoryMapping> listMappingsFiltered(Long categoryId, String keyword) {
        LambdaQueryWrapper<CategoryMapping> w = new LambdaQueryWrapper<>();
        if (categoryId != null) w.eq(CategoryMapping::getCategoryId, categoryId);
        if (keyword != null && !keyword.trim().isEmpty()) {
            w.and(wp -> wp.like(CategoryMapping::getGbCategoryName, keyword)
                    .or().like(CategoryMapping::getKeyword, keyword));
        }
        w.orderByAsc(CategoryMapping::getPriority);
        return categoryMappingMapper.selectList(w);
    }

    @Override
    @Transactional
    public CategoryMapping addMapping(CategoryMapping mapping) {
        categoryMappingMapper.insert(mapping);
        log.info("新增分类映射规则: gbName='{}' keyword='{}' → cateId={}",
                mapping.getGbCategoryName(), mapping.getKeyword(), mapping.getCategoryId());
        return mapping;
    }

    @Override
    @Transactional
    public CategoryMapping updateMapping(CategoryMapping mapping) {
        categoryMappingMapper.updateById(mapping);
        log.info("更新分类映射规则: id={}", mapping.getId());
        return mapping;
    }

    @Override
    @Transactional
    public void deleteMapping(Long id) {
        categoryMappingMapper.deleteById(id);
        log.info("删除分类映射规则: id={}", id);
    }

    @Override
    @Transactional
    public void toggleMapping(Long id) {
        CategoryMapping mapping = categoryMappingMapper.selectById(id);
        if (mapping != null) {
            mapping.setIsActive(mapping.getIsActive() == 1 ? 0 : 1);
            categoryMappingMapper.updateById(mapping);
            log.info("切换映射规则状态: id={} active={}", id, mapping.getIsActive());
        }
    }
}

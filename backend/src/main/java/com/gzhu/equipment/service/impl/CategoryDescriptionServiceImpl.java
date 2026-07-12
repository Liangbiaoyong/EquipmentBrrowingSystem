package com.gzhu.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gzhu.equipment.entity.CategoryDescription;
import com.gzhu.equipment.mapper.CategoryDescriptionMapper;
import com.gzhu.equipment.service.CategoryDescriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** V5: 分类描述元数据服务实现 */
@Slf4j
@Service
public class CategoryDescriptionServiceImpl
        extends ServiceImpl<CategoryDescriptionMapper, CategoryDescription>
        implements CategoryDescriptionService {

    @Override
    public List<CategoryDescription> listByType(String categoryType) {
        return baseMapper.selectList(
                new LambdaQueryWrapper<CategoryDescription>()
                        .eq(CategoryDescription::getCategoryType, categoryType)
                        .eq(CategoryDescription::getStatus, 1)
                        .orderByAsc(CategoryDescription::getSort));
    }

    @Override
    public CategoryDescription getByTypeAndName(String categoryType, String categoryName) {
        return baseMapper.selectOne(
                new LambdaQueryWrapper<CategoryDescription>()
                        .eq(CategoryDescription::getCategoryType, categoryType)
                        .eq(CategoryDescription::getCategoryName, categoryName)
                        .eq(CategoryDescription::getStatus, 1)
                        .last("LIMIT 1"));
    }

    @Override
    public Map<String, List<CategoryDescription>> listAllGrouped() {
        List<CategoryDescription> all = baseMapper.selectList(
                new LambdaQueryWrapper<CategoryDescription>()
                        .eq(CategoryDescription::getStatus, 1)
                        .orderByAsc(CategoryDescription::getCategoryType)
                        .orderByAsc(CategoryDescription::getSort));
        return all.stream().collect(Collectors.groupingBy(
                CategoryDescription::getCategoryType,
                LinkedHashMap::new,
                Collectors.toList()));
    }
}

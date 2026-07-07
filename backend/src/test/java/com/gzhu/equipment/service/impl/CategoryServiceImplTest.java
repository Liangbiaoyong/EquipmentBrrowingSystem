package com.gzhu.equipment.service.impl;

import com.gzhu.equipment.entity.CategoryMapping;
import com.gzhu.equipment.entity.DeviceCategory;
import com.gzhu.equipment.mapper.CategoryMappingMapper;
import com.gzhu.equipment.mapper.DeviceCategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CategoryServiceImpl 单元测试
 *
 * 覆盖：自动分类算法、分类查询、映射规则管理
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private DeviceCategoryMapper deviceCategoryMapper;

    @Mock
    private CategoryMappingMapper categoryMappingMapper;

    private CategoryServiceImpl categoryService;

    private List<CategoryMapping> defaultMappings;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryServiceImpl(deviceCategoryMapper, categoryMappingMapper);

        // 默认映射规则：按 priority 升序
        defaultMappings = Arrays.asList(
                createMapping(1L, "照相机", 2L, 100),
                createMapping(2L, "摄像机", 2L, 100),
                createMapping(3L, "无人机", 7L, 50),   // 高优先级
                createMapping(4L, "计算机", 1L, 100),
                createMapping(5L, "服务器", 1L, 100),
                createMapping(6L, "仪器", 5L, 100),
                createMapping(7L, "空调", 4L, 100),
                createMapping(8L, "舞台", 10L, 200),   // 低优先级
                createMapping(9L, "软件", 8L, 50)      // 高优先级
        );
    }

    // ==================== 自动分类算法 ====================

    @Test
    @DisplayName("国标名精确命中 → 返回对应categoryId")
    void classifyByGbName_exactMatch_shouldReturnCategoryId() {
        when(categoryMappingMapper.selectList(any())).thenReturn(defaultMappings);

        Long result = categoryService.classifyByGbName("台式计算机");

        assertThat(result).isEqualTo(1L); // 匹配"计算机"
    }

    @Test
    @DisplayName("国标名含「其他」前缀 → 去前缀后匹配")
    void classifyByGbName_withOtherPrefix_shouldStripAndMatch() {
        when(categoryMappingMapper.selectList(any())).thenReturn(defaultMappings);

        Long result = categoryService.classifyByGbName("其他照相机及器材");

        assertThat(result).isEqualTo(2L); // 去其他前缀后匹配
    }

    @Test
    @DisplayName("高优先级关键词优先匹配(无人机优先于仪器)")
    void classifyByGbName_highPriority_shouldMatchFirst() {
        when(categoryMappingMapper.selectList(any())).thenReturn(defaultMappings);

        Long result = categoryService.classifyByGbName("无人机航拍器");

        assertThat(result).isEqualTo(7L); // priority=50 匹配"无人机"
    }

    @Test
    @DisplayName("未命中任何规则 → 返回null")
    void classifyByGbName_noMatch_shouldReturnNull() {
        when(categoryMappingMapper.selectList(any())).thenReturn(defaultMappings);

        Long result = categoryService.classifyByGbName("图书资料");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("国标名为空 → 返回null")
    void classifyByGbName_emptyInput_shouldReturnNull() {
        assertThat(categoryService.classifyByGbName(null)).isNull();
        assertThat(categoryService.classifyByGbName("")).isNull();
        assertThat(categoryService.classifyByGbName("   ")).isNull();
    }

    @Test
    @DisplayName("完整国标名中的关键词包含匹配(非前缀)")
    void classifyByGbName_containsMatch_shouldWork() {
        when(categoryMappingMapper.selectList(any())).thenReturn(defaultMappings);

        // "舞台灯光设备" 包含 "舞台" → categoryId=10
        Long result = categoryService.classifyByGbName("舞台灯光设备");
        assertThat(result).isEqualTo(10L);
    }

    @Test
    @DisplayName("仅[其他]前缀无剩余关键词 → 空字符串匹配失败")
    void classifyByGbName_onlyOtherPrefix_shouldReturnNull() {
        when(categoryMappingMapper.selectList(any())).thenReturn(defaultMappings);

        Long result = categoryService.classifyByGbName("其他");
        assertThat(result).isNull();
    }

    // ==================== 分类查询 ====================

    @Test
    @DisplayName("listEnabled → 返回启用的分类列表")
    void listEnabled_shouldReturnEnabledCategories() {
        List<DeviceCategory> mockList = List.of(new DeviceCategory());
        when(deviceCategoryMapper.selectList(any())).thenReturn(mockList);

        List<DeviceCategory> result = categoryService.listEnabled();

        assertThat(result).hasSize(1);
        verify(deviceCategoryMapper).selectList(any());
    }

    @Test
    @DisplayName("listTopLevel → 返回一级分类")
    void listTopLevel_shouldReturnTopLevel() {
        List<DeviceCategory> mockList = List.of(new DeviceCategory(), new DeviceCategory());
        when(deviceCategoryMapper.selectList(any())).thenReturn(mockList);

        List<DeviceCategory> result = categoryService.listTopLevel();

        assertThat(result).hasSize(2);
        verify(deviceCategoryMapper).selectList(any());
    }

    // ==================== 映射规则管理 ====================

    @Test
    @DisplayName("listMappings → 返回所有规则（按priority+categoryId排序）")
    void listMappings_shouldReturnAllSorted() {
        when(categoryMappingMapper.selectList(any())).thenReturn(defaultMappings);

        List<CategoryMapping> result = categoryService.listMappings();

        assertThat(result).hasSize(9);
        verify(categoryMappingMapper).selectList(any());
    }

    @Test
    @DisplayName("addMapping → 插入并返回")
    void addMapping_shouldInsertAndReturn() {
        CategoryMapping mapping = createMapping(null, "测试仪", 5L, 100);

        CategoryMapping result = categoryService.addMapping(mapping);

        assertThat(result).isSameAs(mapping);
        verify(categoryMappingMapper).insert(mapping);
    }

    @Test
    @DisplayName("updateMapping → 更新并返回")
    void updateMapping_shouldUpdateAndReturn() {
        CategoryMapping mapping = createMapping(1L, "照相机", 2L, 100);

        CategoryMapping result = categoryService.updateMapping(mapping);

        assertThat(result).isSameAs(mapping);
        verify(categoryMappingMapper).updateById(mapping);
    }

    @Test
    @DisplayName("deleteMapping → 删除")
    void deleteMapping_shouldDelete() {
        categoryService.deleteMapping(1L);

        verify(categoryMappingMapper).deleteById(1L);
    }

    @Test
    @DisplayName("toggleMapping(启用→禁用) → 切换状态")
    void toggleMapping_enabledToDisabled_shouldToggle() {
        CategoryMapping mapping = createMapping(1L, "照相机", 2L, 100);
        mapping.setIsActive(1);
        when(categoryMappingMapper.selectById(1L)).thenReturn(mapping);

        categoryService.toggleMapping(1L);

        assertThat(mapping.getIsActive()).isEqualTo(0);
        verify(categoryMappingMapper).updateById(mapping);
    }

    @Test
    @DisplayName("toggleMapping(禁用→启用) → 切换状态")
    void toggleMapping_disabledToEnabled_shouldToggle() {
        CategoryMapping mapping = createMapping(1L, "照相机", 2L, 100);
        mapping.setIsActive(0);
        when(categoryMappingMapper.selectById(1L)).thenReturn(mapping);

        categoryService.toggleMapping(1L);

        assertThat(mapping.getIsActive()).isEqualTo(1);
        verify(categoryMappingMapper).updateById(mapping);
    }

    @Test
    @DisplayName("toggleMapping(不存在的id) → 不做任何操作")
    void toggleMapping_notFound_shouldDoNothing() {
        when(categoryMappingMapper.selectById(999L)).thenReturn(null);

        categoryService.toggleMapping(999L);

        verify(categoryMappingMapper, never()).updateById(any());
    }

    // ==================== 辅助方法 ====================

    private CategoryMapping createMapping(Long id, String keyword, Long categoryId, int priority) {
        CategoryMapping m = new CategoryMapping();
        m.setId(id);
        m.setGbCategoryName("测试-" + keyword);
        m.setKeyword(keyword);
        m.setCategoryId(categoryId);
        m.setPriority(priority);
        m.setIsActive(1);
        return m;
    }
}

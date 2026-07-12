package com.gzhu.equipment.service.impl;

import com.gzhu.equipment.entity.CategoryDescription;
import com.gzhu.equipment.mapper.CategoryDescriptionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryDescriptionServiceImplTest {

    @Mock
    private CategoryDescriptionMapper mapper;

    private CategoryDescriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoryDescriptionServiceImpl();
        // 通过 reflection 注入 mock mapper
        try {
            var field = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                    .getDeclaredField("baseMapper");
            field.setAccessible(true);
            field.set(service, mapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("listByType → 按类型获取列表")
    void listByType_shouldReturnList() {
        CategoryDescription cd = new CategoryDescription();
        cd.setId(1L);
        cd.setCategoryType("PURPOSE");
        cd.setCategoryName("教学与培养");
        when(mapper.selectList(any())).thenReturn(List.of(cd));

        List<CategoryDescription> result = service.listByType("PURPOSE");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategoryName()).isEqualTo("教学与培养");
    }

    @Test
    @DisplayName("listByType（空）→ 返回空列表")
    void listByType_empty_shouldReturnEmpty() {
        when(mapper.selectList(any())).thenReturn(List.of());
        assertThat(service.listByType("OUTCOME")).isEmpty();
    }

    @Test
    @DisplayName("getByTypeAndName → 精确查找")
    void getByTypeAndName_shouldReturnMatch() {
        CategoryDescription cd = new CategoryDescription();
        cd.setId(1L);
        cd.setCategoryType("PURPOSE");
        cd.setCategoryName("科学研究");
        when(mapper.selectOne(any())).thenReturn(cd);

        CategoryDescription result = service.getByTypeAndName("PURPOSE", "科学研究");
        assertThat(result).isNotNull();
        assertThat(result.getCategoryName()).isEqualTo("科学研究");
    }

    @Test
    @DisplayName("getByTypeAndName（不存在）→ 返回null")
    void getByTypeAndName_notFound_shouldReturnNull() {
        when(mapper.selectOne(any())).thenReturn(null);
        assertThat(service.getByTypeAndName("PURPOSE", "不存在")).isNull();
    }

    @Test
    @DisplayName("listAllGrouped → 按类型分组")
    void listAllGrouped_shouldGroupByType() {
        CategoryDescription c1 = new CategoryDescription();
        c1.setId(1L);
        c1.setCategoryType("PURPOSE");
        c1.setCategoryName("教学");

        CategoryDescription c2 = new CategoryDescription();
        c2.setId(2L);
        c2.setCategoryType("OUTCOME");
        c2.setCategoryName("论文");

        when(mapper.selectList(any())).thenReturn(List.of(c1, c2));

        Map<String, List<CategoryDescription>> result = service.listAllGrouped();
        assertThat(result).containsKeys("PURPOSE", "OUTCOME");
        assertThat(result.get("PURPOSE")).hasSize(1);
        assertThat(result.get("OUTCOME")).hasSize(1);
    }

    @Test
    @DisplayName("listAllGrouped（空数据）→ 返回空Map")
    void listAllGrouped_empty_shouldReturnEmptyMap() {
        when(mapper.selectList(any())).thenReturn(List.of());
        assertThat(service.listAllGrouped()).isEmpty();
    }
}

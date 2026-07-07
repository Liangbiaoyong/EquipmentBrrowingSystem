package com.gzhu.equipment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzhu.equipment.entity.CategoryMapping;
import com.gzhu.equipment.entity.DeviceCategory;
import com.gzhu.equipment.security.JwtTokenProvider;
import com.gzhu.equipment.service.CategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CategoryController REST 接口测试
 *
 * 覆盖：分类查询、自动分类测试、映射规则管理
 */
@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("GET /categories → 返回分类列表")
    void listCategories_shouldReturnList() throws Exception {
        when(categoryService.listEnabled()).thenReturn(List.of(new DeviceCategory()));

        mockMvc.perform(get("/categories"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /categories/top-level → 返回一级分类")
    void listTopLevel_shouldReturnList() throws Exception {
        when(categoryService.listTopLevel()).thenReturn(List.of(new DeviceCategory()));

        mockMvc.perform(get("/categories/top-level"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /categories/classify?gbName=xxx → 匹配成功返回分类信息")
    void classify_match_shouldReturnCategory() throws Exception {
        when(categoryService.classifyByGbName("计算机")).thenReturn(1L);
        DeviceCategory category = new DeviceCategory();
        category.setId(1L);
        category.setName("计算机及外设");
        when(categoryService.getById(1L)).thenReturn(category);

        mockMvc.perform(get("/categories/classify").param("gbName", "计算机"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("匹配成功: 计算机及外设"));
    }

    @Test
    @DisplayName("GET /categories/classify?gbName=xxx → 未匹配返回兜底信息")
    void classify_noMatch_shouldReturnFallback() throws Exception {
        when(categoryService.classifyByGbName("未知分类")).thenReturn(null);

        mockMvc.perform(get("/categories/classify").param("gbName", "未知分类"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("未匹配，将归入「其他设备」"));
    }

    @Test
    @DisplayName("GET /categories/mappings → 返回映射规则列表")
    void listMappings_shouldReturnList() throws Exception {
        when(categoryService.listMappings()).thenReturn(List.of(new CategoryMapping()));

        mockMvc.perform(get("/categories/mappings"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /categories/mappings?categoryId=1 → 按分类过滤")
    void listMappings_byCategory_shouldFilter() throws Exception {
        when(categoryService.listMappingsByCategory(1L)).thenReturn(List.of());

        mockMvc.perform(get("/categories/mappings").param("categoryId", "1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /categories/mappings → 新增规则")
    void addMapping_shouldReturnCreated() throws Exception {
        CategoryMapping mapping = new CategoryMapping();
        mapping.setKeyword("测试仪");
        mapping.setCategoryId(5L);
        mapping.setPriority(100);
        when(categoryService.addMapping(any())).thenReturn(mapping);

        mockMvc.perform(post("/categories/mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapping)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("DELETE /categories/mappings/1 → 删除规则")
    void deleteMapping_shouldSucceed() throws Exception {
        mockMvc.perform(delete("/categories/mappings/1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("PUT /categories/mappings/1/toggle → 切换启用/禁用")
    void toggleMapping_shouldSucceed() throws Exception {
        mockMvc.perform(put("/categories/mappings/1/toggle"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}

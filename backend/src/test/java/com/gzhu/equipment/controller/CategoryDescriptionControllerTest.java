package com.gzhu.equipment.controller;

import com.gzhu.equipment.entity.CategoryDescription;
import com.gzhu.equipment.security.JwtTokenProvider;
import com.gzhu.equipment.security.LoginRateLimiter;
import com.gzhu.equipment.security.TokenBlacklist;
import com.gzhu.equipment.service.CategoryDescriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryDescriptionController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryDescriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryDescriptionService descriptionService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private LoginRateLimiter loginRateLimiter;

    @MockBean
    private TokenBlacklist tokenBlacklist;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new com.gzhu.equipment.security.JwtUserPrincipal(
                                1L, "student01", 0,
                                List.of("ROLE_STUDENT"),
                                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
    }

    @Test
    @DisplayName("GET /category-descriptions?type=PURPOSE → 返回列表")
    void listByType_shouldReturnList() throws Exception {
        CategoryDescription cd = new CategoryDescription();
        cd.setId(1L);
        cd.setCategoryType("PURPOSE");
        cd.setCategoryName("教学与培养");
        when(descriptionService.listByType("PURPOSE")).thenReturn(List.of(cd));

        mockMvc.perform(get("/category-descriptions").param("type", "PURPOSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].categoryName").value("教学与培养"));
    }

    @Test
    @DisplayName("GET /category-descriptions/grouped → 返回分组数据")
    void listGrouped_shouldReturnGrouped() throws Exception {
        Map<String, List<CategoryDescription>> grouped = new LinkedHashMap<>();
        grouped.put("PURPOSE", List.of());
        grouped.put("OUTCOME", List.of());
        when(descriptionService.listAllGrouped()).thenReturn(grouped);

        mockMvc.perform(get("/category-descriptions/grouped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.PURPOSE").isArray());
    }

    @Test
    @DisplayName("GET /category-descriptions/lookup?type=PURPOSE&name=教学 → 返回匹配")
    void lookup_shouldReturnMatch() throws Exception {
        CategoryDescription cd = new CategoryDescription();
        cd.setId(1L);
        cd.setCategoryName("教学");
        when(descriptionService.getByTypeAndName("PURPOSE", "教学")).thenReturn(cd);

        mockMvc.perform(get("/category-descriptions/lookup")
                        .param("type", "PURPOSE")
                        .param("name", "教学"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /category-descriptions/lookup（不存在）→ 返回404")
    void lookup_notFound_shouldReturn404() throws Exception {
        when(descriptionService.getByTypeAndName("PURPOSE", "不存在")).thenReturn(null);

        mockMvc.perform(get("/category-descriptions/lookup")
                        .param("type", "PURPOSE")
                        .param("name", "不存在"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }
}

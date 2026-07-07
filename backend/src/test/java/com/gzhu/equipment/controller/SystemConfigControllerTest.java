package com.gzhu.equipment.controller;

import com.gzhu.equipment.security.JwtTokenProvider;
import com.gzhu.equipment.security.JwtUserPrincipal;
import com.gzhu.equipment.service.SystemConfigService;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SystemConfigController REST 接口测试
 */
@WebMvcTest(SystemConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
class SystemConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SystemConfigService configService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtUserPrincipal principal = new JwtUserPrincipal(
                1L, "admin", 3,
                List.of("ROLE_SYSTEM_ADMIN"),
                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("GET /admin/config → 配置列表")
    void listConfig_shouldReturnList() throws Exception {
        when(configService.listAll()).thenReturn(List.of(
                new com.gzhu.equipment.entity.SystemConfig()));

        mockMvc.perform(get("/admin/config"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /admin/config/borrow.max_days → 获取单个配置")
    void getValue_shouldReturnValue() throws Exception {
        when(configService.getValue("borrow.max_days", null)).thenReturn("14");

        mockMvc.perform(get("/admin/config/borrow.max_days"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /admin/config/nonexist → 配置不存在")
    void getValue_notFound_shouldReturn404() throws Exception {
        when(configService.getValue("nonexist", null)).thenReturn(null);

        mockMvc.perform(get("/admin/config/nonexist"))
                .andDo(print())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("PUT /admin/config/borrow.max_days → 更新配置")
    void setValue_shouldSucceed() throws Exception {
        mockMvc.perform(put("/admin/config/borrow.max_days")
                        .param("value", "14"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("DELETE /admin/config/borrow.max_days → 删除配置")
    void deleteValue_shouldSucceed() throws Exception {
        mockMvc.perform(delete("/admin/config/borrow.max_days"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}

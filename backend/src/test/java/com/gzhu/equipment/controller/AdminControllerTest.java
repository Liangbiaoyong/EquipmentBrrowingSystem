package com.gzhu.equipment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzhu.equipment.entity.SysUser;
import com.gzhu.equipment.mapper.SysUserMapper;
import com.gzhu.equipment.security.JwtTokenProvider;
import com.gzhu.equipment.security.JwtUserPrincipal;
import com.gzhu.equipment.service.SysUserService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminController REST 接口测试
 */
@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SysUserMapper sysUserMapper;

    @MockBean
    private SysUserService sysUserService;

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
    @DisplayName("GET /admin/users → 用户列表")
    void listUsers_shouldReturnPage() throws Exception {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysUser> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>();
        when(sysUserMapper.selectPage(any(), any())).thenReturn(page);

        mockMvc.perform(get("/admin/users"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /admin/users → 创建本地用户")
    void createUser_shouldSucceed() throws Exception {
        SysUser user = new SysUser();
        user.setUsername("newuser");
        when(sysUserService.createLocalUser(anyString(), anyString(), anyInt(), any(), any(), any(), anyString()))
                .thenReturn(user);

        mockMvc.perform(post("/admin/users")
                        .param("username", "newuser")
                        .param("realName", "新用户")
                        .param("userType", "2")
                        .param("password", "password123"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("PUT /admin/users/1/role → 修改角色")
    void updateRole_shouldSucceed() throws Exception {
        SysUser user = new SysUser();
        user.setId(1L);
        when(sysUserMapper.selectById(1L)).thenReturn(user);

        mockMvc.perform(put("/admin/users/1/role")
                        .param("userType", "2"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("PUT /admin/users/1/status → 启禁用")
    void toggleStatus_shouldSucceed() throws Exception {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setStatus(1);
        when(sysUserMapper.selectById(1L)).thenReturn(user);

        mockMvc.perform(put("/admin/users/1/status"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("PUT /admin/users/999/role → 用户不存在")
    void updateRole_notFound_shouldReturn404() throws Exception {
        when(sysUserMapper.selectById(999L)).thenReturn(null);

        mockMvc.perform(put("/admin/users/999/role")
                        .param("userType", "2"))
                .andDo(print())
                .andExpect(jsonPath("$.code").value(404));
    }
}

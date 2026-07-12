package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.RepairRecord;
import com.gzhu.equipment.security.JwtTokenProvider;
import com.gzhu.equipment.security.LoginRateLimiter;
import com.gzhu.equipment.security.TokenBlacklist;
import com.gzhu.equipment.service.RepairService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RepairController.class)
@AutoConfigureMockMvc(addFilters = false)
class RepairControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RepairService repairService;

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
                                1L, "admin", 3,
                                List.of("ROLE_SYSTEM_ADMIN"),
                                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))));
    }

    @Test
    @DisplayName("GET /repairs → 维修记录列表")
    void list_shouldReturnPage() throws Exception {
        when(repairService.list(anyInt(), anyInt(), any())).thenReturn(new Page<>());
        mockMvc.perform(get("/repairs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /repairs?status=REPAIRING → 按状态筛选")
    void list_withStatus_shouldFilter() throws Exception {
        when(repairService.list(anyInt(), anyInt(), eq("REPAIRING"))).thenReturn(new Page<>());
        mockMvc.perform(get("/repairs").param("status", "REPAIRING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /repairs/devices → 维修设备列表")
    void listDevices_shouldReturnPage() throws Exception {
        when(repairService.listRepairDevices(anyInt(), anyInt(), any())).thenReturn(new Page<Device>());
        mockMvc.perform(get("/repairs/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /repairs → 创建维修记录")
    void create_shouldReturnRecord() throws Exception {
        RepairRecord r = new RepairRecord();
        r.setId(1L);
        r.setStatus("PENDING");
        when(repairService.createFromDamage(anyLong(), any(), anyString())).thenReturn(r);
        mockMvc.perform(post("/repairs")
                        .param("deviceId", "1")
                        .param("borrowId", "100")
                        .param("faultDescription", "屏幕碎裂"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("PUT /repairs/{id}/start → 开始维修")
    void startRepair_shouldSucceed() throws Exception {
        doNothing().when(repairService).startRepair(anyLong(), anyLong());
        mockMvc.perform(put("/repairs/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("PUT /repairs/{id}/fix → 修复完成")
    void markFixed_shouldSucceed() throws Exception {
        doNothing().when(repairService).markFixed(anyLong(), anyString());
        mockMvc.perform(put("/repairs/1/fix").param("comment", "已修好"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("PUT /repairs/{id}/scrap → 标记待报废")
    void markScrap_shouldSucceed() throws Exception {
        doNothing().when(repairService).markScrap(anyLong(), anyString());
        mockMvc.perform(put("/repairs/1/scrap").param("comment", "无法维修"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /repairs/confirm-scrap → 确认报废")
    void confirmScrap_shouldSucceed() throws Exception {
        doNothing().when(repairService).confirmScrap(anyLong(), anyString());
        mockMvc.perform(post("/repairs/confirm-scrap")
                        .param("deviceId", "1")
                        .param("comment", "已报废"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}

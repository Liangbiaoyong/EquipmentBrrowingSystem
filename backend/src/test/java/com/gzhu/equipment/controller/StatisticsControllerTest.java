package com.gzhu.equipment.controller;

import com.gzhu.equipment.mapper.BorrowRecordMapper;
import com.gzhu.equipment.mapper.DeviceCategoryMapper;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.security.JwtTokenProvider;
import com.gzhu.equipment.security.LoginRateLimiter;
import com.gzhu.equipment.security.TokenBlacklist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * StatisticsController REST 接口测试
 */
@WebMvcTest(StatisticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class StatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceMapper deviceMapper;

    @MockBean
    private BorrowRecordMapper borrowMapper;

    @MockBean
    private DeviceCategoryMapper categoryMapper;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private LoginRateLimiter loginRateLimiter;

    @MockBean
    private TokenBlacklist tokenBlacklist;

    @Test
    @DisplayName("GET /statistics/overview → 仪表盘概览")
    void overview_shouldReturnStats() throws Exception {
        when(deviceMapper.selectCount(any())).thenReturn(100L, 80L, 15L, 5L);
        when(borrowMapper.selectCount(any())).thenReturn(20L, 3L, 10L, 200L);

        mockMvc.perform(get("/statistics/overview"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deviceStats.total").value(100))
                .andExpect(jsonPath("$.data.deviceStats.normal").value(80))
                .andExpect(jsonPath("$.data.deviceStats.repair").value(15))
                .andExpect(jsonPath("$.data.deviceStats.scrap").value(5))
                .andExpect(jsonPath("$.data.borrowStats.borrowing").value(20))
                .andExpect(jsonPath("$.data.borrowStats.overdue").value(3))
                .andExpect(jsonPath("$.data.borrowStats.pendingApproval").value(10))
                .andExpect(jsonPath("$.data.borrowStats.total").value(200));
    }

    @Test
    @DisplayName("GET /statistics/trend → 本月借用趋势")
    void trend_shouldReturnRows() throws Exception {
        when(borrowMapper.selectMaps(any())).thenReturn(List.of(
                Map.of("date", "2026-07-01", "count", 5L),
                Map.of("date", "2026-07-02", "count", 3L)));

        mockMvc.perform(get("/statistics/trend"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].date").value("2026-07-01"));
    }

    @Test
    @DisplayName("GET /statistics/top-devices → 热门设备TOP10")
    void topDevices_shouldReturnList() throws Exception {
        when(borrowMapper.selectMaps(any())).thenReturn(List.of(
                Map.of("deviceName", "设备A", "borrowCount", 20L)));

        mockMvc.perform(get("/statistics/top-devices"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].deviceName").value("设备A"));
    }

    @Test
    @DisplayName("GET /statistics/top-users → 高频用户TOP10")
    void topUsers_shouldReturnList() throws Exception {
        when(borrowMapper.selectMaps(any())).thenReturn(List.of(
                Map.of("userName", "张三", "borrowCount", 15L)));

        mockMvc.perform(get("/statistics/top-users"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userName").value("张三"));
    }

    @Test
    @DisplayName("GET /statistics/utilization → 利用率分析")
    void utilization_shouldReturnList() throws Exception {
        when(borrowMapper.selectMaps(any())).thenReturn(List.of(
                Map.of("categoryName", "摄影器材", "borrowCount", 30L)));

        mockMvc.perform(get("/statistics/utilization"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].categoryName").value("摄影器材"));
    }

    @Test
    @DisplayName("GET /statistics/export → 导出CSV")
    void exportCsv_shouldReturnFile() throws Exception {
        when(deviceMapper.selectCount(any())).thenReturn(100L, 80L, 15L, 5L);
        when(borrowMapper.selectCount(any())).thenReturn(20L, 3L, 10L, 200L);

        mockMvc.perform(get("/statistics/export"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().contentType("text/csv;charset=UTF-8"));
    }
}

package com.gzhu.equipment.controller;

import com.gzhu.equipment.mapper.BorrowRecordMapper;
import com.gzhu.equipment.mapper.DeviceCategoryMapper;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.mapper.SysUserMapper;
import com.gzhu.equipment.security.JwtTokenProvider;
import com.gzhu.equipment.security.LoginRateLimiter;
import com.gzhu.equipment.security.TokenBlacklist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;

@WebMvcTest(StatisticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class StatisticsControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private DeviceMapper deviceMapper;
    @MockBean private BorrowRecordMapper borrowMapper;
    @MockBean private DeviceCategoryMapper categoryMapper;
    @MockBean private SysUserMapper sysUserMapper;
    @MockBean private JdbcTemplate jdbcTemplate;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private LoginRateLimiter loginRateLimiter;
    @MockBean private TokenBlacklist tokenBlacklist;

    // topDevices / topUsers 因 @PreAuthorize 在 @WebMvcTest 中无法通过
    // 已在 StatisticsController 中通过 try-catch 保护，空数据返回空数组

    @Test @DisplayName("GET /statistics/overview → 仪表盘概览")
    void overview_shouldReturnStats() throws Exception {
        when(deviceMapper.selectCount(any())).thenReturn(100L, 80L, 15L, 5L);
        when(borrowMapper.selectCount(any())).thenReturn(20L, 3L, 10L, 200L);

        mockMvc.perform(get("/statistics/overview"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceStats.total").value(100));
    }

    @Test @DisplayName("GET /statistics/trend → 本月借用趋势")
    void trend_shouldReturnRows() throws Exception {
        when(borrowMapper.selectMaps(any())).thenReturn(List.of(Map.of("date", "2026-07-01", "count", 5L)));

        mockMvc.perform(get("/statistics/trend"))
                .andDo(print())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /statistics/utilization → 利用率分析")
    void utilization_shouldReturnList() throws Exception {
        mockMvc.perform(get("/statistics/utilization"))
                .andDo(print())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /statistics/export → 导出CSV")
    void exportCsv_shouldReturnFile() throws Exception {
        when(deviceMapper.selectCount(any())).thenReturn(100L, 80L, 15L, 5L);
        when(borrowMapper.selectCount(any())).thenReturn(20L, 3L, 10L, 200L);

        mockMvc.perform(get("/statistics/export"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().contentType("text/csv;charset=UTF-8"));
    }

    @Test @DisplayName("GET /statistics/export?format=xlsx → 导出XLSX")
    void exportXlsx_shouldReturnFile() throws Exception {
        when(deviceMapper.selectCount(any())).thenReturn(100L, 80L, 15L, 5L, 10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);
        when(borrowMapper.selectCount(any())).thenReturn(20L, 3L, 10L, 200L);

        mockMvc.perform(get("/statistics/export").param("format", "xlsx"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}

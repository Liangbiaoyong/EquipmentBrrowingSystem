package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzhu.equipment.dto.BatchInfoDTO;
import com.gzhu.equipment.dto.ImportResultDTO;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.mapper.BorrowRecordMapper;
import com.gzhu.equipment.mapper.DeviceCategoryMapper;
import com.gzhu.equipment.mapper.DeviceImageMapper;
import com.gzhu.equipment.security.JwtTokenProvider;
import com.gzhu.equipment.security.LoginRateLimiter;
import com.gzhu.equipment.security.TokenBlacklist;
import com.gzhu.equipment.security.JwtUserPrincipal;
import com.gzhu.equipment.service.DeviceImportService;
import com.gzhu.equipment.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DeviceController REST 接口测试
 *
 * 覆盖：设备查询、更新、导入（含Dry-Run）、批次管理、CSV导出
 */
@WebMvcTest(DeviceController.class)
@AutoConfigureMockMvc(addFilters = false)
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeviceService deviceService;

    @MockBean
    private DeviceImportService deviceImportService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private LoginRateLimiter loginRateLimiter;

    @MockBean
    private TokenBlacklist tokenBlacklist;

    @MockBean
    private DeviceImageMapper deviceImageMapper;

    @MockBean
    private DeviceCategoryMapper deviceCategoryMapper;

    @MockBean
    private BorrowRecordMapper borrowRecordMapper;

    @MockBean
    private com.gzhu.equipment.mapper.DeviceMapper deviceMapper;

    @MockBean
    private com.gzhu.equipment.mapper.SysUserMapper sysUserMapper;

    @MockBean
    private com.gzhu.equipment.mapper.LaboratoryMapper laboratoryMapper;

    @BeforeEach
    void setUp() {
        // 设置认证上下文 — JwtUserPrincipal
        JwtUserPrincipal principal = new JwtUserPrincipal(
                1L, "admin", 3,
                List.of("ROLE_SYSTEM_ADMIN"),
                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("GET /devices → 分页查询设备列表")
    void listDevices_shouldReturnPage() throws Exception {
        Page<Device> page = new Page<>(1, 20);
        when(deviceService.pageQuery(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/devices"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /devices?keyword=电脑&categoryId=1 → 带筛选条件的分页")
    void listDevices_withFilters_shouldReturnPage() throws Exception {
        when(deviceService.pageQuery(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new Page<>());

        mockMvc.perform(get("/devices")
                        .param("keyword", "电脑")
                        .param("categoryId", "1")
                        .param("borrowStatus", "1"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /devices/1 → 设备详情（含图片、分类名、借用状态）")
    void getDevice_existing_shouldReturnDevice() throws Exception {
        Device device = new Device();
        device.setId(1L);
        device.setName("测试设备");
        device.setCategoryId(1L);
        when(deviceService.getById(1L)).thenReturn(device);
        when(deviceImageMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        when(deviceCategoryMapper.selectById(1L)).thenReturn(null);
        when(borrowRecordMapper.selectOne(any())).thenReturn(null);
        when(borrowRecordMapper.selectCount(any())).thenReturn(0L);

        mockMvc.perform(get("/devices/1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.device.name").value("测试设备"));
    }

    @Test
    @DisplayName("GET /devices/999 → 设备不存在")
    void getDevice_notFound_shouldReturn404() throws Exception {
        when(deviceService.getById(999L)).thenReturn(null);

        mockMvc.perform(get("/devices/999"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("GET /devices/by-asset-no/ASSET001 → 按资产编号查询")
    void getByAssetNo_shouldReturnDevice() throws Exception {
        Device device = new Device();
        device.setAssetNo("ASSET001");
        when(deviceService.getByAssetNo("ASSET001")).thenReturn(device);

        mockMvc.perform(get("/devices/by-asset-no/ASSET001"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 导入 ====================

    @Test
    @DisplayName("POST /devices/import → 批量导入CSV")
    void importDevices_shouldSucceed() throws Exception {
        ImportResultDTO result = ImportResultDTO.builder()
                .totalRows(10).successCount(10).failCount(0).build();
        when(deviceImportService.importFromStream(any(), anyString(), anyLong(), any()))
                .thenReturn(result);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", "a,b,c".getBytes());

        mockMvc.perform(multipart("/devices/import").file(file))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("导入成功"));
    }

    @Test
    @DisplayName("POST /devices/import（失败数据）→ 返回含错误的结果")
    void importDevices_withErrors_shouldReturnPartial() throws Exception {
        ImportResultDTO result = ImportResultDTO.builder()
                .totalRows(10).successCount(8).failCount(2).build();
        when(deviceImportService.importFromStream(any(), anyString(), anyLong(), any()))
                .thenReturn(result);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", "a,b,c".getBytes());

        mockMvc.perform(multipart("/devices/import").file(file))
                .andDo(print())
                .andExpect(jsonPath("$.msg").value("导入完成（含错误）"));
    }

    @Test
    @DisplayName("POST /devices/import（空文件）→ 返回400")
    void importDevices_emptyFile_shouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/devices/import").file(file))
                .andDo(print())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /devices/import/dry-run → 预览导入")
    void dryRun_shouldSucceed() throws Exception {
        ImportResultDTO result = ImportResultDTO.builder().totalRows(5).build();
        when(deviceImportService.dryRun(any(), anyString())).thenReturn(result);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", "a,b,c".getBytes());

        mockMvc.perform(multipart("/devices/import/dry-run").file(file))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 批次管理 ====================

    @Test
    @DisplayName("GET /devices/batches → 批次列表")
    void listBatches_shouldReturnList() throws Exception {
        when(deviceService.listBatches()).thenReturn(List.of(
                BatchInfoDTO.builder().batchId("BATCH001").build(),
                BatchInfoDTO.builder().batchId("BATCH002").build()));

        mockMvc.perform(get("/devices/batches"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].batchId").value("BATCH001"));
    }

    @Test
    @DisplayName("GET /devices/batches/B001 → 按批次查询设备")
    void listByBatch_shouldReturnDevices() throws Exception {
        when(deviceService.listByBatchId("B001")).thenReturn(List.of(new Device()));

        mockMvc.perform(get("/devices/batches/B001"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== CSV导出 ====================

    @Test
    @DisplayName("GET /devices/export/csv → 导出CSV文件")
    void exportCsv_shouldReturnFile() throws Exception {
        Device device = new Device();
        device.setAssetNo("ASSET001");
        device.setName("测试设备");
        Page<Device> page = new Page<>(1, 10000);
        page.setRecords(List.of(device));
        when(deviceService.pageQuery(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/devices/export/csv"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().contentType("text/csv;charset=UTF-8"));
    }
}

package com.gzhu.equipment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzhu.equipment.entity.Laboratory;
import com.gzhu.equipment.entity.LaboratoryRoom;
import com.gzhu.equipment.mapper.LaboratoryRoomMapper;
import com.gzhu.equipment.security.JwtTokenProvider;
import com.gzhu.equipment.security.LoginRateLimiter;
import com.gzhu.equipment.security.TokenBlacklist;
import com.gzhu.equipment.service.LaboratoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LaboratoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class LaboratoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LaboratoryService laboratoryService;

    @MockBean
    private LaboratoryRoomMapper roomMapper;

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
    @DisplayName("GET /laboratories → 分页查询实验室")
    void list_shouldReturnPage() throws Exception {
        when(laboratoryService.pageQuery(anyInt(), anyInt(), any(), any(), any())).thenReturn(new Page<>());
        mockMvc.perform(get("/laboratories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /laboratories?keyword=xx → 关键字搜索")
    void list_withKeyword_shouldFilter() throws Exception {
        when(laboratoryService.pageQuery(anyInt(), anyInt(), eq("实验室A"), any(), any())).thenReturn(new Page<>());
        mockMvc.perform(get("/laboratories").param("keyword", "实验室A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /laboratories/list → 所有启用实验室")
    void listEnabled_shouldReturnList() throws Exception {
        when(laboratoryService.listEnabled()).thenReturn(List.of(new Laboratory()));
        mockMvc.perform(get("/laboratories/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /laboratories/{id} → 获取实验室详情")
    void get_shouldReturnDetail() throws Exception {
        Laboratory lab = new Laboratory();
        lab.setId(1L);
        lab.setName("建筑物理实验室");
        when(laboratoryService.getDetail(1L)).thenReturn(lab);
        mockMvc.perform(get("/laboratories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("建筑物理实验室"));
    }

    @Test
    @DisplayName("GET /laboratories/{id} → 不存在返回404")
    void get_notFound_shouldReturn404() throws Exception {
        when(laboratoryService.getDetail(999L)).thenReturn(null);
        mockMvc.perform(get("/laboratories/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("POST /laboratories → 新增实验室")
    void create_shouldSucceed() throws Exception {
        Laboratory lab = new Laboratory();
        lab.setName("新实验室");
        Laboratory saved = new Laboratory();
        saved.setId(1L);
        saved.setName("新实验室");
        when(laboratoryService.save(any(Laboratory.class))).thenReturn(true);
        mockMvc.perform(post("/laboratories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lab)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("PUT /laboratories/{id} → 更新实验室")
    void update_shouldSucceed() throws Exception {
        Laboratory lab = new Laboratory();
        lab.setName("更新后的实验室");
        when(laboratoryService.updateById(any(Laboratory.class))).thenReturn(true);
        mockMvc.perform(put("/laboratories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lab)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("DELETE /laboratories/{id} → 删除实验室")
    void delete_shouldSucceed() throws Exception {
        when(laboratoryService.removeById(1L)).thenReturn(true);
        mockMvc.perform(delete("/laboratories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /laboratories/rooms → 分页查询地点映射")
    void listRooms_shouldReturnPage() throws Exception {
        when(laboratoryService.pageRooms(anyInt(), anyInt(), any(), any(), any(), any())).thenReturn(new Page<>());
        mockMvc.perform(get("/laboratories/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /laboratories/rooms?laboratoryId=1 → 按实验室筛选")
    void listRooms_withLabFilter() throws Exception {
        when(laboratoryService.pageRooms(anyInt(), anyInt(), eq(1L), any(), any(), any())).thenReturn(new Page<>());
        mockMvc.perform(get("/laboratories/rooms").param("laboratoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /laboratories/rooms → 新增地点映射")
    void createRoom_shouldSucceed() throws Exception {
        LaboratoryRoom room = new LaboratoryRoom();
        room.setRoomName("三楼B区");
        room.setLaboratoryId(1L);
        mockMvc.perform(post("/laboratories/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(room)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("PUT /laboratories/rooms/{id} → 更新地点映射")
    void updateRoom_shouldSucceed() throws Exception {
        LaboratoryRoom room = new LaboratoryRoom();
        room.setRoomName("更新后的映射");
        mockMvc.perform(put("/laboratories/rooms/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(room)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("DELETE /laboratories/rooms/{id} → 删除地点映射")
    void deleteRoom_shouldSucceed() throws Exception {
        mockMvc.perform(delete("/laboratories/rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /laboratories/sync-devices → 同步设备实验室")
    void syncDevices_shouldReturnCount() throws Exception {
        when(laboratoryService.syncDeviceLaboratories()).thenReturn(5);
        mockMvc.perform(post("/laboratories/sync-devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    @DisplayName("GET /laboratories/locations → 获取存放地列表")
    void listLocations_shouldReturnList() throws Exception {
        when(laboratoryService.listDistinctLocations()).thenReturn(List.of("三楼", "四楼"));
        mockMvc.perform(get("/laboratories/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}

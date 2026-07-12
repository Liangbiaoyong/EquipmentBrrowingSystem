package com.gzhu.equipment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzhu.equipment.dto.ApprovalRequestDTO;
import com.gzhu.equipment.dto.BorrowRequestDTO;
import com.gzhu.equipment.entity.BorrowRecord;
import com.gzhu.equipment.security.JwtTokenProvider;
import com.gzhu.equipment.security.LoginRateLimiter;
import com.gzhu.equipment.security.TokenBlacklist;
import com.gzhu.equipment.mapper.ApprovalLogMapper;
import com.gzhu.equipment.mapper.AttachmentMapper;
import com.gzhu.equipment.security.JwtUserPrincipal;
import com.gzhu.equipment.service.BorrowService;
import com.gzhu.equipment.service.MinioFileService;
import com.gzhu.equipment.mapper.BorrowOutcomeMapper;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BorrowController REST 接口测试
 */
@WebMvcTest(BorrowController.class)
@AutoConfigureMockMvc(addFilters = false)
class BorrowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BorrowService borrowService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private LoginRateLimiter loginRateLimiter;

    @MockBean
    private TokenBlacklist tokenBlacklist;

    @MockBean
    private MinioFileService minioFileService;

    @MockBean
    private AttachmentMapper attachmentMapper;

    @MockBean
    private ApprovalLogMapper approvalLogMapper;

    @MockBean
    private BorrowOutcomeMapper borrowOutcomeMapper;

    @BeforeEach
    void setUp() {
        JwtUserPrincipal principal = new JwtUserPrincipal(
                1L, "student01", 0,
                List.of("ROLE_STUDENT"),
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private BorrowRequestDTO fullBorrowRequest() {
        BorrowRequestDTO dto = new BorrowRequestDTO();
        dto.setDeviceId(1L);
        dto.setStartTime(LocalDateTime.now().plusDays(1));
        dto.setEndTime(LocalDateTime.now().plusDays(3));
        dto.setReason("实验用");
        dto.setPurpose("实验测试");
        dto.setApproverId(2L);
        return dto;
    }

    @Test
    @DisplayName("POST /borrows → 提交借用申请")
    void submitBorrow_shouldReturnRecord() throws Exception {
        BorrowRecord record = new BorrowRecord();
        record.setId(100L);
        record.setStatus("PENDING_APPROVAL");
        when(borrowService.submitBorrow(any(), anyLong())).thenReturn(java.util.List.of(record));

        mockMvc.perform(post("/borrows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullBorrowRequest())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /borrows（业务错误）→ 返回错误消息")
    void submitBorrow_error_shouldReturnError() throws Exception {
        when(borrowService.submitBorrow(any(), anyLong()))
                .thenThrow(new IllegalArgumentException("设备不存在"));

        mockMvc.perform(post("/borrows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullBorrowRequest())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("设备不存在"));
    }

    @Test
    @DisplayName("GET /borrows/my → 我的借用列表")
    void myBorrows_shouldReturnPage() throws Exception {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<BorrowRecord> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>();
        when(borrowService.myBorrows(anyLong(), anyInt(), anyInt(), any())).thenReturn(page);

        mockMvc.perform(get("/borrows/my"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /borrows/pending/first → 一级待审批列表")
    void pendingFirst_shouldReturnPage() throws Exception {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<BorrowRecord> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>();
        when(borrowService.pendingApprovals(anyLong(), anyInt(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/borrows/pending/first"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /borrows/approve → 审批操作")
    void approve_shouldSucceed() throws Exception {
        when(borrowService.approve(any(), anyLong())).thenReturn(new BorrowRecord());

        ApprovalRequestDTO dto = new ApprovalRequestDTO();
        dto.setBorrowId(1L);
        dto.setApproved(true);

        mockMvc.perform(post("/borrows/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /borrows/1 → 借用详情")
    void getDetail_shouldReturnRecord() throws Exception {
        BorrowRecord record = new BorrowRecord();
        record.setId(1L);
        when(borrowService.getDetail(1L)).thenReturn(record);

        mockMvc.perform(get("/borrows/1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}

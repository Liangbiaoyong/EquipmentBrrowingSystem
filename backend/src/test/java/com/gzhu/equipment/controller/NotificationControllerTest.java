package com.gzhu.equipment.controller;

import com.gzhu.equipment.entity.Notification;
import com.gzhu.equipment.security.JwtTokenProvider;
import com.gzhu.equipment.security.LoginRateLimiter;
import com.gzhu.equipment.security.TokenBlacklist;
import com.gzhu.equipment.service.NotificationService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

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
    @DisplayName("GET /notifications → 我的通知列表")
    void list_shouldReturnList() throws Exception {
        when(notificationService.listByUser(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /notifications/unread-count → 未读数")
    void unreadCount_shouldReturnCount() throws Exception {
        when(notificationService.unreadCount(1L)).thenReturn(3);
        mockMvc.perform(get("/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.unreadCount").value(3));
    }

    @Test
    @DisplayName("PUT /notifications/{id}/read → 标记已读")
    void markRead_shouldSucceed() throws Exception {
        mockMvc.perform(put("/notifications/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("PUT /notifications/read-all → 全部已读")
    void markAllRead_shouldSucceed() throws Exception {
        mockMvc.perform(put("/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}

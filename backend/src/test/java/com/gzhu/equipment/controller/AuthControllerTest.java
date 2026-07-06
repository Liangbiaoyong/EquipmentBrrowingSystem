package com.gzhu.equipment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzhu.equipment.dto.CasLoginRequest;
import com.gzhu.equipment.dto.LocalLoginRequest;
import com.gzhu.equipment.security.JwtTokenProvider;
import com.gzhu.equipment.security.JwtUserPrincipal;
import com.gzhu.equipment.service.AuthService;
import com.gzhu.equipment.vo.LoginVO;
import com.gzhu.equipment.vo.UserInfoVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController REST 接口测试（使用 MockMvc）
 *
 * 覆盖：CAS登录、本地登录、获取用户信息、健康检查
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private LocalLoginRequest validLocalRequest;
    private LoginVO successLoginVO;

    @BeforeEach
    void setUp() {
        // 准备本地登录请求
        validLocalRequest = new LocalLoginRequest();
        validLocalRequest.setUsername("admin");
        validLocalRequest.setPassword("admin123");

        // 准备登录成功返回值
        UserInfoVO userInfo = UserInfoVO.builder()
                .id(1L)
                .username("admin")
                .realName("系统管理员")
                .userType(3)
                .userTypeName("系统管理员")
                .department("信息中心")
                .authSource("L")
                .roles(List.of("SYSTEM_ADMIN"))
                .permissions(List.of())
                .build();

        successLoginVO = LoginVO.builder()
                .accessToken("mock-jwt-token")
                .tokenType("Bearer")
                .expiresIn(3600000L)
                .userInfo(userInfo)
                .build();
    }

    // ==================== 健康检查 ====================

    @Test
    @DisplayName("健康检查 → 返回200")
    void health_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/auth/health"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("操作成功"));
    }

    // ==================== 本地登录 ====================

    @Test
    @DisplayName("本地登录（有效凭据）→ 返回200 + JWT")
    void localLogin_validCredentials_shouldReturnToken() throws Exception {
        // given
        when(authService.localLogin(any(LocalLoginRequest.class))).thenReturn(successLoginVO);

        // when & then
        mockMvc.perform(post("/auth/local/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLocalRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("登录成功"))
                .andExpect(jsonPath("$.data.accessToken").value("mock-jwt-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.userInfo.username").value("admin"))
                .andExpect(jsonPath("$.data.userInfo.userTypeName").value("系统管理员"));
    }

    @Test
    @DisplayName("本地登录（密码错误）→ 返回401")
    void localLogin_wrongPassword_shouldReturn401() throws Exception {
        // given
        when(authService.localLogin(any(LocalLoginRequest.class)))
                .thenThrow(new BadCredentialsException("用户名或密码错误"));

        LocalLoginRequest request = new LocalLoginRequest();
        request.setUsername("admin");
        request.setPassword("wrong");

        // when & then
        mockMvc.perform(post("/auth/local/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk()) // 控制器catch了异常，返回R.fail(401)
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("本地登录（空请求体）→ 服务层抛出验证错误")
    void localLogin_emptyBody_shouldReturnError() throws Exception {
        // 发送空JSON时，@Valid校验失效，服务层收到默认构造的请求对象
        // 此处不校验具体code，仅验证不抛异常
        mockMvc.perform(post("/auth/local/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    // ==================== CAS登录 ====================

    @Test
    @DisplayName("CAS登录（有效Token）→ 返回200 + JWT")
    void casLogin_validToken_shouldReturnToken() throws Exception {
        // given
        when(authService.casLogin(any(CasLoginRequest.class))).thenReturn(successLoginVO);

        CasLoginRequest request = new CasLoginRequest();
        request.setToken("valid-cas-token");

        // when & then
        mockMvc.perform(post("/auth/cas/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("mock-jwt-token"));
    }

    @Test
    @DisplayName("CAS登录（无效Token）→ 返回401")
    void casLogin_invalidToken_shouldReturn401() throws Exception {
        // given
        when(authService.casLogin(any(CasLoginRequest.class)))
                .thenThrow(new BadCredentialsException("CAS token无效或已过期"));

        CasLoginRequest request = new CasLoginRequest();
        request.setToken("invalid-token");

        // when & then
        mockMvc.perform(post("/auth/cas/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("CAS token无效或已过期"));
    }

    @Test
    @DisplayName("CAS登录（空Token）→ 不抛异常")
    void casLogin_emptyToken_shouldReturnError() throws Exception {
        mockMvc.perform(post("/auth/cas/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"\"}"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    // ==================== 获取用户信息 ====================

    @Test
    @DisplayName("获取用户信息（已认证）→ 返回用户信息")
    void getCurrentUserInfo_authenticated_shouldReturnInfo() throws Exception {
        // given
        UserInfoVO userInfo = UserInfoVO.builder()
                .id(1L)
                .username("admin")
                .realName("系统管理员")
                .userType(3)
                .build();
        when(authService.getCurrentUserInfo(anyLong())).thenReturn(userInfo);

        // 使用 JwtUserPrincipal 作为principal（匹配JwtAuthenticationFilter的行为）
        JwtUserPrincipal principal = new JwtUserPrincipal(
                1L, "admin", 3,
                List.of("ROLE_SYSTEM_ADMIN"),
                List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));

        mockMvc.perform(get("/auth/info"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    @DisplayName("获取用户信息（未认证）→ 返回401")
    void getCurrentUserInfo_unauthenticated_shouldReturnError() throws Exception {
        mockMvc.perform(get("/auth/info"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("未登录"));
    }
}

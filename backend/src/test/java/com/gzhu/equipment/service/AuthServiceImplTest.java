package com.gzhu.equipment.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzhu.equipment.dto.CasLoginRequest;
import com.gzhu.equipment.dto.LocalLoginRequest;
import com.gzhu.equipment.entity.SysUser;
import com.gzhu.equipment.security.JwtTokenProvider;
import com.gzhu.equipment.service.SysUserService;
import com.gzhu.equipment.vo.LoginVO;
import com.gzhu.equipment.vo.UserInfoVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthServiceImpl 单元测试
 *
 * 覆盖：CAS登录、本地登录、用户信息获取
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String TEST_SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha-algorithm";
    private static final long TEST_EXPIRATION = 3600000L;

    @Mock
    private SysUserService sysUserService;

    private JwtTokenProvider jwtTokenProvider;
    private PasswordEncoder passwordEncoder;
    private ObjectMapper objectMapper;
    private RestTemplate restTemplate;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, TEST_EXPIRATION);
        objectMapper = new ObjectMapper();
        restTemplate = mock(RestTemplate.class);

        authService = new AuthServiceImpl(
                sysUserService, jwtTokenProvider, passwordEncoder,
                objectMapper, restTemplate);
    }

    // ==================== 本地登录 ====================

    @Test
    @DisplayName("本地登录（正确凭据）→ 返回 LoginVO")
    void localLogin_validCredentials_shouldReturnLoginVO() {
        // given
        String rawPassword = "admin123";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        SysUser localUser = new SysUser();
        localUser.setId(1L);
        localUser.setUsername("admin");
        localUser.setRealName("系统管理员");
        localUser.setUserType(3);
        localUser.setAuthSource("L");
        localUser.setPassword(encodedPassword);
        localUser.setStatus(1);
        localUser.setDepartment("信息中心");

        when(sysUserService.getByUsername("admin")).thenReturn(localUser);

        LocalLoginRequest request = new LocalLoginRequest();
        request.setUsername("admin");
        request.setPassword(rawPassword);

        // when
        LoginVO loginVO = authService.localLogin(request);

        // then
        assertThat(loginVO).isNotNull();
        assertThat(loginVO.getAccessToken()).isNotNull().isNotEmpty();
        assertThat(loginVO.getTokenType()).isEqualTo("Bearer");
        assertThat(loginVO.getExpiresIn()).isEqualTo(TEST_EXPIRATION);
        assertThat(loginVO.getUserInfo()).isNotNull();
        assertThat(loginVO.getUserInfo().getUsername()).isEqualTo("admin");
        assertThat(loginVO.getUserInfo().getUserTypeName()).isEqualTo("系统管理员");
    }

    @Test
    @DisplayName("本地登录（密码错误）→ 抛出异常")
    void localLogin_wrongPassword_shouldThrow() {
        // given
        SysUser localUser = new SysUser();
        localUser.setAuthSource("L");
        localUser.setPassword(passwordEncoder.encode("correct-password"));
        localUser.setStatus(1);

        when(sysUserService.getByUsername("admin")).thenReturn(localUser);

        LocalLoginRequest request = new LocalLoginRequest();
        request.setUsername("admin");
        request.setPassword("wrong-password");

        // when & then
        assertThatThrownBy(() -> authService.localLogin(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    @DisplayName("本地登录（用户不存在）→ 抛出异常")
    void localLogin_userNotFound_shouldThrow() {
        // given
        when(sysUserService.getByUsername("nobody")).thenReturn(null);

        LocalLoginRequest request = new LocalLoginRequest();
        request.setUsername("nobody");
        request.setPassword("pass");

        // when & then
        assertThatThrownBy(() -> authService.localLogin(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    @DisplayName("本地登录（用户被禁用）→ 抛出异常")
    void localLogin_disabledUser_shouldThrow() {
        // given
        SysUser disabledUser = new SysUser();
        disabledUser.setAuthSource("L");
        disabledUser.setStatus(0);

        when(sysUserService.getByUsername("disabled")).thenReturn(disabledUser);

        LocalLoginRequest request = new LocalLoginRequest();
        request.setUsername("disabled");
        request.setPassword("pass");

        // when & then
        assertThatThrownBy(() -> authService.localLogin(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("用户已被禁用");
    }

    @Test
    @DisplayName("本地登录（CAS用户尝试本地登录）→ 抛出异常")
    void localLogin_casUser_shouldThrow() {
        // given
        SysUser casUser = new SysUser();
        casUser.setAuthSource("C");
        casUser.setPassword("");
        casUser.setStatus(1);

        when(sysUserService.getByUsername("zhangsan")).thenReturn(casUser);

        LocalLoginRequest request = new LocalLoginRequest();
        request.setUsername("zhangsan");
        request.setPassword("pass");

        // when & then
        assertThatThrownBy(() -> authService.localLogin(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("CAS用户请使用统一认证登录");
    }

    @Test
    @DisplayName("本地登录（用户无密码）→ 抛出异常")
    void localLogin_noPassword_shouldThrow() {
        // given
        SysUser noPassUser = new SysUser();
        noPassUser.setAuthSource("L");
        noPassUser.setPassword("");
        noPassUser.setStatus(1);

        when(sysUserService.getByUsername("nopass")).thenReturn(noPassUser);

        LocalLoginRequest request = new LocalLoginRequest();
        request.setUsername("nopass");
        request.setPassword("pass");

        // when & then
        assertThatThrownBy(() -> authService.localLogin(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("账户未设置密码");
    }

    // ==================== 获取用户信息 ====================

    @Test
    @DisplayName("获取当前用户信息 → 返回 UserInfoVO")
    void getCurrentUserInfo_validUser_shouldReturnUserInfo() {
        // given
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("zhangsan");
        user.setRealName("张三");
        user.setUserType(0);
        user.setDepartment("建筑学院");
        user.setAuthSource("C");
        user.setStatus(1);

        when(sysUserService.getById(1L)).thenReturn(user);

        // when
        UserInfoVO userInfo = authService.getCurrentUserInfo(1L);

        // then
        assertThat(userInfo.getUsername()).isEqualTo("zhangsan");
        assertThat(userInfo.getUserTypeName()).isEqualTo("学生");
        assertThat(userInfo.getAuthSource()).isEqualTo("C");
        assertThat(userInfo.getRoles()).contains("STUDENT");
    }

    @Test
    @DisplayName("获取用户信息（用户不存在）→ 抛出异常")
    void getCurrentUserInfo_userNotFound_shouldThrow() {
        when(sysUserService.getById(999L)).thenReturn(null);

        assertThatThrownBy(() -> authService.getCurrentUserInfo(999L))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("用户不存在或已禁用");
    }

    @Test
    @DisplayName("获取用户信息（用户已禁用）→ 抛出异常")
    void getCurrentUserInfo_disabledUser_shouldThrow() {
        // given
        SysUser disabled = new SysUser();
        disabled.setStatus(0);
        when(sysUserService.getById(1L)).thenReturn(disabled);

        // when & then
        assertThatThrownBy(() -> authService.getCurrentUserInfo(1L))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ==================== 用户类型全映射测试 ====================

    @Test
    @DisplayName("用户类型映射 → 所有角色正确")
    void buildUserInfoVO_allUserTypes() {
        for (int type = 0; type <= 3; type++) {
            // given
            SysUser user = new SysUser();
            user.setId((long) type);
            user.setUsername("user" + type);
            user.setUserType(type);
            user.setAuthSource("L");
            user.setStatus(1);

            when(sysUserService.getById((long) type)).thenReturn(user);

            // when
            UserInfoVO info = authService.getCurrentUserInfo((long) type);

            // then
            assertThat(info.getUserType()).isEqualTo(type);
        }
    }

    // ==================== CAS 登录 ====================

    @Test
    @DisplayName("CAS登录（有效Token）→ 返回 LoginVO")
    void casLogin_validToken_shouldReturnLoginVO() {
        // given: Mock RestTemplate 返回成功的CAS用户信息
        String casResponse = "{"
                + "\"code\":\"0\","
                + "\"message\":\"success\","
                + "\"data\":{"
                + "\"uuid\":\"stu-uuid\","
                + "\"logonName\":\"2022010101\","
                + "\"trueName\":\"李四\","
                + "\"deptName\":\"建筑学院\","
                + "\"className\":\"建筑学191\","
                + "\"ident\":257,"
                + "\"sex\":1,"
                + "\"status\":1"
                + "}}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(casResponse, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(responseEntity);

        SysUser savedUser = new SysUser();
        savedUser.setId(10L);
        savedUser.setUsername("2022010101");
        savedUser.setRealName("李四");
        savedUser.setUserType(0);
        savedUser.setAuthSource("C");
        savedUser.setStatus(1);
        savedUser.setDepartment("建筑学院");
        when(sysUserService.createOrUpdateCasUser(any(SysUser.class))).thenReturn(savedUser);

        CasLoginRequest request = new CasLoginRequest();
        request.setToken("valid-cas-token");

        // when
        LoginVO loginVO = authService.casLogin(request);

        // then
        assertThat(loginVO).isNotNull();
        assertThat(loginVO.getAccessToken()).isNotNull().isNotEmpty();
        assertThat(loginVO.getUserInfo().getUsername()).isEqualTo("2022010101");
        assertThat(loginVO.getUserInfo().getUserTypeName()).isEqualTo("学生");
    }

    @Test
    @DisplayName("CAS登录（无效Token）→ 抛出异常")
    void casLogin_invalidToken_shouldThrow() {
        // given: RestTemplate 返回失败
        String errorResponse = "{\"code\":\"1\",\"message\":\"token无效\"}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(errorResponse, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(responseEntity);

        CasLoginRequest request = new CasLoginRequest();
        request.setToken("invalid-token");

        // when & then
        assertThatThrownBy(() -> authService.casLogin(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("CAS token无效或已过期");
    }

    // ==================== JSON解析辅助方法测试 ====================

    @Test
    @DisplayName("CAS用户信息JSON解析 → 学生类型正确映射")
    void parseCasUserInfo_studentIdent_shouldMapToStudent() throws Exception {
        // given
        String json = "{"
                + "\"uuid\":\"stu-uuid-001\","
                + "\"logonName\":\"2022010101\","
                + "\"trueName\":\"李四\","
                + "\"deptName\":\"建筑学院\","
                + "\"className\":\"建筑学191\","
                + "\"ident\":257,"
                + "\"sex\":1,"
                + "\"cardNo\":\"2022010101\","
                + "\"email\":\"lisi@gzhu.edu.cn\","
                + "\"handPhone\":\"13800000002\","
                + "\"status\":1,"
                + "\"accNo\":10001,"
                + "\"expiredDate\":20280630"
                + "}";

        // when - 通过反射测试私有方法
        JsonNode data = objectMapper.readTree(json);
        java.lang.reflect.Method method = AuthServiceImpl.class.getDeclaredMethod("parseCasUserInfo", JsonNode.class);
        method.setAccessible(true);
        SysUser user = (SysUser) method.invoke(authService, data);

        // then
        assertThat(user.getUsername()).isEqualTo("2022010101");
        assertThat(user.getRealName()).isEqualTo("李四");
        assertThat(user.getUserType()).isEqualTo(0); // 学生
        assertThat(user.getIdent()).isEqualTo(257);
        assertThat(user.getDepartment()).isEqualTo("建筑学院");
        assertThat(user.getClassName()).isEqualTo("建筑学191");
        assertThat(user.getCardNo()).isEqualTo("2022010101");
        assertThat(user.getSex()).isEqualTo(1);
        assertThat(user.getStatus()).isEqualTo(1);
        assertThat(user.getExpiredDate()).isEqualTo(20280630);
    }

    @Test
    @DisplayName("CAS用户信息JSON解析 → 教师类型正确映射")
    void parseCasUserInfo_teacherIdent_shouldMapToTeacher() throws Exception {
        // given
        String json = "{"
                + "\"uuid\":\"tch-uuid-001\","
                + "\"logonName\":\"2005001\","
                + "\"trueName\":\"王教授\","
                + "\"deptName\":\"建筑学院\","
                + "\"className\":\"建筑学院\","
                + "\"ident\":259,"
                + "\"sex\":1,"
                + "\"email\":\"wang@gzhu.edu.cn\","
                + "\"status\":1"
                + "}";

        // when
        JsonNode data = objectMapper.readTree(json);
        java.lang.reflect.Method method = AuthServiceImpl.class.getDeclaredMethod("parseCasUserInfo", JsonNode.class);
        method.setAccessible(true);
        SysUser user = (SysUser) method.invoke(authService, data);

        // then
        assertThat(user.getUserType()).isEqualTo(1); // 教师
        assertThat(user.getIdent()).isEqualTo(259);
    }
}

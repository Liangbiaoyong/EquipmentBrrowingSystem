package com.gzhu.equipment.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtTokenProvider 单元测试
 *
 * 覆盖：Token生成、解析、校验、过期、异常处理
 */
@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private static final String TEST_SECRET = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha-algorithm";
    private static final long TEST_EXPIRATION = 3600000L; // 1小时

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, TEST_EXPIRATION);
    }

    @Test
    @DisplayName("生成Token → 包含用户ID、用户名、角色、userType")
    void generateToken_shouldContainAllClaims() {
        // given
        List<String> roles = Arrays.asList("ROLE_SYSTEM_ADMIN");

        // when
        String token = jwtTokenProvider.generateToken(1L, "admin", 3, roles);

        // then
        assertThat(token).isNotNull().isNotEmpty();

        Claims claims = jwtTokenProvider.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("username")).isEqualTo("admin");
        assertThat(claims.get("userType")).isEqualTo(3);
        assertThat(claims.get("roles")).isEqualTo(roles);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    @DisplayName("多角色Token → 角色列表完整保留")
    void generateToken_withMultipleRoles_shouldPreserveAllRoles() {
        // given
        List<String> roles = Arrays.asList("ROLE_TEACHER", "ROLE_LAB_ADMIN");

        // when
        String token = jwtTokenProvider.generateToken(2L, "teacher01", 1, roles);
        Claims claims = jwtTokenProvider.parseToken(token);

        // then
        @SuppressWarnings("unchecked")
        List<String> actualRoles = claims.get("roles", List.class);
        assertThat(actualRoles).containsExactly("ROLE_TEACHER", "ROLE_LAB_ADMIN");
    }

    @Test
    @DisplayName("校验有效Token → 返回true")
    void validateToken_validToken_shouldReturnTrue() {
        // given
        String token = jwtTokenProvider.generateToken(1L, "admin", 3, List.of("ROLE_SYSTEM_ADMIN"));

        // when
        boolean isValid = jwtTokenProvider.validateToken(token);

        // then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("校验无效Token → 返回false（格式错误）")
    void validateToken_malformedToken_shouldReturnFalse() {
        // when
        boolean isValid = jwtTokenProvider.validateToken("invalid.jwt.token");

        // then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("校验空Token → 返回false")
    void validateToken_emptyToken_shouldReturnFalse() {
        // when
        boolean isValid = jwtTokenProvider.validateToken("");

        // then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("校验过期Token → 返回false")
    void validateToken_expiredToken_shouldReturnFalse() throws Exception {
        // given
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(TEST_SECRET, 1L); // 1ms过期

        // 使用反射设置 expirationMs 来创建几乎立即过期的 token
        String token = shortLivedProvider.generateToken(1L, "admin", 3, List.of("ROLE_SYSTEM_ADMIN"));

        // 等待token过期
        Thread.sleep(10);

        // when
        boolean isValid = shortLivedProvider.validateToken(token);

        // then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("不同密钥生成的Token → 相互不认可")
    void validateToken_wrongSecret_shouldReturnFalse() {
        // given
        JwtTokenProvider providerA = new JwtTokenProvider(TEST_SECRET, TEST_EXPIRATION);
        JwtTokenProvider providerB = new JwtTokenProvider(
                "a-completely-different-secret-key-that-is-also-long-enough-for-testing", TEST_EXPIRATION);

        String token = providerA.generateToken(1L, "admin", 3, List.of("ROLE_SYSTEM_ADMIN"));

        // when
        boolean isValid = providerB.validateToken(token);

        // then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("从Token提取用户ID → 返回正确ID")
    void getUserId_shouldReturnCorrectId() {
        // given
        String token = jwtTokenProvider.generateToken(42L, "testuser", 0, List.of("ROLE_STUDENT"));

        // when
        Long userId = jwtTokenProvider.getUserId(token);

        // then
        assertThat(userId).isEqualTo(42L);
    }

    @Test
    @DisplayName("从Token提取用户名 → 返回正确用户名")
    void getUsername_shouldReturnCorrectUsername() {
        // given
        String token = jwtTokenProvider.generateToken(1L, "zhangsan", 0, List.of("ROLE_STUDENT"));

        // when
        String username = jwtTokenProvider.getUsername(token);

        // then
        assertThat(username).isEqualTo("zhangsan");
    }

    @Test
    @DisplayName("从Token提取角色 → 返回正确角色列表")
    void getRoles_shouldReturnCorrectRoles() {
        // given
        List<String> roles = Arrays.asList("ROLE_TEACHER", "ROLE_LAB_ADMIN");
        String token = jwtTokenProvider.generateToken(2L, "teacher01", 1, roles);

        // when
        List<String> actualRoles = jwtTokenProvider.getRoles(token);

        // then
        assertThat(actualRoles).containsExactly("ROLE_TEACHER", "ROLE_LAB_ADMIN");
    }

    @Test
    @DisplayName("解析过期Token → 抛出异常")
    void parseToken_expiredToken_shouldThrowException() throws Exception {
        // given
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(TEST_SECRET, 1L);
        String token = shortLivedProvider.generateToken(1L, "admin", 3, List.of("ROLE_SYSTEM_ADMIN"));
        Thread.sleep(10);

        // when & then
        assertThatThrownBy(() -> shortLivedProvider.parseToken(token))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }
}

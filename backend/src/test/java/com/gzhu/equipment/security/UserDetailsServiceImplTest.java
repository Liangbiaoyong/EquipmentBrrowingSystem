package com.gzhu.equipment.security;

import com.gzhu.equipment.entity.SysUser;
import com.gzhu.equipment.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * UserDetailsServiceImpl 单元测试 — 角色映射 + loadUserByUsername
 */
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;

    private UserDetailsServiceImpl userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new UserDetailsServiceImpl(sysUserMapper);
    }

    // ==================== loadUserByUsername ====================

    @Test
    @DisplayName("loadUserByUsername → 本地用户登录成功")
    void loadUserByUsername_localUser_shouldReturnUserDetails() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("$2a$10$encodedpasswordhash");
        user.setUserType(3);
        user.setAuthSource("L");
        user.setStatus(1);

        when(sysUserMapper.selectByUsername("admin")).thenReturn(user);

        UserDetails details = userDetailsService.loadUserByUsername("admin");

        assertThat(details.getUsername()).isEqualTo("admin");
        assertThat(details.getAuthorities()).isNotEmpty();
        assertThat(details.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_SYSTEM_ADMIN");
    }

    @Test
    @DisplayName("loadUserByUsername → 用户不存在抛出异常")
    void loadUserByUsername_notFound_shouldThrow() {
        when(sysUserMapper.selectByUsername("nobody")).thenReturn(null);

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("loadUserByUsername → 被禁用用户抛出异常")
    void loadUserByUsername_disabled_shouldThrow() {
        SysUser user = new SysUser();
        user.setUsername("disabled");
        user.setAuthSource("L");
        user.setStatus(0);

        when(sysUserMapper.selectByUsername("disabled")).thenReturn(user);

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("disabled"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("已被禁用");
    }

    @Test
    @DisplayName("loadUserByUsername → CAS用户尝试本地登录抛出异常")
    void loadUserByUsername_casUser_shouldThrow() {
        SysUser user = new SysUser();
        user.setUsername("zhangsan");
        user.setAuthSource("C");
        user.setStatus(1);

        when(sysUserMapper.selectByUsername("zhangsan")).thenReturn(user);

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("zhangsan"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("CAS用户不支持");
    }

    @Test
    @DisplayName("学生类型 → 返回 ROLE_STUDENT")
    void getUserRole_student_shouldReturnStudent() {
        assertThat(UserDetailsServiceImpl.getUserRole(0)).isEqualTo("STUDENT");
    }

    @Test
    @DisplayName("教师类型 → 返回 ROLE_TEACHER")
    void getUserRole_teacher_shouldReturnTeacher() {
        assertThat(UserDetailsServiceImpl.getUserRole(1)).isEqualTo("TEACHER");
    }

    @Test
    @DisplayName("实验室管理员 → 返回 ROLE_LAB_ADMIN")
    void getUserRole_labAdmin_shouldReturnLabAdmin() {
        assertThat(UserDetailsServiceImpl.getUserRole(2)).isEqualTo("LAB_ADMIN");
    }

    @Test
    @DisplayName("系统管理员 → 返回 ROLE_SYSTEM_ADMIN")
    void getUserRole_systemAdmin_shouldReturnSystemAdmin() {
        assertThat(UserDetailsServiceImpl.getUserRole(3)).isEqualTo("SYSTEM_ADMIN");
    }

    @Test
    @DisplayName("null用户类型 → 返回null")
    void getUserRole_null_shouldReturnNull() {
        assertThat(UserDetailsServiceImpl.getUserRole(null)).isNull();
    }

    @Test
    @DisplayName("构建权限列表 → 包含 ROLE_ 前缀")
    void buildAuthorities_shouldContainRolePrefix() {
        List authorities = UserDetailsServiceImpl.buildAuthorities(0);
        assertThat(authorities)
                .hasSize(1)
                .allMatch(a -> a.toString().equals("ROLE_STUDENT"));
    }

    @Test
    @DisplayName("null类型构建权限 → 返回空列表")
    void buildAuthorities_null_shouldReturnEmpty() {
        List authorities = UserDetailsServiceImpl.buildAuthorities(null);
        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("用户类型名称映射 → 中文正确")
    void getUserTypeName_shouldReturnChineseName() {
        assertThat(UserDetailsServiceImpl.getUserTypeName(0)).isEqualTo("学生");
        assertThat(UserDetailsServiceImpl.getUserTypeName(1)).isEqualTo("教师");
        assertThat(UserDetailsServiceImpl.getUserTypeName(2)).isEqualTo("实验室管理员");
        assertThat(UserDetailsServiceImpl.getUserTypeName(3)).isEqualTo("系统管理员");
        assertThat(UserDetailsServiceImpl.getUserTypeName(null)).isEqualTo("未知");
        assertThat(UserDetailsServiceImpl.getUserTypeName(99)).isEqualTo("未知");
    }
}

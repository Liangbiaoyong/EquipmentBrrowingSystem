package com.gzhu.equipment.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserDetailsServiceImpl 单元测试 — 角色映射与权限构建
 */
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

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

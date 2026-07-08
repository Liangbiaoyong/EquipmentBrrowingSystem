package com.gzhu.equipment.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionConstantsTest {

    @Test @DisplayName("学生 → 8个权限")
    void student_shouldHave8Perms() {
        List<String> perms = PermissionConstants.getPermissionsByUserType(0);
        assertThat(perms).contains(
                "dashboard:view", "device:view",
                "borrow:create", "borrow:my", "borrow:view", "borrow:return")
                .doesNotContain("device:manage", "approval:first", "admin:user");
        assertThat(perms).hasSize(8);
    }

    @Test @DisplayName("教师 → 学生权限+一级审批+统计")
    void teacher_shouldHaveExtraPerms() {
        List<String> perms = PermissionConstants.getPermissionsByUserType(1);
        assertThat(perms).contains("approval:first", "statistics:view");
        assertThat(perms).hasSize(10);
    }

    @Test @DisplayName("实验室管理员 → 11个权限+一级审批=12")
    void labAdmin_shouldHaveManagePerms() {
        List<String> perms = PermissionConstants.getPermissionsByUserType(2);
        assertThat(perms).contains(
                "device:manage", "approval:second",
                "return:manage", "repair:manage",
                "approval:first")
                .doesNotContain("admin:user", "admin:config");
        assertThat(perms).hasSize(12);
    }

    @Test @DisplayName("系统管理员 → 18个权限")
    void systemAdmin_shouldHaveAllPerms() {
        List<String> perms = PermissionConstants.getPermissionsByUserType(3);
        assertThat(perms).contains(
                "admin:user", "admin:config", "admin:log", "admin:backup",
                "device:manage", "approval:first", "approval:second",
                "borrow:create", "borrow:my");
        assertThat(perms).hasSize(18);
    }

    @Test @DisplayName("null类型 → 空权限列表")
    void nullType_shouldReturnEmpty() {
        assertThat(PermissionConstants.getPermissionsByUserType(null)).isEmpty();
    }

    @Test @DisplayName("未知类型 → 空权限列表")
    void unknownType_shouldReturnEmpty() {
        assertThat(PermissionConstants.getPermissionsByUserType(99)).isEmpty();
    }

    @Test @DisplayName("toAuthorityStrings → 与getPermissions一致")
    void toAuthorityStrings_shouldMatch() {
        assertThat(PermissionConstants.toAuthorityStrings(3))
                .containsExactlyElementsOf(PermissionConstants.getPermissionsByUserType(3));
    }
}

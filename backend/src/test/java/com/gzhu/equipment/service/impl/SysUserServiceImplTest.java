package com.gzhu.equipment.service.impl;

import com.gzhu.equipment.entity.SysUser;
import com.gzhu.equipment.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private SysUserServiceImpl sysUserService;

    @BeforeEach
    void setUp() {
        sysUserService = new SysUserServiceImpl(sysUserMapper, passwordEncoder);
    }

    @Test
    @DisplayName("getByUsername → 返回用户")
    void getByUsername_shouldReturnUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("testuser");
        when(sysUserMapper.selectByUsername("testuser")).thenReturn(user);

        SysUser result = sysUserService.getByUsername("testuser");
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("getByUsername（不存在）→ 返回null")
    void getByUsername_notFound_shouldReturnNull() {
        when(sysUserMapper.selectByUsername("nonexist")).thenReturn(null);
        assertThat(sysUserService.getByUsername("nonexist")).isNull();
    }

    @Test
    @DisplayName("getByCasUuid → 返回用户")
    void getByCasUuid_shouldReturnUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setCasUuid("uuid-123");
        when(sysUserMapper.selectByCasUuid("uuid-123")).thenReturn(user);

        assertThat(sysUserService.getByCasUuid("uuid-123")).isNotNull();
    }

    @Test
    @DisplayName("createOrUpdateCasUser → 新用户创建")
    void createOrUpdateCasUser_newUser_shouldInsert() {
        SysUser casUser = new SysUser();
        casUser.setCasUuid("uuid-001");
        casUser.setUsername("newuser");
        casUser.setRealName("新用户");
        casUser.setUserType(0);
        when(sysUserMapper.selectByCasUuid("uuid-001")).thenReturn(null);
        when(sysUserMapper.selectByUsername("newuser")).thenReturn(null);

        SysUser result = sysUserService.createOrUpdateCasUser(casUser);

        verify(sysUserMapper).insert(any(SysUser.class));
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("createOrUpdateCasUser → 已存在用户更新")
    void createOrUpdateCasUser_existingUser_shouldUpdate() {
        SysUser existing = new SysUser();
        existing.setId(1L);
        existing.setUsername("olduser");
        existing.setUserType(0);

        SysUser casUser = new SysUser();
        casUser.setCasUuid("uuid-001");
        casUser.setUsername("olduser");
        casUser.setRealName("更新后的名字");
        casUser.setUserType(0);

        when(sysUserMapper.selectByCasUuid("uuid-001")).thenReturn(existing);

        SysUser result = sysUserService.createOrUpdateCasUser(casUser);

        verify(sysUserMapper, never()).insert(any());
        verify(sysUserMapper).updateById(existing);
        assertThat(result.getRealName()).isEqualTo("更新后的名字");
    }

    @Test
    @DisplayName("createOrUpdateCasUser → 并发插入冲突降级为更新")
    void createOrUpdateCasUser_duplicateKey_shouldFallbackToUpdate() {
        SysUser casUser = new SysUser();
        casUser.setCasUuid("uuid-001");
        casUser.setUsername("newuser");
        casUser.setRealName("新用户");
        casUser.setUserType(0);

        SysUser concurrent = new SysUser();
        concurrent.setId(1L);
        concurrent.setUsername("newuser");
        concurrent.setRealName("旧名字");
        concurrent.setUserType(0);

        when(sysUserMapper.selectByCasUuid("uuid-001")).thenReturn(null, concurrent);
        when(sysUserMapper.selectByUsername("newuser")).thenReturn(null);
        when(sysUserMapper.insert(any(SysUser.class))).thenThrow(new DuplicateKeyException("Duplicate entry"));

        SysUser result = sysUserService.createOrUpdateCasUser(casUser);

        verify(sysUserMapper).insert(any(SysUser.class));
        verify(sysUserMapper, atLeastOnce()).selectByCasUuid("uuid-001");
        verify(sysUserMapper).updateById(concurrent);
        assertThat(result.getRealName()).isEqualTo("新用户");
    }

    @Test
    @DisplayName("createLocalUser → 创建本地用户成功")
    void createLocalUser_shouldSucceed() {
        when(sysUserMapper.selectByUsername("newlocal")).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(sysUserMapper.insert(any(SysUser.class))).thenReturn(1);

        SysUser result = sysUserService.createLocalUser("newlocal", "本地用户", 0,
                "建筑学院", "test@test.com", "13800138000", "password123");

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("newlocal");
        assertThat(result.getAuthSource()).isEqualTo("L");
        assertThat(result.getStatus()).isEqualTo(1);
        verify(sysUserMapper).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("createLocalUser → 用户名太短抛异常")
    void createLocalUser_shortUsername_shouldThrow() {
        assertThatThrownBy(() -> sysUserService.createLocalUser("ab", "用户", 0,
                null, null, null, "password123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户名");
    }

    @Test
    @DisplayName("createLocalUser → 密码太短抛异常")
    void createLocalUser_shortPassword_shouldThrow() {
        assertThatThrownBy(() -> sysUserService.createLocalUser("validuser", "用户", 0,
                null, null, null, "short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密码");
    }

    @Test
    @DisplayName("createLocalUser → 用户名已存在抛异常")
    void createLocalUser_duplicateUsername_shouldThrow() {
        when(sysUserMapper.selectByUsername("existing")).thenReturn(new SysUser());
        assertThatThrownBy(() -> sysUserService.createLocalUser("existing", "已存在", 0,
                null, null, null, "password123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户名已存在");
    }
}

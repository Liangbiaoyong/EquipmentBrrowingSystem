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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SysUserServiceImpl 单元测试
 *
 * 覆盖：CAS用户创建/更新、本地用户创建、查询
 */
@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;

    private PasswordEncoder passwordEncoder;
    private SysUserServiceImpl sysUserService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        sysUserService = new SysUserServiceImpl(sysUserMapper, passwordEncoder);
    }

    // ==================== getByUsername ====================

    @Test
    @DisplayName("按用户名查找 → 返回用户")
    void getByUsername_shouldReturnUser() {
        // given
        SysUser mockUser = createCasUser();
        when(sysUserMapper.selectByUsername("zhangsan")).thenReturn(mockUser);

        // when
        SysUser result = sysUserService.getByUsername("zhangsan");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("zhangsan");
        verify(sysUserMapper).selectByUsername("zhangsan");
    }

    @Test
    @DisplayName("按用户名查找（不存在）→ 返回null")
    void getByUsername_notFound_shouldReturnNull() {
        // given
        when(sysUserMapper.selectByUsername("nobody")).thenReturn(null);

        // when
        SysUser result = sysUserService.getByUsername("nobody");

        // then
        assertThat(result).isNull();
    }

    // ==================== getByCasUuid ====================

    @Test
    @DisplayName("按CAS UUID查找 → 返回用户")
    void getByCasUuid_shouldReturnUser() {
        // given
        SysUser mockUser = createCasUser();
        when(sysUserMapper.selectByCasUuid("uuid-123")).thenReturn(mockUser);

        // when
        SysUser result = sysUserService.getByCasUuid("uuid-123");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getCasUuid()).isEqualTo("uuid-123");
    }

    // ==================== createOrUpdateCasUser（新建）====================

    @Test
    @DisplayName("CAS新用户 → 创建并返回")
    void createOrUpdateCasUser_newUser_shouldInsert() {
        // given
        SysUser newUser = createCasUser();
        when(sysUserMapper.selectByCasUuid(anyString())).thenReturn(null);
        when(sysUserMapper.selectByUsername(anyString())).thenReturn(null);

        // when
        SysUser result = sysUserService.createOrUpdateCasUser(newUser);

        // then
        assertThat(result.getAuthSource()).isEqualTo("C");
        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getLastCasLogin()).isNotNull();
        verify(sysUserMapper).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("CAS新用户（无UUID但有用户名）→ 走username查找并更新")
    void createOrUpdateCasUser_noUuid_shouldFindByUsername() {
        // given
        SysUser casUser = createCasUser();
        casUser.setCasUuid(null); // 无UUID

        SysUser existingUser = createCasUser();
        // casUuid=null 时跳过 selectByCasUuid，直接走 selectByUsername
        when(sysUserMapper.selectByUsername("zhangsan")).thenReturn(existingUser);

        // when
        sysUserService.createOrUpdateCasUser(casUser);

        // then
        verify(sysUserMapper).updateById(any(SysUser.class));
    }

    // ==================== createOrUpdateCasUser（更新）====================

    @Test
    @DisplayName("CAS已存在用户 → 更新信息并返回")
    void createOrUpdateCasUser_existingUser_shouldUpdate() {
        // given
        SysUser casUser = createCasUser();
        casUser.setRealName("张三（更新）");
        casUser.setPhone("13800000001");

        SysUser existingUser = createCasUser();
        when(sysUserMapper.selectByCasUuid("uuid-123")).thenReturn(existingUser);

        // when
        SysUser result = sysUserService.createOrUpdateCasUser(casUser);

        // then
        assertThat(result.getRealName()).isEqualTo("张三（更新）");
        assertThat(result.getPhone()).isEqualTo("13800000001");
        assertThat(result.getAuthSource()).isEqualTo("C");
        verify(sysUserMapper).updateById(result);
    }

    @Test
    @DisplayName("CAS用户更新 → 升级为管理员后不会降级")
    void createOrUpdateCasUser_promotedToAdmin_shouldNotDowngrade() {
        // given
        SysUser casUser = createCasUser();  // userType=0 学生
        SysUser existingUser = createCasUser();
        existingUser.setUserType(2); // 已经被手动升级为实验室管理员

        when(sysUserMapper.selectByCasUuid("uuid-123")).thenReturn(existingUser);

        // when
        SysUser result = sysUserService.createOrUpdateCasUser(casUser);

        // then
        assertThat(result.getUserType()).isEqualTo(2); // 保持管理员身份
    }

    // ==================== createLocalUser ====================

    @Test
    @DisplayName("创建本地用户 → username+password+auth_source=L")
    void createLocalUser_shouldCreateLocalUser() {
        // given
        when(sysUserMapper.selectByUsername("newadmin")).thenReturn(null);

        // when
        SysUser result = sysUserService.createLocalUser(
                "newadmin", "新管理员", 3,
                "信息中心", "admin@gzhu.edu.cn", "13800138000", "admin123");

        // then
        assertThat(result.getUsername()).isEqualTo("newadmin");
        assertThat(result.getAuthSource()).isEqualTo("L");
        assertThat(result.getPassword()).isNotEqualTo("admin123"); // 加密
        assertThat(result.getStatus()).isEqualTo(1);
        verify(sysUserMapper).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("创建本地用户（用户名重复）→ 抛出异常")
    void createLocalUser_duplicateUsername_shouldThrow() {
        // given
        when(sysUserMapper.selectByUsername("admin")).thenReturn(createCasUser());

        // when & then
        assertThatThrownBy(() ->
                sysUserService.createLocalUser("admin", "admin", 3, null, null, null, "pass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户名已存在");
    }

    @Test
    @DisplayName("CAS并发插入 → DuplicateKeyException降级为更新")
    void createOrUpdateCasUser_concurrentInsert_shouldFallbackToUpdate() {
        // given: 第一次查询未找到，insert抛出DuplicateKeyException
        SysUser newUser = createCasUser();
        when(sysUserMapper.selectByCasUuid(anyString())).thenReturn(null);
        when(sysUserMapper.selectByUsername(anyString())).thenReturn(null);
        doThrow(new DuplicateKeyException("Duplicate entry")).when(sysUserMapper).insert(any(SysUser.class));

        // 并发场景：另一线程已插入，重新查询能查到
        SysUser concurrent = createCasUser();
        when(sysUserMapper.selectByCasUuid("uuid-123")).thenReturn(concurrent);

        // when
        SysUser result = sysUserService.createOrUpdateCasUser(newUser);

        // then: 降级为update，不抛异常
        assertThat(result).isNotNull();
        verify(sysUserMapper).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("创建本地用户（短密码）→ 抛出异常")
    void createLocalUser_shortPassword_shouldThrow() {
        assertThatThrownBy(() ->
                sysUserService.createLocalUser("u1", "Name", 3, null, null, null, "123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少为8位");
    }

    @Test
    @DisplayName("创建本地用户（短用户名）→ 抛出异常")
    void createLocalUser_shortUsername_shouldThrow() {
        assertThatThrownBy(() ->
                sysUserService.createLocalUser("ab", "Name", 3, null, null, null, "password123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要3个字符");
    }

    // ==================== 辅助方法 ====================

    private SysUser createCasUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("zhangsan");
        user.setRealName("张三");
        user.setUserType(0);
        user.setDepartment("建筑学院");
        user.setClassName("建筑学191");
        user.setCasUuid("uuid-123");
        user.setAuthSource("C");
        user.setStatus(1);
        user.setIdent(257);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        return user;
    }
}

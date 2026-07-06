package com.gzhu.equipment.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzhu.equipment.entity.SysUser;
import com.gzhu.equipment.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户详情加载服务 — 用于本地账户登录认证
 *
 * CAS用户不经过此服务（无密码），仅 auth_source=L 的本地账户走此流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new UsernameNotFoundException("用户已被禁用: " + username);
        }
        if (!"L".equals(user.getAuthSource())) {
            throw new UsernameNotFoundException("CAS用户不支持本地密码登录，请使用CAS统一认证");
        }

        List<SimpleGrantedAuthority> authorities = buildAuthorities(user.getUserType());

        return new User(
                user.getUsername(),
                StringUtils.hasText(user.getPassword()) ? user.getPassword() : "",
                authorities
        );
    }

    /**
     * 根据用户类型构建权限列表
     */
    public static List<SimpleGrantedAuthority> buildAuthorities(Integer userType) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        String role = getUserRole(userType);
        if (role != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return authorities;
    }

    /**
     * 用户类型 → 角色名映射
     */
    public static String getUserRole(Integer userType) {
        if (userType == null) return null;
        switch (userType) {
            case 0: return "STUDENT";
            case 1: return "TEACHER";
            case 2: return "LAB_ADMIN";
            case 3: return "SYSTEM_ADMIN";
            default: return null;
        }
    }

    /**
     * 用户类型 → 中文名称映射
     */
    public static String getUserTypeName(Integer userType) {
        if (userType == null) return "未知";
        switch (userType) {
            case 0: return "学生";
            case 1: return "教师";
            case 2: return "实验室管理员";
            case 3: return "系统管理员";
            default: return "未知";
        }
    }
}

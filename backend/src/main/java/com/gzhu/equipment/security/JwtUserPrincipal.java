package com.gzhu.equipment.security;

import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;

/**
 * JWT 认证主体 — 替代 Long 作为 Authentication.principal
 *
 * 包含从JWT解析出的用户核心信息，符合 Spring Security UserDetails 惯例，
 * 使 @AuthenticationPrincipal 和 PermissionEvaluator 可正常工作。
 */
@Getter
public class JwtUserPrincipal {

    private final Long userId;
    private final String username;
    private final Integer userType;
    private final List<String> roles;
    private final List<SimpleGrantedAuthority> authorities;

    public JwtUserPrincipal(Long userId, String username, Integer userType,
                            List<String> roles, List<SimpleGrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.userType = userType;
        this.roles = roles != null ? Collections.unmodifiableList(roles) : Collections.emptyList();
        this.authorities = authorities != null ? Collections.unmodifiableList(authorities) : Collections.emptyList();
    }

    @Override
    public String toString() {
        return "JwtUserPrincipal{userId=" + userId + ", username='" + username + "'}";
    }
}

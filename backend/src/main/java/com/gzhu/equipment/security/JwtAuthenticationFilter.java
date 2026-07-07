package com.gzhu.equipment.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器 — 每次请求拦截，从请求头提取JWT并设置认证上下文
 *
 * 认证流程：
 * 1. 从 Authorization 头部提取 Bearer token
 * 2. 验证token有效性
 * 3. 从token解析用户ID、用户名、角色
 * 4. 构造 Authentication 对象存入 SecurityContext
 *
 * 注意：此过滤器不查数据库，所有用户信息均来自JWT payload（无状态设计）
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TokenBlacklist tokenBlacklist;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token) && !tokenBlacklist.isBlacklisted(token) && jwtTokenProvider.validateToken(token)) {
            try {
                Long userId = jwtTokenProvider.getUserId(token);
                String username = jwtTokenProvider.getUsername(token);
                List<String> roles = jwtTokenProvider.getRoles(token);

                // ROLE_ 前缀的权限（兼容 hasRole）
                List<SimpleGrantedAuthority> authorities = roles != null
                        ? roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList())
                        : new java.util.ArrayList<>();
                // module:action 细粒度权限（用于 hasAuthority）
                Integer userType = jwtTokenProvider.getUserType(token);
                PermissionConstants.getPermissionsByUserType(userType).stream()
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);

                // 使用 JwtUserPrincipal 作为 principal，符合 Spring Security 惯例
                // credentials 不存储原始 JWT，避免日志/调试时泄露
                JwtUserPrincipal principal = new JwtUserPrincipal(
                        userId, username, jwtTokenProvider.getUserType(token), roles, authorities);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                log.warn("JWT解析异常: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头提取 Bearer token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}

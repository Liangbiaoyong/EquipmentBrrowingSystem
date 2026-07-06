package com.gzhu.equipment.config;

import com.gzhu.equipment.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置 — JWT无状态认证 + 方法级鉴权
 *
 * 认证路径：
 * - /auth/**                  → 公开（CAS登录、本地登录、健康检查）
 * - /doc.html, /swagger-*, /webjars/**
 *                              → 公开（API文档）
 * - /actuator/**              → 公开（运维监控）
 * - 其余所有接口              → 需认证（JWT Bearer Token）
 *
 * 鉴权方案：
 * - 方法级 @PreAuthorize 注解（推荐）
 * - 角色: ROLE_STUDENT, ROLE_TEACHER, ROLE_LAB_ADMIN, ROLE_SYSTEM_ADMIN
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 暴露 AuthenticationManager Bean，供 AuthController 中本地登录使用
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF（API无状态，不依赖Cookie）
            .csrf().disable()
            // 无状态会话
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            // 请求授权
            .authorizeRequests()
            // --- 公开端点 ---
            .antMatchers("/auth/**").permitAll()
            // API文档
            .antMatchers(
                "/doc.html",
                "/swagger-resources/**",
                "/swagger-ui/**",
                "/webjars/**",
                "/v2/api-docs",
                "/v3/api-docs/**"
            ).permitAll()
            // 运维监控（生产环境建议限制IP）
            .antMatchers("/actuator/**").permitAll()
            // 静态资源
            .antMatchers(HttpMethod.GET,
                "/", "/favicon.ico", "/error"
            ).permitAll()
            // --- 其余需认证 ---
            .anyRequest().authenticated()
            .and()
            // 禁用默认登录方式
            .httpBasic().disable()
            .formLogin().disable()
            .logout().disable();

        // 在 UsernamePasswordAuthenticationFilter 之前插入JWT过滤器
        http.addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

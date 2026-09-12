package com.gzhu.equipment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * 跨域配置
 */
@Configuration
public class CorsConfig {

    /** 允许的跨域来源，逗号分隔；生产可用 cors.allowed-origins / CORS_ALLOWED_ORIGINS 覆盖 */
    @Value("${cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://gzhu-server.ydns.eu,https://gzhu-server.ydns.eu}")
    private String[] allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(Arrays.asList(allowedOrigins));
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}

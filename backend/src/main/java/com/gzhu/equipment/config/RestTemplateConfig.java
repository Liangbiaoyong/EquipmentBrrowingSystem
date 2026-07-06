package com.gzhu.equipment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置 — 用于调用外部HTTP API（CAS用户信息验证）
 */
@Configuration
public class RestTemplateConfig {

    @Value("${cas.request-timeout:15000}")
    private int casRequestTimeout;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(casRequestTimeout);
        factory.setReadTimeout(casRequestTimeout);
        return new RestTemplate(factory);
    }
}

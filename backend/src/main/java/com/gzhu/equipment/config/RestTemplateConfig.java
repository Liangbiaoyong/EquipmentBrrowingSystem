package com.gzhu.equipment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.net.HttpURLConnection;
import java.security.cert.X509Certificate;

/**
 * RestTemplate 配置 — 用于调用CAS用户信息API（广州大学内网HTTPS）
 *
 * 注意：学校内部服务器 libbooking.gzhu.edu.cn 使用自签名/内部CA证书，
 * 不走标准SSL验证，仅做主机名验证放宽处理。
 */
@Configuration
public class RestTemplateConfig {

    @Value("${cas.request-timeout:15000}")
    private int casRequestTimeout;

    static {
        // 信任所有SSL证书（仅用于学校内网CAS API调用）
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            throw new RuntimeException("SSL初始化失败", e);
        }
    }

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(casRequestTimeout);
        factory.setReadTimeout(casRequestTimeout);
        return new RestTemplate(factory);
    }
}

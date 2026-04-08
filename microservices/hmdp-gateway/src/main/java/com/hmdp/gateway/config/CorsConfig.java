package com.hmdp.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * 跨域配置
 * 在网关层统一处理跨域问题，避免在每个微服务中重复配置
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许所有域名跨域（生产环境建议指定具体域名）
        config.addAllowedOriginPattern("*");
        
        // 允许的请求方法
        config.addAllowedMethod("*");
        
        // 允许的请求头
        config.addAllowedHeader("*");
        
        // 允许携带认证信息（cookies、authorization headers等）
        config.setAllowCredentials(true);
        
        // 预检请求的有效期（秒）
        config.setMaxAge(3600L);
        
        // 暴露的响应头
        config.addExposedHeader("Authorization");
        config.addExposedHeader("Content-Type");
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有路径应用跨域配置
        source.registerCorsConfiguration("/**", config);
        
        return new CorsWebFilter(source);
    }
}

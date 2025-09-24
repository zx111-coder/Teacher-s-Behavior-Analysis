package com.example.demo.config;

import org.springframework.context.annotation.Configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

// 处理跨域访问限制
@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 跨域规则
        config.addAllowedOriginPattern("*"); // 允许所有域名进行跨域调用
        config.addAllowedHeader("*"); // 允许请求携带任何头信息
        config.addAllowedMethod("*"); // 允许任何http方法（POST、GET等）
        config.setAllowCredentials(true); // 允许携带认证凭证（如 Cookie）

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // 对所有API接口都有效

        return new CorsFilter(source);
    }
}
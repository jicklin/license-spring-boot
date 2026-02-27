package com.example.license.server;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 注册管理 API 鉴权拦截器，区分管理类和客户端类 API
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;

    public WebMvcConfig(AdminAuthInterceptor adminAuthInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns(
                        "/api/license/generate",   // 签发授权码
                        "/api/license/list",       // 授权码列表
                        "/api/license/**",         // 删除等管理操作
                        "/api/node/online",        // 在线节点列表
                        "/api/node/stats"          // 统计数据
                )
                .excludePathPatterns(
                        "/api/license/publicKey",  // 公钥（公开）
                        "/api/node/register",      // 客户端注册
                        "/api/node/heartbeat",     // 客户端心跳
                        "/api/node/unregister"     // 客户端注销
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}

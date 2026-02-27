package com.example.license.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理 API 鉴权拦截器
 * 校验请求头 Authorization: Bearer <admin-token>
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthInterceptor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${license.admin-token:}")
    private String adminToken;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // 如果未配置 admin-token，跳过鉴权（向后兼容，方便本地开发）
        if (adminToken == null || adminToken.isEmpty()) {
            return true;
        }

        // OPTIONS 预检请求放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 校验 Authorization 头
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            if (adminToken.equals(token)) {
                return true;
            }
        }

        // 鉴权失败
        log.warn("管理 API 鉴权失败: {} {}, remoteAddr={}", request.getMethod(), request.getRequestURI(),
                request.getRemoteAddr());

        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new HashMap<>();
        body.put("code", 401);
        body.put("message", "未授权访问，请提供有效的管理员 Token");
        response.getWriter().write(objectMapper.writeValueAsString(body));
        return false;
    }
}

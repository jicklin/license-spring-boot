package com.example.license.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * License 校验 Servlet Filter
 * 每个请求到达时检查 LicenseContext 是否有效
 */
public class LicenseCheckFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(LicenseCheckFilter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private List<String> excludePatterns;

    public LicenseCheckFilter(String excludePaths) {
        if (excludePaths != null && !excludePaths.isEmpty()) {
            this.excludePatterns = Arrays.asList(excludePaths.split(","));
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // 排除路径检查
        if (isExcluded(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 检查授权状态
        if (!LicenseContext.isValid()) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(403);
            httpResponse.setContentType("application/json;charset=UTF-8");
            String body = objectMapper.writeValueAsString(
                    com.example.license.common.Result.fail(403, "License 授权无效: " + LicenseContext.getMessage()));
            httpResponse.getWriter().write(body);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 检查路径是否在排除列表中
     */
    private boolean isExcluded(String path) {
        if (excludePatterns == null) {
            return false;
        }
        for (String pattern : excludePatterns) {
            String p = pattern.trim();
            if (p.endsWith("/**")) {
                String prefix = p.substring(0, p.length() - 3);
                if (path.startsWith(prefix)) {
                    return true;
                }
            } else if (p.equals(path)) {
                return true;
            }
        }
        return false;
    }
}

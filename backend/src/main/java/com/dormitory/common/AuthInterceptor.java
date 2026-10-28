package com.dormitory.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 统一请求认证与日志审计拦截器
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("Authorization");
        request.setAttribute("requestStartTime", System.currentTimeMillis());
        if (token != null && token.startsWith("SMART-DORM-TOKEN-")) {
            request.setAttribute("authenticatedToken", token);
            try {
                String[] parts = token.split("-");
                if (parts.length >= 4) {
                    Long userId = Long.parseLong(parts[3]);
                    request.setAttribute("currentUserId", userId);
                }
            } catch (Exception ignored) {
            }
        }
        return true;
    }
}

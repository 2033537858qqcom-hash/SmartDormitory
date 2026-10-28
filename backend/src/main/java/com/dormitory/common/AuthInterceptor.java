package com.dormitory.common;

import com.dormitory.entity.SysUser;
import com.dormitory.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 统一请求认证与日志审计拦截器
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final SessionService sessions;
    private final SysUserMapper users;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("Authorization");
        Long userId = sessions.getUserId(token);
        SysUser user = userId == null ? null : users.selectById(userId);
        if (user == null || Integer.valueOf(0).equals(user.getStatus())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"请登录后重试\"}");
            return false;
        }
        request.setAttribute("currentUserId", userId);
        request.setAttribute("currentUserRole", user.getRole());
        request.setAttribute("authenticatedToken", token);
        return true;
    }
}

package com.dormitory.common;

import com.dormitory.entity.SysUser;
import com.dormitory.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.Set;

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
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!allowed(user.getRole(), userId, request.getMethod(), path)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"没有权限执行此操作\"}");
            return false;
        }
        request.setAttribute("currentUserId", userId);
        request.setAttribute("currentUserRole", user.getRole());
        request.setAttribute("authenticatedToken", token);
        return true;
    }

    private boolean allowed(String role, Long userId, String method, String path) {
        if ("ADMIN".equals(role)) return true;
        if (!Set.of("STUDENT", "DORM_MANAGER", "REPAIRMAN").contains(role == null ? "" : role)) return false;
        if ("POST".equals(method) && Set.of("/api/auth/logout", "/api/auth/change-password").contains(path)) return true;
        // 站内消息：登录用户均可读写自己的
        if (path.equals("/api/messages") || path.equals("/api/messages/unread-count") || path.equals("/api/messages/read")) {
            return ("GET".equals(method) && !path.equals("/api/messages/read"))
                    || ("POST".equals(method) && path.equals("/api/messages/read"))
                    || ("GET".equals(method) && path.equals("/api/messages/unread-count"))
                    || ("GET".equals(method) && path.equals("/api/messages"));
        }
        if ("STUDENT".equals(role)) {
            if ("GET".equals(method)) {
                return path.equals("/api/auth/student-dashboard/" + userId)
                        || Set.of("/api/dorm/buildings", "/api/dorm/rooms", "/api/affair/notices",
                        "/api/messages", "/api/messages/unread-count").contains(path);
            }
            return "POST".equals(method) && Set.of("/api/smart-alloc/habit-profile", "/api/repair/submit",
                    "/api/repair/rate", "/api/utility/pay-or-recharge",
                    "/api/affair/discipline-appeal", "/api/affair/adjust-apply", "/api/affair/visitor",
                    "/api/upload/image", "/api/messages/read").contains(path);
        }
        if ("REPAIRMAN".equals(role)) {
            return ("GET".equals(method) && (path.equals("/api/repair/list")
                    || path.equals("/api/messages") || path.equals("/api/messages/unread-count")))
                    || ("POST".equals(method) && Set.of("/api/repair/process", "/api/messages/read", "/api/upload/image").contains(path));
        }
        // DORM_MANAGER
        if ("GET".equals(method)) {
            return Set.of("/api/dashboard/overview", "/api/dorm/buildings", "/api/dorm/rooms",
                    "/api/smart-alloc/candidates", "/api/smart-alloc/precheck", "/api/repair/list", "/api/utility/bills",
                    "/api/utility/threshold-rules", "/api/affair/evaluation", "/api/affair/hygiene", "/api/affair/adjust-list",
                    "/api/affair/discipline", "/api/affair/visitors", "/api/affair/curfew-stats",
                    "/api/affair/notices", "/api/auth/users",
                    "/api/messages", "/api/messages/unread-count").contains(path);
        }
        return "POST".equals(method) && (Set.of("/api/dorm/room", "/api/dorm/assign-bed",
                "/api/repair/submit", "/api/repair/process", "/api/repair/reassign", "/api/repair/cancel",
                "/api/utility/meter-entry", "/api/utility/meter-batch", "/api/utility/anomaly-to-repair",
                "/api/utility/pay-or-recharge", "/api/affair/curfew-check", "/api/affair/discipline",
                "/api/affair/discipline-review", "/api/affair/visitor", "/api/affair/visitor-verify",
                "/api/affair/hygiene", "/api/affair/adjust-review",
                "/api/upload/image", "/api/messages/read",
                "/api/auth/import-students").contains(path) || path.matches("/api/affair/visitor-leave/[0-9]+"));
    }
}

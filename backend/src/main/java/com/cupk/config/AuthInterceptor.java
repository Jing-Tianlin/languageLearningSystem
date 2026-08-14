package com.cupk.config;

import com.cupk.pojo.User;
import com.cupk.util.AuthUtil;
import com.cupk.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    private final AuthCacheService authCacheService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // CORS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // 公开路径（登录/注册/内容浏览/API 文档/健康检查）无需鉴权
        if (isPublicPath(path, method)) {
            return true;
        }

        // 其余路径统一要求登录（token 仅接受 Authorization 头，不接受 query 参数，避免泄漏）
        JwtUtil.TokenPayload payload = getTokenPayload(request);
        if (payload == null) {
            return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录");
        }
        Long userId = payload.userId();

        // 用户信息 + 角色走本地缓存（60s TTL），避免每个请求查两次库
        AuthCacheService.AuthEntry entry = authCacheService.get(userId);
        if (entry == null || entry.user() == null) {
            return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "用户不存在");
        }
        User user = entry.user();
        List<String> roles = entry.roles();

        // 账号被禁用时立即拒绝（不依赖 token 过期）
        if (user.getStatus() != null && user.getStatus() != 1) {
            return writeJson(response, HttpServletResponse.SC_FORBIDDEN, "账号已被禁用");
        }

        // 密码被重置后，旧 token 立即失效（token 签发时间早于密码修改时间）
        if (user.getLastPasswordChangeAt() != null
                && payload.issuedAt() < user.getLastPasswordChangeAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) {
            return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "密码已修改，请重新登录");
        }

        boolean isAdmin = roles.contains("ROLE_ADMIN");

        // /admin/** 仅管理员可访问
        if (path.startsWith("/admin")) {
            if (!isAdmin) {
                return writeJson(response, HttpServletResponse.SC_FORBIDDEN, "无管理员权限");
            }
        }

        // 语种/课程等内容的增删改属于管理功能，仅管理员可操作
        if (!isAdmin && isAdminWritePath(path, method)) {
            return writeJson(response, HttpServletResponse.SC_FORBIDDEN, "无管理员权限");
        }

        request.setAttribute(AuthUtil.ATTR_USER_ID, userId);
        request.setAttribute(AuthUtil.ATTR_USER_ROLES, roles);
        return true;
    }

    /** 无需登录的公开路径 */
    private boolean isPublicPath(String path, String method) {
        if ("GET".equalsIgnoreCase(method)) {
            return path.startsWith("/language/")
                || path.startsWith("/course/")
                || path.startsWith("/vocabulary/vocabularies")
                || path.startsWith("/sentences/")
                || path.equals("/grammar/lessons")
                || path.equals("/grammar/practices")
                || path.equals("/reading/articles")
                || path.equals("/reading/level-stats")
                || path.equals("/writing/prompt")
                || path.equals("/writing/prompts")
                // API 文档与健康检查（仅 health/info 对外暴露）
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.equals("/actuator/health")
                || path.equals("/actuator/info");
        }
        // POST 仅放行登录与注册
        if ("POST".equalsIgnoreCase(method)) {
            return path.equals("/user/login") || path.equals("/user/register");
        }
        return false;
    }

    /** 管理写操作：语种/课程模块的增删改 */
    private boolean isAdminWritePath(String path, String method) {
        if (!isWriteMethod(method)) {
            return false;
        }
        return path.startsWith("/language/") || path.startsWith("/course/");
    }

    private boolean isWriteMethod(String method) {
        return "POST".equalsIgnoreCase(method)
            || "PUT".equalsIgnoreCase(method)
            || "DELETE".equalsIgnoreCase(method);
    }

    private boolean writeJson(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\"}");
        return false;
    }

    private JwtUtil.TokenPayload getTokenPayload(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isEmpty()) {
            return null;
        }
        return jwtUtil.parseToken(token);
    }
}

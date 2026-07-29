package com.cupk.config;

import com.cupk.mapper.UserMapper;
import com.cupk.pojo.User;
import com.cupk.util.AuthUtil;
import com.cupk.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private final UserMapper userMapper;

    private final JdbcTemplate jdbcTemplate;

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // CORS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // 公开路径（登录/注册/内容浏览）无需鉴权
        if (isPublicPath(path, method)) {
            return true;
        }

        // 其余路径统一要求登录
        String userIdStr = getUserIdFromToken(request);
        if (userIdStr == null) {
            return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录");
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "无效的token");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "用户不存在");
        }

        List<String> roles = queryRoles(userId);
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
                || path.equals("/writing/prompts");
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

    private List<String> queryRoles(Long userId) {
        return jdbcTemplate.query(
            "SELECT r.code FROM role r JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?",
            (rs, rowNum) -> rs.getString("code"),
            userId
        );
    }

    private boolean writeJson(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\"}");
        return false;
    }

    private String getUserIdFromToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isEmpty()) {
            token = request.getParameter("token");
        }
        if (token == null || token.isEmpty()) {
            return null;
        }
        long userId = jwtUtil.parseToken(token);
        return userId > 0 ? String.valueOf(userId) : null;
    }
}

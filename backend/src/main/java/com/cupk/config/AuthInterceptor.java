package com.cupk.config;

import com.cupk.mapper.UserMapper;
import com.cupk.pojo.User;
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
        
        if (path.startsWith("/admin/")) {
            String userIdStr = getUserIdFromToken(request);
            if (userIdStr == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
                return false;
            }
            
            try {
                Long userId = Long.parseLong(userIdStr);
                User user = userMapper.selectById(userId);
                if (user == null) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"用户不存在\"}");
                    return false;
                }
                
                List<String> roles = jdbcTemplate.query(
                    "SELECT r.code FROM role r JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?",
                    (rs, rowNum) -> rs.getString("code"),
                    userId
                );
                
                boolean isAdmin = roles.contains("ROLE_ADMIN");
                if (!isAdmin) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"无管理员权限\"}");
                    return false;
                }
                
                request.setAttribute("currentUserId", userId);
                request.setAttribute("currentUserRoles", roles);
                return true;
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"无效的token\"}");
                return false;
            }
        }
        
        return true;
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
        // 兼容旧版 "token-N" 格式（升级后可移除）
        if (token.startsWith("token-")) {
            return token.substring(6);
        }
        long userId = jwtUtil.parseToken(token);
        return userId > 0 ? String.valueOf(userId) : null;
    }
}

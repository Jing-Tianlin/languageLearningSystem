package com.cupk.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * AuthUtil — 从当前请求上下文中获取登录用户信息
 * 由 AuthInterceptor 在 preHandle 阶段解析 token 后写入 request attribute。
 */
public class AuthUtil {

    public static final String ATTR_USER_ID = "currentUserId";
    public static final String ATTR_USER_ROLES = "currentUserRoles";

    private AuthUtil() {
    }

    /** 当前登录用户 ID；未登录返回 null */
    public static Long getCurrentUserId() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(ATTR_USER_ID);
        return value instanceof Long ? (Long) value : null;
    }

    /** 当前用户是否拥有指定角色 */
    public static boolean hasRole(String role) {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return false;
        }
        Object value = request.getAttribute(ATTR_USER_ROLES);
        return value instanceof List<?> roles && roles.contains(role);
    }

    private static HttpServletRequest getRequest() {
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }
}

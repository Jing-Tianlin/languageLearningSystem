package com.cupk.config;

import com.cupk.util.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * AI 接口限流拦截器：在 AuthInterceptor 之后执行（注册顺序保证），
 * 读取已登录用户 ID 并消耗其 AI 配额，超限返回 429。
 */
@Component
@RequiredArgsConstructor
public class AiQuotaInterceptor implements HandlerInterceptor {

    private final AiQuotaService quotaService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!quotaService.isEnabled()) {
            return true;
        }
        Long userId = (Long) request.getAttribute(AuthUtil.ATTR_USER_ID);
        // 未登录请求由 AuthInterceptor 拦截，这里仅兜底放行
        if (userId == null) {
            return true;
        }
        String rejectReason = quotaService.tryAcquire(userId);
        if (rejectReason != null) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"" + rejectReason + "\"}");
            return false;
        }
        return true;
    }
}

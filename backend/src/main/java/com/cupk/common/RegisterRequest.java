package com.cupk.common;

/**
 * 用户注册请求 DTO —— 白名单字段，杜绝 mass-assignment
 * （不接收 status / points / role / isDeleted 等敏感字段）。
 */
public record RegisterRequest(
        String username,
        String password,
        String nickname,
        String email,
        String phone
) {}

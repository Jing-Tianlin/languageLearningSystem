package com.cupk.common;

/**
 * 管理员建户请求 DTO —— 仅允许显式列出的字段，防止越权写入用户表其它列。
 */
public record AdminCreateUserRequest(
        String username,
        String password,
        String nickname,
        String email,
        String phone,
        Integer gender,
        String avatar,
        String currentLangCode,
        String currentLevel,
        Integer status
) {}

package com.cupk.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 密码工具类（BCrypt 强化版）
 *
 * 新版密码使用 BCrypt（强度 10，自动加盐）存储，密文以 "$2" 开头。
 * 旧版密码为 "盐$SHA256" 格式，校验仍然兼容；用户成功登录后由调用方自动升级为 BCrypt。
 */
public final class PasswordUtil {

    /** BCrypt 密码最大字节数（72 字节，超出部分会被截断） */
    private static final int MAX_PASSWORD_BYTES = 72;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtil() {}

    /** 加密密码（BCrypt，自动加盐） */
    public static String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("密码过长（最多 72 字节）");
        }
        return ENCODER.encode(rawPassword);
    }

    /** 校验密码：兼容 BCrypt 与旧版 "盐$SHA256" 格式 */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }
        if (encodedPassword.startsWith("$2")) {
            try {
                return ENCODER.matches(rawPassword, encodedPassword);
            } catch (IllegalArgumentException e) {
                // BCrypt 密文格式损坏时视为不匹配，而不是抛出异常
                return false;
            }
        }
        return legacyMatches(rawPassword, encodedPassword);
    }

    /** 是否需要重新哈希升级（非 BCrypt 格式的存量密码需要升级） */
    public static boolean needsRehash(String encodedPassword) {
        return encodedPassword == null || !encodedPassword.startsWith("$2");
    }

    /** 旧版 "盐$SHA256" 校验，仅用于存量数据兼容 */
    private static boolean legacyMatches(String rawPassword, String encodedPassword) {
        int idx = encodedPassword.indexOf('$');
        if (idx <= 0) {
            // 历史遗留的明文存储（理论上不存在），保持原行为直接比较
            return encodedPassword.equals(rawPassword);
        }
        String salt = encodedPassword.substring(0, idx);
        String hash = encodedPassword.substring(idx + 1);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashed = md.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(hashed);
            return computed.equals(hash);
        } catch (Exception e) {
            return false;
        }
    }
}

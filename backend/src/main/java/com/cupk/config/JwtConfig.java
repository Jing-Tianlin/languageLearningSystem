package com.cupk.config;

import com.cupk.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

@Configuration
public class JwtConfig {

    /** 不提供默认值：未配置 JWT_SECRET 时启动直接失败，避免生产环境使用公开密钥 */
    @Value("${jwt.secret:}")
    private String secret;

    @Bean
    public JwtUtil jwtUtil() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "未配置 JWT 密钥！请设置环境变量 JWT_SECRET（建议至少 32 字节随机字符串）。" +
                "生成示例: openssl rand -base64 48");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                "JWT 密钥过短（当前 " + secret.getBytes(StandardCharsets.UTF_8).length + " 字节，至少 32 字节），请更换更强的 JWT_SECRET");
        }
        return new JwtUtil(secret);
    }
}

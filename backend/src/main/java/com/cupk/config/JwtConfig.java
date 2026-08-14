package com.cupk.config;

import com.cupk.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    /** 不提供硬编码默认值；未配置时启动时临时随机生成（仅限本地开发），生产请设置环境变量 */
    @Value("${jwt.secret:}")
    private String secret;

    @Bean
    public JwtUtil jwtUtil() {
        if (secret == null || secret.isBlank()) {
            String generated = generateRandomSecret();
            log.warn("未配置 JWT_SECRET，已临时生成随机密钥（仅限本地开发，重启后失效）。生产环境请务必设置环境变量 JWT_SECRET。");
            return new JwtUtil(generated);
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            log.warn("JWT_SECRET 过短（{} 字节），建议至少 32 字节；本次仍继续使用。",
                secret.getBytes(StandardCharsets.UTF_8).length);
        }
        return new JwtUtil(secret);
    }

    private String generateRandomSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

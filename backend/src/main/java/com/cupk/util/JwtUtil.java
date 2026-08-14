package com.cupk.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 简易 HMAC-SHA256 签名令牌（不依赖第三方库，仅用 JDK 内置加密）。
 * 载荷格式: userId:issuedAt:expiresAt
 */
public final class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final long TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L; // 7 天

    private final byte[] secretBytes;

    /** 解析后的令牌载荷 */
    public record TokenPayload(long userId, long issuedAt, long expiresAt) {}

    public JwtUtil(String secret) {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** 生成令牌：base64(payload.base64(signature)) */
    public String generateToken(long userId) {
        long iat = System.currentTimeMillis();
        long exp = iat + TTL_MILLIS;
        String payload = userId + ":" + iat + ":" + exp;
        String sig = sign(payload);
        String token = payload + "." + sig;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    /** 解析令牌，校验签名和过期时间；无效返回 null */
    public TokenPayload parseToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int dotIdx = decoded.lastIndexOf('.');
            if (dotIdx <= 0) {
                return null;
            }
            String payload = decoded.substring(0, dotIdx);
            String sig = decoded.substring(dotIdx + 1);

            if (!sign(payload).equals(sig)) {
                log.warn("JWT 签名校验失败");
                return null;
            }

            String[] parts = payload.split(":");
            if (parts.length < 3) {
                log.warn("JWT 载荷格式过旧，请重新登录");
                return null;
            }
            long userId = Long.parseLong(parts[0]);
            long iat = Long.parseLong(parts[1]);
            long exp = Long.parseLong(parts[2]);
            if (System.currentTimeMillis() > exp) {
                log.warn("JWT 已过期 userId={}", userId);
                return null;
            }
            return new TokenPayload(userId, iat, exp);
        } catch (Exception e) {
            log.warn("JWT 解析失败", e);
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGO));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC 签名失败", e);
        }
    }
}

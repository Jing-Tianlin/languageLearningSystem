package com.cupk.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 简易 HMAC-SHA256 签名令牌（不依赖第三方库，仅用 JDK 内置加密）
 */
public final class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final long TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L; // 7 天

    private final byte[] secretBytes;

    public JwtUtil(String secret) {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** 生成令牌：base64(payload.base64(signature)) */
    public String generateToken(long userId) {
        long exp = System.currentTimeMillis() + TTL_MILLIS;
        String payload = userId + ":" + exp;
        String sig = sign(payload);
        String token = payload + "." + sig;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    /** 解析令牌，校验签名和过期时间，返回 userId；无效返回 -1 */
    public long parseToken(String token) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int dotIdx = decoded.lastIndexOf('.');
            if (dotIdx <= 0) return -1;
            String payload = decoded.substring(0, dotIdx);
            String sig = decoded.substring(dotIdx + 1);

            if (!sign(payload).equals(sig)) {
                log.warn("JWT 签名校验失败");
                return -1;
            }

            String[] parts = payload.split(":");
            long userId = Long.parseLong(parts[0]);
            long exp = Long.parseLong(parts[1]);
            if (System.currentTimeMillis() > exp) {
                log.warn("JWT 已过期 userId={}", userId);
                return -1;
            }
            return userId;
        } catch (Exception e) {
            log.warn("JWT 解析失败", e);
            return -1;
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

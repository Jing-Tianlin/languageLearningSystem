package com.cupk.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 测试：签发/解析往返、签名篡改拒绝、非法输入。
 */
class JwtUtilTest {

    /** 32+ 字节密钥（生产密钥至少该强度） */
    private static final String SECRET = "test-secret-key-0123456789-abcdefghij";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET);

    @Test
    void generateAndParseRoundTrip() {
        String token = jwtUtil.generateToken(42L);
        JwtUtil.TokenPayload payload = jwtUtil.parseToken(token);
        assertNotNull(payload);
        assertEquals(42L, payload.userId());
        assertTrue(payload.issuedAt() > 0);
        assertTrue(payload.expiresAt() > payload.issuedAt());
    }

    @Test
    void tamperedPayloadIsRejected() {
        String token = jwtUtil.generateToken(42L);
        byte[] raw = Base64.getUrlDecoder().decode(token);
        String decoded = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
        // 篡改 userId 部分（payload 形如 "42:1690000000000:1690000000000"）
        String tampered = decoded.replaceFirst("^42:", "99:");
        String tamperedToken = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(tampered.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertNull(jwtUtil.parseToken(tamperedToken), "签名不匹配的令牌应被拒绝");
    }

    @Test
    void invalidTokensReturnNull() {
        assertNull(jwtUtil.parseToken("not-a-token"));
        assertNull(jwtUtil.parseToken(""));
        assertNull(jwtUtil.parseToken(null));
        assertNull(jwtUtil.parseToken("!!!invalid-base64!!!"));
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        JwtUtil otherKey = new JwtUtil("another-secret-key-9876543210-zyxwvutsrq");
        String token = otherKey.generateToken(7L);
        assertNull(jwtUtil.parseToken(token), "使用其他密钥签发的令牌应被拒绝");
    }
}

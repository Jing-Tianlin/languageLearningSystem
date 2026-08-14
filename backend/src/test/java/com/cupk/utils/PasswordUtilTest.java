package com.cupk.utils;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PasswordUtil 测试：BCrypt 编解码、旧格式兼容、升级判断、异常输入。
 */
class PasswordUtilTest {

    @Test
    void encodeProducesBcryptHashWithUniqueSalt() {
        String h1 = PasswordUtil.encode("secret123");
        String h2 = PasswordUtil.encode("secret123");
        assertTrue(h1.startsWith("$2"), "BCrypt 密文应以 $2 开头");
        assertNotEquals(h1, h2, "相同密码两次加密应产生不同盐");
    }

    @Test
    void matchesRoundTrip() {
        String hash = PasswordUtil.encode("myPassword1");
        assertTrue(PasswordUtil.matches("myPassword1", hash));
        assertFalse(PasswordUtil.matches("wrongPassword", hash));
    }

    @Test
    void matchesRejectsNullAndEmpty() {
        assertFalse(PasswordUtil.matches(null, "anything"));
        assertFalse(PasswordUtil.matches("pw", null));
        assertFalse(PasswordUtil.matches("pw", ""));
    }

    @Test
    void legacySaltedSha256StillVerifies() throws Exception {
        // 旧算法: base64(SHA256(salt + password))，密文格式 "salt$hash"
        String salt = "legacySalt123";
        String password = "oldPassword";
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(salt.getBytes(StandardCharsets.UTF_8));
        String hash = Base64.getEncoder().encodeToString(md.digest(password.getBytes(StandardCharsets.UTF_8)));
        String legacy = salt + "$" + hash;

        assertTrue(PasswordUtil.matches(password, legacy), "旧格式密码应仍可校验");
        assertFalse(PasswordUtil.matches("wrong", legacy));
        assertTrue(PasswordUtil.needsRehash(legacy), "旧格式应标记为需要升级");
    }

    @Test
    void needsRehashFlags() {
        assertTrue(PasswordUtil.needsRehash(null));
        assertFalse(PasswordUtil.needsRehash(PasswordUtil.encode("pw123456")));
        assertTrue(PasswordUtil.needsRehash("plaintextpassword"));
    }

    @Test
    void malformedBcryptHashDoesNotThrow() {
        assertFalse(PasswordUtil.matches("anything", "$2a$10$not-a-valid-bcrypt-hash"));
    }

    @Test
    void encodeRejectsEmptyAndOverlong() {
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.encode(""));
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.encode(null));
        String overlong = "a".repeat(80); // 80 字节 > BCrypt 72 字节上限
        assertThrows(IllegalArgumentException.class, () -> PasswordUtil.encode(overlong));
    }
}

package com.cupk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败计数与锁定服务（进程内实现）。
 *
 * 同一用户名连续失败达到阈值后，锁定一段时间；成功后清零。
 * 多实例部署需换 Redis；生产建议叠加图形验证码。
 */
@Component
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration lockDuration;
    private final Clock clock;

    private static final class Attempt {
        int count;
        Instant lockUntil;
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${login.attempt.max:5}") int maxAttempts,
            @Value("${login.attempt.lock-minutes:15}") int lockMinutes,
            Clock clock) {
        this.maxAttempts = maxAttempts;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
        this.clock = clock;
    }

    /** 该键是否处于锁定状态（读取时顺带清理已过期锁） */
    public boolean isLocked(String key) {
        Attempt a = attempts.get(key);
        if (a == null || a.lockUntil == null) {
            return false;
        }
        if (a.lockUntil.isAfter(Instant.now(clock))) {
            return true;
        }
        attempts.remove(key, a);
        return false;
    }

    /** 剩余锁定秒数（未锁定返回 0） */
    public long remainingLockSeconds(String key) {
        Attempt a = attempts.get(key);
        if (a != null && a.lockUntil != null) {
            return Math.max(Duration.between(Instant.now(clock), a.lockUntil).getSeconds(), 0);
        }
        return 0;
    }

    /** 记录一次失败，达到阈值则锁定 */
    public void recordFailure(String key) {
        attempts.compute(key, (k, a) -> {
            Attempt cur = (a == null) ? new Attempt() : a;
            cur.count++;
            if (cur.count >= maxAttempts) {
                cur.lockUntil = Instant.now(clock).plus(lockDuration);
            }
            return cur;
        });
    }

    /** 登录成功，清零计数 */
    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}

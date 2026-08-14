package com.cupk.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoginAttemptService 测试：失败计数、阈值锁定、成功清零、锁过期。
 */
class LoginAttemptServiceTest {

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone = ZoneId.systemDefault();

        MutableClock(Instant start) {
            this.instant = start;
        }

        void advance(Duration d) {
            instant = instant.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Test
    void locksAfterThresholdFailures() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T08:00:00Z"));
        LoginAttemptService service = new LoginAttemptService(3, 15, clock);

        assertFalse(service.isLocked("alice"));
        service.recordFailure("alice");
        service.recordFailure("alice");
        assertFalse(service.isLocked("alice"), "未达到阈值不应锁定");
        service.recordFailure("alice");
        assertTrue(service.isLocked("alice"), "达到阈值应锁定");
        assertTrue(service.remainingLockSeconds("alice") > 0);
    }

    @Test
    void successResetsCount() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T08:00:00Z"));
        LoginAttemptService service = new LoginAttemptService(3, 15, clock);

        service.recordFailure("bob");
        service.recordFailure("bob");
        service.recordSuccess("bob");
        service.recordFailure("bob");
        service.recordFailure("bob");
        assertFalse(service.isLocked("bob"), "成功清零后重新计数，未达阈值不应锁定");
    }

    @Test
    void lockExpiresAfterDuration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T08:00:00Z"));
        LoginAttemptService service = new LoginAttemptService(2, 15, clock);

        service.recordFailure("carol");
        service.recordFailure("carol");
        assertTrue(service.isLocked("carol"));

        clock.advance(Duration.ofMinutes(16));
        assertFalse(service.isLocked("carol"), "锁过期后应解除");
    }

    @Test
    void differentKeysAreIndependent() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T08:00:00Z"));
        LoginAttemptService service = new LoginAttemptService(1, 15, clock);

        service.recordFailure("dave");
        assertTrue(service.isLocked("dave"));
        assertFalse(service.isLocked("erin"), "不同用户名互不影响");
    }
}

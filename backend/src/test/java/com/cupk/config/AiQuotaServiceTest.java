package com.cupk.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiQuotaService 测试：分钟/日双重限额、跨窗口重置、开关控制。
 */
class AiQuotaServiceTest {

    /** 可控时钟：测试中可手动拨快时间 */
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

    private AiQuotaService newService(boolean enabled, int perMinute, int daily, MutableClock clock) {
        return new AiQuotaService(enabled, perMinute, daily, clock);
    }

    @Test
    void blocksWhenPerMinuteLimitExceeded() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T08:00:00Z"));
        AiQuotaService service = newService(true, 3, 100, clock);

        assertNull(service.tryAcquire(1L));
        assertNull(service.tryAcquire(1L));
        assertNull(service.tryAcquire(1L));
        String reject = service.tryAcquire(1L);
        assertNotNull(reject, "第 4 次调用应被分钟限流");
        assertTrue(reject.contains("每分钟"), "拒绝信息应说明分钟限流: " + reject);
    }

    @Test
    void minuteLimitResetsButDailyLimitPersists() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T08:00:00Z"));
        AiQuotaService service = newService(true, 2, 3, clock);

        assertNull(service.tryAcquire(1L));
        assertNull(service.tryAcquire(1L));
        assertNotNull(service.tryAcquire(1L), "分钟限额 2，第 3 次应被拒");

        // 61 秒后进入新的一分钟：分钟窗口重置，但当日计数保留
        clock.advance(Duration.ofSeconds(61));
        assertNull(service.tryAcquire(1L), "新一分钟应放行");
        String reject = service.tryAcquire(1L);
        assertNotNull(reject);
        assertTrue(reject.contains("今日"), "日限额 3 用尽后应提示今日已达上限: " + reject);
    }

    @Test
    void newDayResetsDailyQuota() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T08:00:00Z"));
        AiQuotaService service = newService(true, 10, 1, clock);

        assertNull(service.tryAcquire(1L));
        assertNotNull(service.tryAcquire(1L), "日限额 1，第二次应被拒");

        clock.advance(Duration.ofHours(25));
        assertNull(service.tryAcquire(1L), "新的一天应重置日配额");
    }

    @Test
    void differentUsersHaveIndependentQuota() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T08:00:00Z"));
        AiQuotaService service = newService(true, 1, 10, clock);

        assertNull(service.tryAcquire(1L));
        assertNotNull(service.tryAcquire(1L), "用户 1 达到分钟限额");
        assertNull(service.tryAcquire(2L), "用户 2 不应受用户 1 影响");
    }

    @Test
    void disabledServiceAlwaysAllows() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T08:00:00Z"));
        AiQuotaService service = newService(false, 0, 0, clock);
        for (int i = 0; i < 1000; i++) {
            assertNull(service.tryAcquire(1L));
        }
    }

    @Test
    void nullUserAlwaysAllows() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T08:00:00Z"));
        AiQuotaService service = newService(true, 1, 1, clock);
        assertNull(service.tryAcquire(null));
        assertNull(service.tryAcquire(null));
    }
}

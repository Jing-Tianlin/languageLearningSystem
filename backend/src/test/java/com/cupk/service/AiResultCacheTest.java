package com.cupk.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiResultCache 测试：写入/命中、TTL 过期、禁用、容量限制。
 */
class AiResultCacheTest {

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
    void putAndGetRoundTrip() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T08:00:00Z"));
        AiResultCache cache = new AiResultCache(true, 10, 100, clock);

        cache.put("key1", "value1");
        assertEquals("value1", cache.get("key1"));
        assertNull(cache.get("missing"));
    }

    @Test
    void expiredEntryIsEvicted() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T08:00:00Z"));
        AiResultCache cache = new AiResultCache(true, 10, 100, clock);

        cache.put("key1", "value1");
        clock.advance(Duration.ofMinutes(11));
        assertNull(cache.get("key1"), "TTL 过期后应失效");
    }

    @Test
    void disabledCacheAlwaysMisses() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T08:00:00Z"));
        AiResultCache cache = new AiResultCache(false, 10, 100, clock);

        cache.put("key1", "value1");
        assertNull(cache.get("key1"));
    }

    @Test
    void capacityIsBounded() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-01T08:00:00Z"));
        AiResultCache cache = new AiResultCache(true, 60, 3, clock);

        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        cache.put("d", "4"); // 触发淘汰
        assertTrue(cache.size() <= 3, "缓存容量不应超过上限");
    }
}

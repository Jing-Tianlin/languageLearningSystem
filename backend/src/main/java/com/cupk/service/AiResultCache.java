package com.cupk.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 结果缓存：以 prompt 哈希为键，短时间内缓存成功的 AI 响应，降低 DeepSeek API 成本。
 * 进程内实现，单实例适用；多实例需换 Redis。
 */
@Component
public class AiResultCache {

    private final boolean enabled;
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;

    private record Entry(String value, Instant expireAt) {}

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public AiResultCache(
            @Value("${ai.cache.enabled:true}") boolean enabled,
            @Value("${ai.cache.ttl-minutes:10}") int ttlMinutes,
            @Value("${ai.cache.max-entries:500}") int maxEntries,
            Clock clock) {
        this.enabled = enabled;
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    /** 命中返回缓存值，否则返回 null */
    public String get(String key) {
        if (!enabled) {
            return null;
        }
        Entry e = cache.get(key);
        if (e == null) {
            return null;
        }
        if (e.expireAt.isAfter(Instant.now(clock))) {
            return e.value;
        }
        cache.remove(key, e);
        return null;
    }

    /** 写入缓存（容量受限，超出时清理过期项，仍超出则淘汰最旧条目） */
    public void put(String key, String value) {
        if (!enabled || value == null) {
            return;
        }
        if (cache.size() >= maxEntries) {
            evictExpired();
            if (cache.size() >= maxEntries) {
                // 简单策略：淘汰一个任意条目，避免无界增长
                String oldest = cache.keySet().iterator().next();
                cache.remove(oldest);
            }
        }
        cache.put(key, new Entry(value, Instant.now(clock).plus(ttl)));
    }

    private void evictExpired() {
        Instant now = Instant.now(clock);
        cache.entrySet().removeIf(e -> e.getValue().expireAt.isBefore(now));
    }

    /** 供测试与监控 */
    public int size() {
        return cache.size();
    }
}

package com.cupk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 调用配额与限流服务。
 *
 * 双重限制：
 *  1. 每分钟请求数上限（防突发刷接口）
 *  2. 每日请求数上限（控制 DeepSeek API 成本）
 *
 * 进程内实现，适用于单实例部署；多实例部署需替换为 Redis 等共享存储。
 */
@Component
public class AiQuotaService {

    private final boolean enabled;
    private final int perMinuteLimit;
    private final int dailyLimit;
    private final Clock clock;

    /** 每多少次调用后做一次过期窗口清理，防止内存无限增长 */
    private final AtomicLong accessCounter = new AtomicLong(0);

    public AiQuotaService(
            @Value("${ai.quota.enabled:true}") boolean enabled,
            @Value("${ai.quota.per-minute:10}") int perMinuteLimit,
            @Value("${ai.quota.daily:100}") int dailyLimit,
            Clock clock) {
        this.enabled = enabled;
        this.perMinuteLimit = perMinuteLimit;
        this.dailyLimit = dailyLimit;
        this.clock = clock;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static final class Window {
        Instant minuteStart;
        Instant dayStart;
        int minuteCount;
        int dayCount;

        Window(Instant now) {
            this.minuteStart = now;
            this.dayStart = now;
        }
    }

    private final Map<Long, Window> windows = new ConcurrentHashMap<>();

    /**
     * 尝试消耗一次配额。
     *
     * @return null 表示放行；非 null 为拒绝原因（可直接展示给用户）
     */
    public String tryAcquire(Long userId) {
        if (!enabled || userId == null) {
            return null;
        }
        Instant now = Instant.now(clock);
        Window w = windows.computeIfAbsent(userId, k -> new Window(now));
        synchronized (w) {
            // 跨分钟：重置分钟计数
            if (w.minuteStart.plusSeconds(60).isBefore(now)) {
                w.minuteStart = now;
                w.minuteCount = 0;
            }
            // 跨天：重置当日计数（按本地时区自然日）
            if (!isSameDay(w.dayStart, now)) {
                w.dayStart = now;
                w.dayCount = 0;
            }
            if (w.dayCount >= dailyLimit) {
                return "今日 AI 调用次数已达上限（" + dailyLimit + " 次/天），请明天再试";
            }
            if (w.minuteCount >= perMinuteLimit) {
                return "AI 调用过于频繁（每分钟最多 " + perMinuteLimit + " 次），请稍后再试";
            }
            w.minuteCount++;
            w.dayCount++;
        }
        // 每 1000 次调用清理一次过期窗口
        if (accessCounter.incrementAndGet() % 1000 == 0) {
            cleanup(now);
        }
        return null;
    }

    private boolean isSameDay(Instant a, Instant b) {
        ZoneId zone = clock.getZone();
        return a.atZone(zone).toLocalDate().equals(b.atZone(zone).toLocalDate());
    }

    private void cleanup(Instant now) {
        windows.entrySet().removeIf(e -> {
            Window w = e.getValue();
            return w.dayStart.plusSeconds(48 * 3600).isBefore(now);
        });
    }

    /** 供测试与监控使用 */
    public int windowCount() {
        return windows.size();
    }
}

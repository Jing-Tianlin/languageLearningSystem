package com.cupk.config;

import com.cupk.mapper.UserMapper;
import com.cupk.pojo.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 鉴权信息缓存：缓存用户实体与角色列表，减少每个请求的数据库查询。
 *
 * TTL 60 秒；角色变更 / 账号状态变更 / 登录后调用 evict 主动失效。
 * 进程内缓存，多实例部署时需替换为 Redis。
 */
@Component
@RequiredArgsConstructor
public class AuthCacheService {

    private static final long TTL_MILLIS = 60_000L;

    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;

    public record AuthEntry(User user, List<String> roles) {}

    private record CacheEntry(AuthEntry entry, long expireAt) {}

    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    /** 获取用户鉴权信息（缓存未命中时回源数据库） */
    public AuthEntry get(Long userId) {
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(userId);
        if (cached != null && cached.expireAt > now) {
            return cached.entry;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            // 用户不存在不缓存，避免删除账号后残留
            cache.remove(userId);
            return null;
        }
        List<String> roles = jdbcTemplate.query(
            "SELECT r.code FROM role r JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?",
            (rs, rowNum) -> rs.getString("code"),
            userId
        );
        AuthEntry entry = new AuthEntry(user, roles);
        cache.put(userId, new CacheEntry(entry, now + TTL_MILLIS));
        return entry;
    }

    /** 主动失效指定用户的缓存（角色/状态/密码等变更后调用） */
    public void evict(Long userId) {
        cache.remove(userId);
    }

    /** 清空全部缓存（如角色被删除等全局变更后调用） */
    public void clear() {
        cache.clear();
    }
}

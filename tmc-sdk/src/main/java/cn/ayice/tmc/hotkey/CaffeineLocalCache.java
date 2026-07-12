package cn.ayice.tmc.hotkey;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;

/**
 * Caffeine 本地缓存封装。
 *
 * <p>本项目只对热点 key 使用本地缓存，普通 key 仍然回源 Redis。
 * 封装一层是为了让 TmcClient 不直接依赖 Caffeine API，后续也更容易集中处理缓存参数。</p>
 */
public class CaffeineLocalCache {

    /**
     * key/value 均为 String，与当前 Jedis get/set 接入保持一致。
     */
    private final Cache<String, String> cache;

    public CaffeineLocalCache(long maximumSize, long expireAfterWriteMillis) {
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        if (expireAfterWriteMillis <= 0) {
            throw new IllegalArgumentException("expireAfterWriteMillis must be positive");
        }
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(Duration.ofMillis(expireAfterWriteMillis))
                .build();
    }

    /**
     * 查询本地缓存，不触发自动加载。
     */
    public String getIfPresent(String key) {
        return cache.getIfPresent(key);
    }

    /**
     * 写入本地缓存。Redis 返回 null 时不缓存，避免把不存在的值固化到本地。
     */
    public void put(String key, String value) {
        if (value == null) {
            return;
        }
        cache.put(key, value);
    }

    /**
     * 删除某个 key 的本地缓存，用于写操作后的失效处理。
     */
    public void invalidate(String key) {
        cache.invalidate(key);
    }

    /**
     * 返回 Caffeine 估算的当前缓存大小，主要用于测试和观察。
     */
    public long estimatedSize() {
        return cache.estimatedSize();
    }
}

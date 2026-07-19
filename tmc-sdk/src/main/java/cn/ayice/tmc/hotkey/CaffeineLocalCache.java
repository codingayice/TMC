package cn.ayice.tmc.hotkey;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.function.Function;

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
     * 读取本地缓存，未命中时由 Caffeine 原子执行加载函数。
     *
     * <p>这是热点 key 防击穿的关键入口：当同一个 key 在高并发下同时 miss 时，
     * Caffeine 会保证同一时刻只有一个线程执行 {@code loader}，其他线程等待并复用
     * 这次加载结果。对于 TMC 来说，{@code loader} 通常就是 Redis 回源读取，因此该方法
     * 可以把同 key 的并发 Redis 请求合并成一次。</p>
     *
     * <p>如果 {@code loader} 返回 {@code null}，Caffeine 不会缓存该值，后续请求仍会重新回源。
     * 这符合 Redis key 不存在时的语义，避免把空值长期固化在本地缓存中。</p>
     */
    public String get(String key, Function<String, String> loader) {
        return cache.get(key, loader);
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
     * 清空当前节点的全部本地缓存。
     *
     * <p>该方法主要服务 Demo、运维调试和故障恢复场景：当需要从一个完全冷启动的
     * 本地缓存状态重新验证热点发现链路时，可以一次性删除 Caffeine 中的所有值。
     * 它只影响当前 JVM，不会删除 Redis 中的远端数据。</p>
     */
    public void invalidateAll() {
        cache.invalidateAll();
        cache.cleanUp();
    }

    /**
     * 返回 Caffeine 估算的当前缓存大小，主要用于测试和观察。
     */
    public long estimatedSize() {
        return cache.estimatedSize();
    }
}

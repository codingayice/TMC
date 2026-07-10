package cn.ayice.tmc.sdk;

import cn.ayice.tmc.communication.AccessReporter;
import cn.ayice.tmc.constant.TmcConstants;
import cn.ayice.tmc.enums.CacheOperation;
import cn.ayice.tmc.hotkey.HotKeyManager;
import cn.ayice.tmc.hotkey.CaffeineLocalCache;
import cn.ayice.tmc.model.AccessEvent;
import cn.ayice.tmc.model.HotKey;
import java.util.function.Supplier;

public class TmcClient {

    private final TmcProperties properties;
    private final HotKeyManager hotKeyManager;
    private final CaffeineLocalCache localCache;
    private final TmcMetrics metrics;
    private final AccessReporter accessReporter;

    public TmcClient(
            TmcProperties properties,
            HotKeyManager hotKeyManager,
            CaffeineLocalCache localCache,
            TmcMetrics metrics
    ) {
        this(properties, hotKeyManager, localCache, metrics, null);
    }

    public TmcClient(
            TmcProperties properties,
            HotKeyManager hotKeyManager,
            CaffeineLocalCache localCache,
            TmcMetrics metrics,
            AccessReporter accessReporter
    ) {
        this.properties = requireNonNull(properties, "properties");
        this.properties.validate();
        this.hotKeyManager = requireNonNull(hotKeyManager, "hotKeyManager");
        this.localCache = requireNonNull(localCache, "localCache");
        this.metrics = requireNonNull(metrics, "metrics");
        this.accessReporter = accessReporter;
    }

    public String get(String key, Supplier<String> jedisGetter) {
        requireNonNull(jedisGetter, "jedisGetter");
        incrementSafely(metrics::incrementTotalGets);
        reportAccessEvent(key);

        // 本地缓存未启用时，直接请求 Redis
        if (!properties.getLocalCache().isEnabled()) {
            return getFromJedis(jedisGetter);
        }

        /**
         * 非热key直接请求Redis
         * 热key:
         *      本地缓存存在->本地缓存
         *      本地缓存不存在-> Redis 获取值，写入本地缓存
         *      本地缓存请求异常->降级请求 Redis
         *
         *
         */
        boolean hotKey;
        try {
            hotKey = hotKeyManager.isHotKey(key);
        } catch (RuntimeException e) {
            incrementSafely(metrics::incrementFallbackGets);
            return getFromJedis(jedisGetter);
        }

        if (!hotKey) {
            return getFromJedis(jedisGetter);
        }

        incrementSafely(metrics::incrementHotKeyGets);
        String localValue;
        try {
            localValue = localCache.getIfPresent(key);
        } catch (RuntimeException e) {
            incrementSafely(metrics::incrementFallbackGets);
            return getFromJedis(jedisGetter);
        }

        if (localValue != null) {
            incrementSafely(metrics::incrementLocalCacheHits);
            return localValue;
        }

        incrementSafely(metrics::incrementLocalCacheMisses);
        String jedisValue = getFromJedis(jedisGetter);
        if (jedisValue != null) {
            try {
                localCache.put(key, jedisValue);
            } catch (RuntimeException e) {
                incrementSafely(metrics::incrementFallbackGets);
            }
        }
        return jedisValue;
    }

    public void addHotKey(HotKey hotKey) {
        hotKeyManager.addHotKey(hotKey);
    }

    public void removeHotKey(String key) {
        hotKeyManager.removeHotKey(key);
    }

    public void invalidate(String key) {
        localCache.invalidate(key);
    }

    public TmcMetricsSnapshot metrics() {
        return metrics.snapshot();
    }

    private String getFromJedis(Supplier<String> jedisGetter) {
        incrementSafely(metrics::incrementRedisGets);
        return jedisGetter.get();
    }

    private void reportAccessEvent(String key) {
        if (accessReporter == null || !properties.getReport().isEnabled()) {
            return;
        }
        try {
            accessReporter.report(new AccessEvent(
                    properties.getAppName(),
                    key,
                    System.currentTimeMillis(),
                    TmcConstants.ACCESS_EVENT_WEIGHT,
                    properties.getClientId(),
                    CacheOperation.GET
            ));
        } catch (RuntimeException e) {
            incrementSafely(() -> metrics.incrementReportFailed(1));
        }
    }

    /**
     * 指标统计出错不影响流程
     * @param increment
     */
    private static void incrementSafely(Runnable increment) {
        try {
            increment.run();
        } catch (RuntimeException ignored) {
            // 指标统计依次不影响主流程
        }
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}

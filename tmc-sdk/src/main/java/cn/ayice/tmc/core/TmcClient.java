package cn.ayice.tmc.core;

import cn.ayice.tmc.cache.LocalCache;
import cn.ayice.tmc.config.TmcProperties;
import cn.ayice.tmc.hotkey.HotKeyManager;
import cn.ayice.tmc.metrics.TmcMetrics;
import cn.ayice.tmc.metrics.TmcMetricsSnapshot;
import cn.ayice.tmc.model.HotKey;
import cn.ayice.tmc.remote.RemoteCacheClient;

public class TmcClient {

    private final TmcProperties properties;
    private final RemoteCacheClient remoteCacheClient;
    private final HotKeyManager hotKeyManager;
    private final LocalCache localCache;
    private final TmcMetrics metrics;

    public TmcClient(
            TmcProperties properties,
            RemoteCacheClient remoteCacheClient,
            HotKeyManager hotKeyManager,
            LocalCache localCache,
            TmcMetrics metrics
    ) {
        this.properties = requireNonNull(properties, "properties");
        this.properties.validate();
        this.remoteCacheClient = requireNonNull(remoteCacheClient, "remoteCacheClient");
        this.hotKeyManager = requireNonNull(hotKeyManager, "hotKeyManager");
        this.localCache = requireNonNull(localCache, "localCache");
        this.metrics = requireNonNull(metrics, "metrics");
    }

    public String get(String key) {
        incrementSafely(metrics::incrementTotalGets);

        // 本地缓存未启用时，直接请求 Redis
        if (!properties.getLocalCache().isEnabled()) {
            return getFromRemote(key);
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
            return getFromRemote(key);
        }

        if (!hotKey) {
            return getFromRemote(key);
        }

        incrementSafely(metrics::incrementHotKeyGets);
        String localValue;
        try {
            localValue = localCache.getIfPresent(key);
        } catch (RuntimeException e) {
            incrementSafely(metrics::incrementFallbackGets);
            return getFromRemote(key);
        }

        if (localValue != null) {
            incrementSafely(metrics::incrementLocalCacheHits);
            return localValue;
        }

        incrementSafely(metrics::incrementLocalCacheMisses);
        String remoteValue = getFromRemote(key);
        if (remoteValue != null) {
            try {
                localCache.put(key, remoteValue);
            } catch (RuntimeException e) {
                incrementSafely(metrics::incrementFallbackGets);
            }
        }
        return remoteValue;
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

    private String getFromRemote(String key) {
        incrementSafely(metrics::incrementRemoteGets);
        return remoteCacheClient.get(key);
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

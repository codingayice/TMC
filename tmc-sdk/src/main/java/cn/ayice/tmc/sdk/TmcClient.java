package cn.ayice.tmc.sdk;

import cn.ayice.tmc.communication.AccessReporter;
import cn.ayice.tmc.constant.TmcConstants;
import cn.ayice.tmc.enums.CacheOperation;
import cn.ayice.tmc.hotkey.HotKeyManager;
import cn.ayice.tmc.hotkey.CaffeineLocalCache;
import cn.ayice.tmc.model.AccessEvent;
import cn.ayice.tmc.model.HotKey;
import java.util.function.Supplier;

/**
 * TMC SDK 核心客户端。
 *
 * <p>该类承载业务读路径：访问事件上报、热点 key 判断、本地缓存读取、Redis 回源和指标统计。
 * tmc-jedis 只负责把 Jedis 风格 API 适配到这里。</p>
 */
public class TmcClient {

    /**
     * SDK 配置，包含应用名、本地缓存开关、热点 TTL 和访问事件上报配置。
     */
    private final TmcProperties properties;

    /**
     * 维护服务端下发的热点 key 集合。
     */
    private final HotKeyManager hotKeyManager;

    /**
     * Caffeine 本地缓存，仅对热点 key 生效。
     */
    private final CaffeineLocalCache localCache;

    /**
     * 客户端侧指标。指标失败不能影响业务读路径。
     */
    private final TmcMetrics metrics;

    /**
     * 访问事件上报器。允许为空，表示当前 SDK 不启用访问事件上报。
     */
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

    /**
     * 读取 key 的主流程。
     *
     * <p>{@code jedisGetter} 是业务原始 Redis get 回调。TMC 只在需要回源时执行该回调，
     * 因此非热 key、本地缓存未命中、热点判断异常和本地缓存异常都会安全回退到 Redis。</p>
     */
    public String get(String key, Supplier<String> jedisGetter) {
        requireNonNull(jedisGetter, "jedisGetter");
        incrementSafely(metrics::incrementTotalGets);
        reportAccessEvent(key);

        // 本地缓存未启用时，直接请求 Redis
        if (!properties.getLocalCache().isEnabled()) {
            return getFromJedis(jedisGetter);
        }

        // 非热 key 直接请求 Redis；热 key 优先读本地缓存，未命中再回源并写入本地缓存。
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

    /**
     * 手动添加热点 key，主要用于测试或后续 etcd 监听到热点快照后的增量处理。
     */
    public void addHotKey(HotKey hotKey) {
        hotKeyManager.addHotKey(hotKey);
    }

    /**
     * 手动移除热点 key。
     */
    public void removeHotKey(String key) {
        hotKeyManager.removeHotKey(key);
    }

    /**
     * 失效本地缓存。写操作成功后调用，避免本地继续读取旧值。
     */
    public void invalidate(String key) {
        localCache.invalidate(key);
    }

    /**
     * 获取当前 SDK 指标快照。
     */
    public TmcMetricsSnapshot metrics() {
        return metrics.snapshot();
    }

    /**
     * Redis 回源统一入口，方便统计真实 Redis get 次数。
     */
    private String getFromJedis(Supplier<String> jedisGetter) {
        incrementSafely(metrics::incrementRedisGets);
        return jedisGetter.get();
    }

    /**
     * 上报访问事件。
     *
     * <p>访问事件只服务热点探测，是旁路能力；任何异常都只记录失败指标，不能影响业务读结果。</p>
     */
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
     * 安全递增指标。
     *
     * <p>指标系统属于观察能力，不能反向破坏业务读取。</p>
     */
    private static void incrementSafely(Runnable increment) {
        try {
            increment.run();
        } catch (RuntimeException ignored) {
            // 指标统计异常不影响主流程。
        }
    }

    /**
     * 构造参数统一非空校验。
     */
    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}

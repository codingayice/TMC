package cn.ayice.tmc.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/**
 * SDK Micrometer 指标绑定测试。
 *
 * <p>这些测试保护简历级核心观测指标：Redis 回源、本地缓存命中、热点快照应用和写后失效。
 * Binder 只把已有 TmcMetrics 暴露给 MeterRegistry，不参与业务读写逻辑。</p>
 */
class TmcClientMetricsBinderTest {

    @Test
    void shouldRegisterCoreClientCountersWithAppAndClientTags() {
        TmcProperties properties = new TmcProperties();
        properties.setAppName("product-service");
        properties.setClientId("demo-a");
        TmcMetrics metrics = new TmcMetrics();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new TmcClientMetricsBinder(properties, metrics, registry);
        metrics.incrementTotalGets();
        metrics.incrementRedisGets();
        metrics.incrementLocalCacheHits();
        metrics.incrementHotKeySnapshotApplied();
        metrics.incrementInvalidationReceived();

        assertEquals(1.0, counter(registry, "tmc.client.total.gets"), 0.001);
        assertEquals(1.0, counter(registry, "tmc.client.redis.gets"), 0.001);
        assertEquals(1.0, counter(registry, "tmc.client.local.cache.hits"), 0.001);
        assertEquals(1.0, counter(registry, "tmc.client.hot.key.snapshot.applied"), 0.001);
        assertEquals(1.0, counter(registry, "tmc.client.invalidation.received"), 0.001);
        assertTrue(hasTag(registry, "tmc.client.total.gets", "app_name", "product-service"));
        assertTrue(hasTag(registry, "tmc.client.total.gets", "client_id", "demo-a"));
    }

    @Test
    void shouldNotRegisterHighCardinalityTags() {
        TmcProperties properties = new TmcProperties();
        properties.setAppName("product-service");
        properties.setClientId("demo-a");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new TmcClientMetricsBinder(properties, new TmcMetrics(), registry);

        assertFalse(hasTagKey(registry, "key"));
        assertFalse(hasTagKey(registry, "event_id"));
        assertFalse(hasTagKey(registry, "snapshot_version"));
    }

    private static double counter(SimpleMeterRegistry registry, String name) {
        return registry.get(name).functionCounter().count();
    }

    private static boolean hasTag(SimpleMeterRegistry registry, String name, String key, String value) {
        return registry.get(name).meters().stream()
                .anyMatch(meter -> value.equals(meter.getId().getTag(key)));
    }

    private static boolean hasTagKey(SimpleMeterRegistry registry, String key) {
        return registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .anyMatch(tag -> key.equals(tag.getKey()));
    }
}

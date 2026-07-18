package cn.ayice.tmc.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * SDK Micrometer 指标绑定测试。
 *
 * <p>这些测试保护简历级核心观测指标：Key 请求总数、本地缓存命中总数和 RT。
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
        metrics.incrementLocalCacheHits();
        metrics.recordReadDurationNanos(1_000_000);

        assertEquals(1.0, counter(registry, "tmc.sdk.key.request"), 0.001);
        assertEquals(1.0, counter(registry, "tmc.sdk.local.cache.hit"), 0.001);
        assertEquals(1.0, registry.get("tmc.sdk.read.duration").functionTimer().count(), 0.001);
        assertEquals(0.001, registry.get("tmc.sdk.read.duration").functionTimer().totalTime(java.util.concurrent.TimeUnit.SECONDS), 0.000_001);
        assertEquals(Set.of("tmc.sdk.key.request", "tmc.sdk.local.cache.hit", "tmc.sdk.read.duration"), meterNames(registry));
        assertTrue(hasTag(registry, "tmc.sdk.key.request", "app_name", "product-service"));
        assertTrue(hasTag(registry, "tmc.sdk.key.request", "client_id", "demo-a"));
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

    private static Set<String> meterNames(SimpleMeterRegistry registry) {
        return registry.getMeters().stream()
                .map(meter -> meter.getId().getName())
                .collect(Collectors.toSet());
    }
}

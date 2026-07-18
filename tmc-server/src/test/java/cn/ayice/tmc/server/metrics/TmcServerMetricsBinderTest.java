package cn.ayice.tmc.server.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/**
 * tmc-server Micrometer 指标绑定测试。
 *
 * <p>这些测试保护服务端核心观测指标：访问事件是否进入服务端、滑窗是否执行、
 * 当前热点数量以及热点快照是否成功发布。</p>
 */
class TmcServerMetricsBinderTest {

    @Test
    void shouldRegisterCoreServerCountersAndGauges() {
        TmcServerMetrics metrics = new TmcServerMetrics();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new TmcServerMetricsBinder(metrics, registry);
        metrics.incrementMessagesConsumed();
        metrics.incrementAccessEventsAccumulated();
        metrics.incrementMappingRuns();
        metrics.incrementHotKeyPublishSucceeded();
        metrics.updateTracked(2, 10);
        metrics.setHotKeysDetected(3);

        assertEquals(1.0, counter(registry, "tmc.server.messages.consumed"), 0.001);
        assertEquals(1.0, counter(registry, "tmc.server.access.events.accumulated"), 0.001);
        assertEquals(1.0, counter(registry, "tmc.server.mapping.runs"), 0.001);
        assertEquals(1.0, counter(registry, "tmc.server.hot.key.publish.succeeded"), 0.001);
        assertEquals(2.0, gauge(registry, "tmc.server.tracked.apps"), 0.001);
        assertEquals(10.0, gauge(registry, "tmc.server.tracked.keys"), 0.001);
        assertEquals(3.0, gauge(registry, "tmc.server.hot.keys.detected"), 0.001);
    }

    @Test
    void shouldNotRegisterKeyLabel() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new TmcServerMetricsBinder(new TmcServerMetrics(), registry);

        assertFalse(registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .anyMatch(tag -> "key".equals(tag.getKey())));
    }

    private static double counter(SimpleMeterRegistry registry, String name) {
        return registry.get(name).functionCounter().count();
    }

    private static double gauge(SimpleMeterRegistry registry, String name) {
        return registry.get(name).gauge().value();
    }
}

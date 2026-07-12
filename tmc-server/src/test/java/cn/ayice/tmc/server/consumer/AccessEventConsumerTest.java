package cn.ayice.tmc.server.consumer;

import cn.ayice.tmc.enums.CacheOperation;
import cn.ayice.tmc.model.AccessEvent;
import cn.ayice.tmc.server.hotkey.AccessEventAccumulator;
import cn.ayice.tmc.server.metrics.TmcServerMetrics;
import cn.ayice.tmc.util.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AccessEventConsumer 消费测试。
 *
 * <p>保障合法 Kafka JSON 消息进入热点累加器，非法消息只增加指标而不会污染统计。</p>
 */
class AccessEventConsumerTest {

    private final AccessEventAccumulator accumulator = new AccessEventAccumulator();
    private final TmcServerMetrics metrics = new TmcServerMetrics();
    private final AccessEventConsumer consumer = new AccessEventConsumer(accumulator, metrics);

    @Test
    void shouldConsumeValidAccessEventJson() {
        AccessEvent event = new AccessEvent("app-a", "product:1", 1000L, 2, "client-1", CacheOperation.GET);

        consumer.consume(JsonUtils.toJson(event));

        assertEquals(2L, accumulator.drainApp("app-a").get("product:1"));
        assertEquals(1L, metrics.snapshot().getMessagesConsumed());
        assertEquals(1L, metrics.snapshot().getAccessEventsAccumulated());
    }

    @Test
    void shouldIgnoreInvalidJson() {
        consumer.consume("{bad-json");

        assertEquals(1L, metrics.snapshot().getMessagesInvalid());
        assertTrue(accumulator.appNames().isEmpty());
    }

    @Test
    void shouldIgnoreBlankAppName() {
        AccessEvent event = new AccessEvent("", "product:1", 1000L, 1, "client-1", CacheOperation.GET);

        consumer.consume(JsonUtils.toJson(event));

        assertEquals(1L, metrics.snapshot().getMessagesInvalid());
        assertTrue(accumulator.appNames().isEmpty());
    }

    @Test
    void shouldIgnoreBlankKey() {
        AccessEvent event = new AccessEvent("app-a", " ", 1000L, 1, "client-1", CacheOperation.GET);

        consumer.consume(JsonUtils.toJson(event));

        assertEquals(1L, metrics.snapshot().getMessagesInvalid());
        assertTrue(accumulator.appNames().isEmpty());
    }

    @Test
    void shouldIgnoreNonPositiveWeight() {
        AccessEvent event = new AccessEvent("app-a", "product:1", 1000L, 0, "client-1", CacheOperation.GET);

        consumer.consume(JsonUtils.toJson(event));

        assertEquals(1L, metrics.snapshot().getMessagesInvalid());
        assertTrue(accumulator.appNames().isEmpty());
    }

    @Test
    void shouldIgnoreNonGetOperation() {
        AccessEvent event = new AccessEvent("app-a", "product:1", 1000L, 1, "client-1", CacheOperation.SET);

        consumer.consume(JsonUtils.toJson(event));

        assertEquals(1L, metrics.snapshot().getMessagesInvalid());
        assertTrue(accumulator.appNames().isEmpty());
    }
}

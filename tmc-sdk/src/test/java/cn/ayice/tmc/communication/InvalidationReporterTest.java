package cn.ayice.tmc.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.ayice.tmc.enums.CacheOperation;
import cn.ayice.tmc.model.InvalidationEvent;
import cn.ayice.tmc.util.EtcdKeys;
import cn.ayice.tmc.util.JsonUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * InvalidationReporter 失效事件发布测试。
 *
 * <p>该测试保护 SDK 写操作后的广播协议：每次写操作必须写入唯一 etcd 路径，
 * value 必须是 InvalidationEvent JSON，发布异常不能抛回业务写路径。</p>
 */
class InvalidationReporterTest {

    @Test
    void shouldWriteInvalidationEventToUniquePath() {
        List<String> paths = new ArrayList<>();
        List<String> values = new ArrayList<>();
        InvalidationReporter reporter = InvalidationReporter.forTest(
                "product-service",
                "client-1",
                new InvalidationProperties(),
                (path, value) -> {
                    paths.add(path);
                    values.add(value);
                }
        );

        reporter.report("product:1", CacheOperation.SET);
        reporter.report("product:1", CacheOperation.SET);

        assertEquals(2, paths.size());
        assertNotEquals(paths.get(0), paths.get(1));
        assertTrue(paths.get(0).startsWith(EtcdKeys.invalidationEventPrefix("product-service")));
        InvalidationEvent event = JsonUtils.fromJson(values.get(0), InvalidationEvent.class);
        assertEquals("product-service", event.getAppName());
        assertEquals("product:1", event.getKey());
        assertEquals(CacheOperation.SET, event.getOperation());
        assertEquals("client-1", event.getClientId());
    }

    @Test
    void shouldRecordFailureWhenWriteThrows() {
        InvalidationReporter reporter = InvalidationReporter.forTest(
                "product-service",
                "client-1",
                new InvalidationProperties(),
                (path, value) -> {
                    throw new IllegalStateException("etcd unavailable");
                }
        );

        assertDoesNotThrow(() -> reporter.report("product:1", CacheOperation.SET));
    }

    @Test
    void shouldRejectBlankKeyWithoutWriting() {
        List<String> paths = new ArrayList<>();
        InvalidationReporter reporter = InvalidationReporter.forTest(
                "product-service",
                "client-1",
                new InvalidationProperties(),
                (path, value) -> paths.add(path)
        );

        reporter.report(" ", CacheOperation.SET);

        assertTrue(paths.isEmpty());
    }
}

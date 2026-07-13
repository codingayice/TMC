package cn.ayice.tmc.server.hotkey;

import cn.ayice.tmc.enums.CacheOperation;
import cn.ayice.tmc.model.AccessEvent;
import cn.ayice.tmc.server.config.EtcdProperties;
import cn.ayice.tmc.server.config.HotKeyDetectProperties;
import cn.ayice.tmc.server.metrics.TmcServerMetrics;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * HotKeyMappingScheduler 映射调度测试。
 *
 * <p>保障当前周期热度能进入时间轮，本周期无访问的旧 key 会写入 0 并最终被清理。</p>
 */
class HotKeyMappingSchedulerTest {

    @Test
    void shouldMapCurrentScoresIntoTimeWheelAndSaveSnapshot() {
        AccessEventAccumulator accumulator = new AccessEventAccumulator();
        TmcServerMetrics metrics = new TmcServerMetrics();
        HotKeySnapshotRepository repository = new HotKeySnapshotRepository();
        HotKeyMappingScheduler scheduler = new HotKeyMappingScheduler(
                accumulator,
                new HotKeyDetector(properties(2, 10)),
                repository,
                testPublisher(metrics),
                metrics,
                properties(2, 10)
        );
        accumulator.add(event("app-a", "product:1", 2));

        scheduler.mapOnce();

        assertNotNull(repository.get("app-a"));
        assertEquals("product:1", repository.get("app-a").getHotKeys().get(0).getKey());
        assertEquals(1L, metrics.snapshot().getMappingRuns());
    }

    @Test
    void shouldAdvanceOldKeysWithZeroAndCleanColdKeys() {
        AccessEventAccumulator accumulator = new AccessEventAccumulator();
        TmcServerMetrics metrics = new TmcServerMetrics();
        HotKeySnapshotRepository repository = new HotKeySnapshotRepository();
        HotKeyDetectProperties properties = properties(1, 10);
        properties.setWindowSeconds(6);
        properties.setBucketSeconds(3);
        HotKeyMappingScheduler scheduler = new HotKeyMappingScheduler(
                accumulator,
                new HotKeyDetector(properties),
                repository,
                testPublisher(metrics),
                metrics,
                properties
        );
        accumulator.add(event("app-a", "product:1", 1));

        scheduler.mapOnce();
        scheduler.mapOnce();
        scheduler.mapOnce();

        assertNull(repository.get("app-a"));
        assertEquals(3L, metrics.snapshot().getMappingRuns());
    }

    @Test
    void shouldPublishSnapshotAfterMapping() {
        AccessEventAccumulator accumulator = new AccessEventAccumulator();
        TmcServerMetrics metrics = new TmcServerMetrics();
        HotKeySnapshotRepository repository = new HotKeySnapshotRepository();
        List<String> publishedValues = new ArrayList<>();
        HotKeyMappingScheduler scheduler = new HotKeyMappingScheduler(
                accumulator,
                new HotKeyDetector(properties(1, 10)),
                repository,
                HotKeyPublisher.forTest(new EtcdProperties(), metrics, (path, value) -> publishedValues.add(value), path -> {
                }),
                metrics,
                properties(1, 10)
        );
        accumulator.add(event("app-a", "product:1", 1));

        scheduler.mapOnce();

        assertEquals(1, publishedValues.size());
        assertEquals(1L, metrics.snapshot().getHotKeyPublishSucceeded());
    }

    private static AccessEvent event(String appName, String key, int weight) {
        return new AccessEvent(appName, key, System.currentTimeMillis(), weight, "client-1", CacheOperation.GET);
    }

    private static HotKeyDetectProperties properties(long threshold, int topN) {
        HotKeyDetectProperties properties = new HotKeyDetectProperties();
        properties.setThreshold(threshold);
        properties.setTopN(topN);
        properties.setTtlMillis(300000L);
        return properties;
    }

    private static HotKeyPublisher testPublisher(TmcServerMetrics metrics) {
        return HotKeyPublisher.forTest(new EtcdProperties(), metrics, (path, value) -> {
        }, path -> {
        });
    }
}

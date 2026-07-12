package cn.ayice.tmc.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * TmcMetrics 指标测试。
 *
 * <p>保障 SDK 读路径和访问事件上报相关计数能被正确累计并形成快照。</p>
 */
class TmcMetricsTest {

    @Test
    void shouldStartWithZeroValues() {
        TmcMetricsSnapshot snapshot = new TmcMetrics().snapshot();

        assertEquals(0, snapshot.getTotalGets());
        assertEquals(0, snapshot.getHotKeyGets());
        assertEquals(0, snapshot.getLocalCacheHits());
        assertEquals(0, snapshot.getLocalCacheMisses());
        assertEquals(0, snapshot.getRedisGets());
        assertEquals(0, snapshot.getFallbackGets());
        assertEquals(0, snapshot.getReportQueued());
        assertEquals(0, snapshot.getReportDropped());
        assertEquals(0, snapshot.getReportSucceeded());
        assertEquals(0, snapshot.getReportFailed());
    }

    @Test
    void shouldSnapshotIncrementedValues() {
        TmcMetrics metrics = new TmcMetrics();

        metrics.incrementTotalGets();
        metrics.incrementHotKeyGets();
        metrics.incrementLocalCacheHits();
        metrics.incrementLocalCacheMisses();
        metrics.incrementRedisGets();
        metrics.incrementFallbackGets();
        metrics.incrementReportQueued();
        metrics.incrementReportDropped();
        metrics.incrementReportSucceeded(2);
        metrics.incrementReportFailed(3);

        TmcMetricsSnapshot snapshot = metrics.snapshot();
        assertEquals(1, snapshot.getTotalGets());
        assertEquals(1, snapshot.getHotKeyGets());
        assertEquals(1, snapshot.getLocalCacheHits());
        assertEquals(1, snapshot.getLocalCacheMisses());
        assertEquals(1, snapshot.getRedisGets());
        assertEquals(1, snapshot.getFallbackGets());
        assertEquals(1, snapshot.getReportQueued());
        assertEquals(1, snapshot.getReportDropped());
        assertEquals(2, snapshot.getReportSucceeded());
        assertEquals(3, snapshot.getReportFailed());
    }
}

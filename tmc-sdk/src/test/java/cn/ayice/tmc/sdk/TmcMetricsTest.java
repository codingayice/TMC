package cn.ayice.tmc.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * TmcMetrics 指标测试。
 *
 * <p>保障 SDK 只保留能直观看出 TMC 效果的最小指标：Key 访问总数、
 * 本地缓存命中总数和读请求总耗时。命中率、QPS 和 RT 都由 Grafana 基于这些值计算。</p>
 */
class TmcMetricsTest {

    @Test
    void shouldStartWithZeroValues() {
        TmcMetricsSnapshot snapshot = new TmcMetrics().snapshot();

        assertEquals(0, snapshot.getTotalGets());
        assertEquals(0, snapshot.getLocalCacheHits());
        assertEquals(0, snapshot.getReadDurationNanos());
    }

    @Test
    void shouldSnapshotIncrementedValues() {
        TmcMetrics metrics = new TmcMetrics();

        metrics.incrementTotalGets();
        metrics.incrementLocalCacheHits();
        metrics.recordReadDurationNanos(123);

        TmcMetricsSnapshot snapshot = metrics.snapshot();
        assertEquals(1, snapshot.getTotalGets());
        assertEquals(1, snapshot.getLocalCacheHits());
        assertEquals(123, snapshot.getReadDurationNanos());
    }
}

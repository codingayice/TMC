package cn.ayice.tmc.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TmcMetricsTest {

    @Test
    void shouldStartWithZeroValues() {
        TmcMetricsSnapshot snapshot = new TmcMetrics().snapshot();

        assertEquals(0, snapshot.getTotalGets());
        assertEquals(0, snapshot.getHotKeyGets());
        assertEquals(0, snapshot.getLocalCacheHits());
        assertEquals(0, snapshot.getLocalCacheMisses());
        assertEquals(0, snapshot.getRemoteGets());
        assertEquals(0, snapshot.getFallbackGets());
    }

    @Test
    void shouldSnapshotIncrementedValues() {
        TmcMetrics metrics = new TmcMetrics();

        metrics.incrementTotalGets();
        metrics.incrementHotKeyGets();
        metrics.incrementLocalCacheHits();
        metrics.incrementLocalCacheMisses();
        metrics.incrementRemoteGets();
        metrics.incrementFallbackGets();

        TmcMetricsSnapshot snapshot = metrics.snapshot();
        assertEquals(1, snapshot.getTotalGets());
        assertEquals(1, snapshot.getHotKeyGets());
        assertEquals(1, snapshot.getLocalCacheHits());
        assertEquals(1, snapshot.getLocalCacheMisses());
        assertEquals(1, snapshot.getRemoteGets());
        assertEquals(1, snapshot.getFallbackGets());
    }
}

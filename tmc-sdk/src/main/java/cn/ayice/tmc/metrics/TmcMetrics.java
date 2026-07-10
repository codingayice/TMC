package cn.ayice.tmc.metrics;

import java.util.concurrent.atomic.LongAdder;

public class TmcMetrics {

    private final LongAdder totalGets = new LongAdder();
    private final LongAdder hotKeyGets = new LongAdder();
    private final LongAdder localCacheHits = new LongAdder();
    private final LongAdder localCacheMisses = new LongAdder();
    private final LongAdder remoteGets = new LongAdder();
    private final LongAdder fallbackGets = new LongAdder();

    public void incrementTotalGets() {
        totalGets.increment();
    }

    public void incrementHotKeyGets() {
        hotKeyGets.increment();
    }

    public void incrementLocalCacheHits() {
        localCacheHits.increment();
    }

    public void incrementLocalCacheMisses() {
        localCacheMisses.increment();
    }

    public void incrementRemoteGets() {
        remoteGets.increment();
    }

    public void incrementFallbackGets() {
        fallbackGets.increment();
    }

    public TmcMetricsSnapshot snapshot() {
        return new TmcMetricsSnapshot(
                totalGets.sum(),
                hotKeyGets.sum(),
                localCacheHits.sum(),
                localCacheMisses.sum(),
                remoteGets.sum(),
                fallbackGets.sum()
        );
    }
}

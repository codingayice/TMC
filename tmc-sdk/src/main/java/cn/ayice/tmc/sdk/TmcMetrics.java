package cn.ayice.tmc.sdk;

import java.util.concurrent.atomic.LongAdder;

public class TmcMetrics {

    private final LongAdder totalGets = new LongAdder();
    private final LongAdder hotKeyGets = new LongAdder();
    private final LongAdder localCacheHits = new LongAdder();
    private final LongAdder localCacheMisses = new LongAdder();
    private final LongAdder redisGets = new LongAdder();
    private final LongAdder fallbackGets = new LongAdder();
    private final LongAdder reportQueued = new LongAdder();
    private final LongAdder reportDropped = new LongAdder();
    private final LongAdder reportSucceeded = new LongAdder();
    private final LongAdder reportFailed = new LongAdder();

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

    public void incrementRedisGets() {
        redisGets.increment();
    }

    public void incrementFallbackGets() {
        fallbackGets.increment();
    }

    public void incrementReportQueued() {
        reportQueued.increment();
    }

    public void incrementReportDropped() {
        reportDropped.increment();
    }

    public void incrementReportSucceeded(long count) {
        reportSucceeded.add(count);
    }

    public void incrementReportFailed(long count) {
        reportFailed.add(count);
    }

    public TmcMetricsSnapshot snapshot() {
        return new TmcMetricsSnapshot(
                totalGets.sum(),
                hotKeyGets.sum(),
                localCacheHits.sum(),
                localCacheMisses.sum(),
                redisGets.sum(),
                fallbackGets.sum(),
                reportQueued.sum(),
                reportDropped.sum(),
                reportSucceeded.sum(),
                reportFailed.sum()
        );
    }
}

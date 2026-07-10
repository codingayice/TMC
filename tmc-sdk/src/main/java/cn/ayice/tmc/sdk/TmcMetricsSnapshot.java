package cn.ayice.tmc.sdk;

public class TmcMetricsSnapshot {

    private final long totalGets;
    private final long hotKeyGets;
    private final long localCacheHits;
    private final long localCacheMisses;
    private final long redisGets;
    private final long fallbackGets;
    private final long reportQueued;
    private final long reportDropped;
    private final long reportSucceeded;
    private final long reportFailed;

    public TmcMetricsSnapshot(
            long totalGets,
            long hotKeyGets,
            long localCacheHits,
            long localCacheMisses,
            long redisGets,
            long fallbackGets,
            long reportQueued,
            long reportDropped,
            long reportSucceeded,
            long reportFailed
    ) {
        this.totalGets = totalGets;
        this.hotKeyGets = hotKeyGets;
        this.localCacheHits = localCacheHits;
        this.localCacheMisses = localCacheMisses;
        this.redisGets = redisGets;
        this.fallbackGets = fallbackGets;
        this.reportQueued = reportQueued;
        this.reportDropped = reportDropped;
        this.reportSucceeded = reportSucceeded;
        this.reportFailed = reportFailed;
    }

    public long getTotalGets() {
        return totalGets;
    }

    public long getHotKeyGets() {
        return hotKeyGets;
    }

    public long getLocalCacheHits() {
        return localCacheHits;
    }

    public long getLocalCacheMisses() {
        return localCacheMisses;
    }

    public long getRedisGets() {
        return redisGets;
    }

    public long getFallbackGets() {
        return fallbackGets;
    }

    public long getReportQueued() {
        return reportQueued;
    }

    public long getReportDropped() {
        return reportDropped;
    }

    public long getReportSucceeded() {
        return reportSucceeded;
    }

    public long getReportFailed() {
        return reportFailed;
    }
}

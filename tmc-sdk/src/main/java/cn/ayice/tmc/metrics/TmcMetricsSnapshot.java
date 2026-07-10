package cn.ayice.tmc.metrics;

public class TmcMetricsSnapshot {

    private final long totalGets;
    private final long hotKeyGets;
    private final long localCacheHits;
    private final long localCacheMisses;
    private final long remoteGets;
    private final long fallbackGets;

    public TmcMetricsSnapshot(
            long totalGets,
            long hotKeyGets,
            long localCacheHits,
            long localCacheMisses,
            long remoteGets,
            long fallbackGets
    ) {
        this.totalGets = totalGets;
        this.hotKeyGets = hotKeyGets;
        this.localCacheHits = localCacheHits;
        this.localCacheMisses = localCacheMisses;
        this.remoteGets = remoteGets;
        this.fallbackGets = fallbackGets;
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

    public long getRemoteGets() {
        return remoteGets;
    }

    public long getFallbackGets() {
        return fallbackGets;
    }
}

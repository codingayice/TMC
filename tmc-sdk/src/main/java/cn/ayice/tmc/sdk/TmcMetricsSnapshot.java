package cn.ayice.tmc.sdk;

/**
 * SDK 指标快照。
 *
 * <p>快照对象用于对外展示某一刻的计数值，避免外部代码直接依赖可变的 LongAdder。</p>
 */
public class TmcMetricsSnapshot {

    /**
     * 所有 get 请求次数。
     */
    private final long totalGets;

    /**
     * 热点 key get 请求次数。
     */
    private final long hotKeyGets;

    /**
     * 本地缓存命中次数。
     */
    private final long localCacheHits;

    /**
     * 本地缓存未命中次数。
     */
    private final long localCacheMisses;

    /**
     * 实际回源 Redis 的次数。
     */
    private final long redisGets;

    /**
     * 旁路能力异常后降级次数。
     */
    private final long fallbackGets;

    /**
     * 成功进入访问事件上报队列的数量。
     */
    private final long reportQueued;

    /**
     * 上报队列满或异常导致丢弃的事件数量。
     */
    private final long reportDropped;

    /**
     * 成功写入 rsyslog 的访问事件数量。
     */
    private final long reportSucceeded;

    /**
     * 写入 rsyslog 失败的访问事件数量。
     */
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

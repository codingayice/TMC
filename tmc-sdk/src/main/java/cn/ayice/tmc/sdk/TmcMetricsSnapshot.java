package cn.ayice.tmc.sdk;

/**
 * SDK 核心效果指标快照。
 *
 * <p>这个快照只保留能够直观看出 TMC 运行效果的最小数据：
 * Key 访问总数、本地缓存命中总数和读请求总耗时。Grafana 中的本地缓存命中率、
 * QPS 和 RT 都从这三个值派生，避免把通信重连、内部异常等低层细节塞进主看板。</p>
 */
public class TmcMetricsSnapshot {

    /**
     * SDK 处理过的 Key 读请求总数。
     */
    private final long totalGets;

    /**
     * 热点 Key 命中 Caffeine 本地缓存的总次数。
     */
    private final long localCacheHits;

    /**
     * SDK 读请求累计耗时，单位为纳秒。
     */
    private final long readDurationNanos;

    public TmcMetricsSnapshot(long totalGets, long localCacheHits, long readDurationNanos) {
        this.totalGets = totalGets;
        this.localCacheHits = localCacheHits;
        this.readDurationNanos = readDurationNanos;
    }

    public long getTotalGets() {
        return totalGets;
    }

    public long getLocalCacheHits() {
        return localCacheHits;
    }

    public long getReadDurationNanos() {
        return readDurationNanos;
    }
}

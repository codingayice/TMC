package cn.ayice.tmc.sdk;

import java.util.concurrent.atomic.LongAdder;

/**
 * SDK 客户端指标。
 *
 * <p>这些指标用于判断 TMC 是否减少 Redis 压力、是否命中本地缓存、访问事件上报是否健康。
 * 使用 LongAdder 是因为读路径高频调用，LongAdder 在并发递增场景下比 AtomicLong 更适合。</p>
 */
public class TmcMetrics {

    /**
     * 所有 get 请求次数。
     */
    private final LongAdder totalGets = new LongAdder();

    /**
     * 被识别为热点 key 的 get 请求次数。
     */
    private final LongAdder hotKeyGets = new LongAdder();

    /**
     * 本地缓存命中次数。
     */
    private final LongAdder localCacheHits = new LongAdder();

    /**
     * 热点 key 本地缓存未命中次数。
     */
    private final LongAdder localCacheMisses = new LongAdder();

    /**
     * 实际回源 Redis 的 get 次数。
     */
    private final LongAdder redisGets = new LongAdder();

    /**
     * 热点判断、本地缓存或其他旁路能力异常后降级到 Redis 的次数。
     */
    private final LongAdder fallbackGets = new LongAdder();

    /**
     * 访问事件成功进入上报队列的数量。
     */
    private final LongAdder reportQueued = new LongAdder();

    /**
     * 上报队列满或入队异常导致丢弃的事件数量。
     */
    private final LongAdder reportDropped = new LongAdder();

    /**
     * 后台线程成功写入 rsyslog 的事件数量。
     */
    private final LongAdder reportSucceeded = new LongAdder();

    /**
     * 后台线程写 rsyslog 失败的事件数量。
     */
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

    /**
     * 生成不可变指标快照，避免调用方直接持有 LongAdder。
     */
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

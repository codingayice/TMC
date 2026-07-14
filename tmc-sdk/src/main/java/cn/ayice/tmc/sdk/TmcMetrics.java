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

    /**
     * 启动或运行过程中成功应用热点快照的次数。
     */
    private final LongAdder hotKeySnapshotApplied = new LongAdder();

    /**
     * 收到非法热点快照的次数，通常代表服务端和 SDK 协议不一致。
     */
    private final LongAdder hotKeySnapshotInvalid = new LongAdder();

    /**
     * 收到热点快照删除事件并清空本地热点集合的次数。
     */
    private final LongAdder hotKeySnapshotDeleted = new LongAdder();

    /**
     * etcd watch 断开后触发重连的次数。
     */
    private final LongAdder hotKeyWatchReconnect = new LongAdder();

    /**
     * 热点发现监听启动或处理失败次数。
     */
    private final LongAdder hotKeyWatchFailed = new LongAdder();

    /**
     * 当前节点主动删除本地缓存的次数。
     */
    private final LongAdder localInvalidations = new LongAdder();

    /**
     * 失效事件成功写入 etcd 的次数。
     */
    private final LongAdder invalidationReportSucceeded = new LongAdder();

    /**
     * 失效事件写入失败次数。
     */
    private final LongAdder invalidationReportFailed = new LongAdder();

    /**
     * 收到并处理其他节点失效事件的次数。
     */
    private final LongAdder invalidationReceived = new LongAdder();

    /**
     * 忽略自身失效事件的次数。
     */
    private final LongAdder invalidationSelfIgnored = new LongAdder();

    /**
     * 非法失效事件次数。
     */
    private final LongAdder invalidationInvalid = new LongAdder();

    /**
     * 失效事件 watch 重连次数。
     */
    private final LongAdder invalidationWatchReconnect = new LongAdder();

    /**
     * 失效事件 watch 启动或处理失败次数。
     */
    private final LongAdder invalidationWatchFailed = new LongAdder();

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

    public void incrementHotKeySnapshotApplied() {
        hotKeySnapshotApplied.increment();
    }

    public void incrementHotKeySnapshotInvalid() {
        hotKeySnapshotInvalid.increment();
    }

    public void incrementHotKeySnapshotDeleted() {
        hotKeySnapshotDeleted.increment();
    }

    public void incrementHotKeyWatchReconnect() {
        hotKeyWatchReconnect.increment();
    }

    public void incrementHotKeyWatchFailed() {
        hotKeyWatchFailed.increment();
    }

    public void incrementLocalInvalidations() {
        localInvalidations.increment();
    }

    public void incrementInvalidationReportSucceeded() {
        invalidationReportSucceeded.increment();
    }

    public void incrementInvalidationReportFailed() {
        invalidationReportFailed.increment();
    }

    public void incrementInvalidationReceived() {
        invalidationReceived.increment();
    }

    public void incrementInvalidationSelfIgnored() {
        invalidationSelfIgnored.increment();
    }

    public void incrementInvalidationInvalid() {
        invalidationInvalid.increment();
    }

    public void incrementInvalidationWatchReconnect() {
        invalidationWatchReconnect.increment();
    }

    public void incrementInvalidationWatchFailed() {
        invalidationWatchFailed.increment();
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
                reportFailed.sum(),
                hotKeySnapshotApplied.sum(),
                hotKeySnapshotInvalid.sum(),
                hotKeySnapshotDeleted.sum(),
                hotKeyWatchReconnect.sum(),
                hotKeyWatchFailed.sum(),
                localInvalidations.sum(),
                invalidationReportSucceeded.sum(),
                invalidationReportFailed.sum(),
                invalidationReceived.sum(),
                invalidationSelfIgnored.sum(),
                invalidationInvalid.sum(),
                invalidationWatchReconnect.sum(),
                invalidationWatchFailed.sum()
        );
    }
}

package cn.ayice.tmc.sdk;

import java.util.concurrent.atomic.LongAdder;

/**
 * SDK 核心效果指标。
 *
 * <p>指标设计只服务四张 Grafana 图：Key 访问总量、本地缓存命中总量、
 * 本地缓存命中率、QPS 和 RT。访问上报、热点监听、失效广播等内部通信动作仍然是
 * TMC 链路的一部分，但不再作为主观测指标暴露，避免看板信息过载。</p>
 */
public class TmcMetrics {

    /**
     * SDK 处理过的 Key 读请求总数，用于展示访问量和计算 QPS。
     */
    private final LongAdder totalGets = new LongAdder();

    /**
     * 热点 Key 命中本地缓存的总次数，用于和访问总量对比并计算本地缓存命中率。
     */
    private final LongAdder localCacheHits = new LongAdder();

    /**
     * SDK 读请求累计耗时，和 totalGets 一起用于计算平均 RT。
     */
    private final LongAdder readDurationNanos = new LongAdder();

    public void incrementTotalGets() {
        totalGets.increment();
    }

    public void incrementLocalCacheHits() {
        localCacheHits.increment();
    }

    /**
     * 记录一次 SDK get 调用耗时。
     *
     * <p>调用方传入纳秒值，便于直接使用 {@link System#nanoTime()} 的差值，
     * 避免毫秒精度在本地缓存命中这种极快路径上过于粗糙。</p>
     */
    public void recordReadDurationNanos(long nanos) {
        if (nanos > 0) {
            readDurationNanos.add(nanos);
        }
    }

    /**
     * 生成不可变指标快照，避免调用方直接持有 LongAdder。
     */
    public TmcMetricsSnapshot snapshot() {
        return new TmcMetricsSnapshot(
                totalGets.sum(),
                localCacheHits.sum(),
                readDurationNanos.sum()
        );
    }
}

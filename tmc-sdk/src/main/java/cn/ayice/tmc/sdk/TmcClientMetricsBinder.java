package cn.ayice.tmc.sdk;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.function.ToDoubleFunction;

/**
 * SDK Micrometer 指标绑定器。
 *
 * <p>业务读写路径只维护 {@link TmcMetrics}，本类负责把这些内存指标注册到
 * Micrometer。这样 Prometheus/Grafana 能观测 TMC 效果，同时业务代码不直接依赖监控系统。</p>
 */
public class TmcClientMetricsBinder {

    /**
     * SDK 内存指标源。
     */
    private final TmcMetrics metrics;

    /**
     * Micrometer 指标注册中心。
     */
    private final MeterRegistry registry;

    /**
     * SDK 指标公共标签，只使用低基数字段。
     */
    private final Tags tags;

    public TmcClientMetricsBinder(TmcProperties properties, TmcMetrics metrics, MeterRegistry registry) {
        this.metrics = metrics;
        this.registry = registry;
        this.tags = Tags.of(
                "app_name", properties.getAppName(),
                "client_id", properties.getClientId()
        );
        bind();
    }

    /**
     * 注册 SDK 核心观测指标。
     */
    private void bind() {
        counter("tmc.client.total.gets", "SDK get 总次数", snapshot -> snapshot.getTotalGets());
        counter("tmc.client.hot.key.gets", "热点 key get 次数", snapshot -> snapshot.getHotKeyGets());
        counter("tmc.client.local.cache.hits", "本地缓存命中次数", snapshot -> snapshot.getLocalCacheHits());
        counter("tmc.client.local.cache.misses", "热点 key 本地缓存未命中次数", snapshot -> snapshot.getLocalCacheMisses());
        counter("tmc.client.redis.gets", "真实回源 Redis 次数", snapshot -> snapshot.getRedisGets());
        counter("tmc.client.fallback.gets", "旁路异常后降级 Redis 次数", snapshot -> snapshot.getFallbackGets());
        counter("tmc.client.hot.key.snapshot.applied", "SDK 应用热点快照次数",
                snapshot -> snapshot.getHotKeySnapshotApplied());
        counter("tmc.client.local.invalidations", "当前节点主动删除本地缓存次数",
                snapshot -> snapshot.getLocalInvalidations());
        counter("tmc.client.invalidation.report.succeeded", "失效事件发布成功次数",
                snapshot -> snapshot.getInvalidationReportSucceeded());
        counter("tmc.client.invalidation.report.failed", "失效事件发布失败次数",
                snapshot -> snapshot.getInvalidationReportFailed());
        counter("tmc.client.invalidation.received", "处理其他节点失效事件次数",
                snapshot -> snapshot.getInvalidationReceived());
        counter("tmc.client.invalidation.self.ignored", "忽略自身失效事件次数",
                snapshot -> snapshot.getInvalidationSelfIgnored());
    }

    /**
     * 注册从 TmcMetricsSnapshot 读取累计值的 FunctionCounter。
     */
    private void counter(String name, String description, ToDoubleFunction<TmcMetricsSnapshot> value) {
        FunctionCounter.builder(name, metrics, currentMetrics -> value.applyAsDouble(currentMetrics.snapshot()))
                .description(description)
                .tags(tags)
                .register(registry);
    }
}

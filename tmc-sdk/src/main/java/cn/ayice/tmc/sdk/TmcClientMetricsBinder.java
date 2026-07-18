package cn.ayice.tmc.sdk;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.function.ToDoubleFunction;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * SDK Micrometer 指标绑定器。
 *
 * <p>业务读写路径只维护 {@link TmcMetrics}，本类负责把这些内存指标注册到
 * Micrometer。这样 Prometheus/Grafana 能观测 TMC 效果，同时业务代码不直接依赖监控系统。</p>
 */
public class TmcClientMetricsBinder implements SmartInitializingSingleton {

    /**
     * SDK 内存指标源。
     */
    private final TmcMetrics metrics;

    /**
     * Micrometer 指标注册中心。
     */
    private final MeterRegistry registry;

    /**
     * Spring Boot 自动配置场景下的延迟指标注册中心。
     *
     * <p>Actuator 的 MeterRegistry 可能在 SDK 自动配置条件判断之后才完成注册，
     * 因此自动配置路径不在构造方法里立即绑定，而是在所有单例初始化完成后再取真实 registry。</p>
     */
    private final ObjectProvider<MeterRegistry> registryProvider;

    /**
     * SDK 指标公共标签，只使用低基数字段。
     */
    private final Tags tags;

    public TmcClientMetricsBinder(TmcProperties properties, TmcMetrics metrics, MeterRegistry registry) {
        this.metrics = metrics;
        this.registry = registry;
        this.registryProvider = null;
        this.tags = Tags.of(
                "app_name", properties.getAppName(),
                "client_id", properties.getClientId()
        );
        bind(registry);
    }

    public TmcClientMetricsBinder(
            TmcProperties properties,
            TmcMetrics metrics,
            ObjectProvider<MeterRegistry> registryProvider
    ) {
        this.metrics = metrics;
        this.registry = null;
        this.registryProvider = registryProvider;
        this.tags = Tags.of(
                "app_name", properties.getAppName(),
                "client_id", properties.getClientId()
        );
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (registryProvider == null) {
            return;
        }
        MeterRegistry availableRegistry = registryProvider.getIfAvailable();
        if (availableRegistry != null) {
            bind(availableRegistry);
        }
    }

    /**
     * 注册 SDK 核心观测指标。
     */
    private void bind(MeterRegistry targetRegistry) {
        counter(targetRegistry, "tmc.client.total.gets", "SDK get 总次数", snapshot -> snapshot.getTotalGets());
        counter(targetRegistry, "tmc.client.hot.key.gets", "热点 key get 次数", snapshot -> snapshot.getHotKeyGets());
        counter(targetRegistry, "tmc.client.local.cache.hits", "本地缓存命中次数", snapshot -> snapshot.getLocalCacheHits());
        counter(targetRegistry, "tmc.client.local.cache.misses", "热点 key 本地缓存未命中次数",
                snapshot -> snapshot.getLocalCacheMisses());
        counter(targetRegistry, "tmc.client.redis.gets", "真实回源 Redis 次数", snapshot -> snapshot.getRedisGets());
        counter(targetRegistry, "tmc.client.fallback.gets", "旁路异常后降级 Redis 次数",
                snapshot -> snapshot.getFallbackGets());
        counter(targetRegistry, "tmc.client.hot.key.snapshot.applied", "SDK 应用热点快照次数",
                snapshot -> snapshot.getHotKeySnapshotApplied());
        counter(targetRegistry, "tmc.client.local.invalidations", "当前节点主动删除本地缓存次数",
                snapshot -> snapshot.getLocalInvalidations());
        counter(targetRegistry, "tmc.client.invalidation.report.succeeded", "失效事件发布成功次数",
                snapshot -> snapshot.getInvalidationReportSucceeded());
        counter(targetRegistry, "tmc.client.invalidation.report.failed", "失效事件发布失败次数",
                snapshot -> snapshot.getInvalidationReportFailed());
        counter(targetRegistry, "tmc.client.invalidation.received", "处理其他节点失效事件次数",
                snapshot -> snapshot.getInvalidationReceived());
        counter(targetRegistry, "tmc.client.invalidation.self.ignored", "忽略自身失效事件次数",
                snapshot -> snapshot.getInvalidationSelfIgnored());
    }

    /**
     * 注册从 TmcMetricsSnapshot 读取累计值的 FunctionCounter。
     */
    private void counter(
            MeterRegistry targetRegistry,
            String name,
            String description,
            ToDoubleFunction<TmcMetricsSnapshot> value
    ) {
        FunctionCounter.builder(name, metrics, currentMetrics -> value.applyAsDouble(currentMetrics.snapshot()))
                .description(description)
                .tags(tags)
                .register(targetRegistry);
    }
}

package cn.ayice.tmc.sdk;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.concurrent.TimeUnit;
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
     *
     * <p>这里只暴露最小效果指标：Key 访问总量、本地缓存命中总量和读耗时。
     * 本地缓存命中率、QPS、平均 RT 都在 Grafana 中通过 PromQL 计算。</p>
     */
    private void bind(MeterRegistry targetRegistry) {
        counter(targetRegistry, "tmc.sdk.key.request", "SDK 处理的 Key 读请求总数", snapshot -> snapshot.getTotalGets());
        counter(targetRegistry, "tmc.sdk.local.cache.hit", "热点 Key 命中本地缓存总次数",
                snapshot -> snapshot.getLocalCacheHits());
        FunctionTimer.builder(
                        "tmc.sdk.read.duration",
                        metrics,
                        currentMetrics -> currentMetrics.snapshot().getTotalGets(),
                        currentMetrics -> currentMetrics.snapshot().getReadDurationNanos(),
                        TimeUnit.NANOSECONDS
                )
                .description("SDK get 读请求累计耗时")
                .tags(tags)
                .register(targetRegistry);
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

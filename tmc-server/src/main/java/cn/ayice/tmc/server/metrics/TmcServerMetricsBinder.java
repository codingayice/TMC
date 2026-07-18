package cn.ayice.tmc.server.metrics;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.ToDoubleFunction;

/**
 * tmc-server Micrometer 指标绑定器。
 *
 * <p>服务端热点探测逻辑只维护 {@link TmcServerMetrics}，本类负责把核心指标注册到
 * Micrometer，供 Prometheus 采集和 Grafana 展示。</p>
 */
public class TmcServerMetricsBinder {

    /**
     * 服务端内存指标源。
     */
    private final TmcServerMetrics metrics;

    /**
     * Micrometer 指标注册中心。
     */
    private final MeterRegistry registry;

    public TmcServerMetricsBinder(TmcServerMetrics metrics, MeterRegistry registry) {
        this.metrics = metrics;
        this.registry = registry;
        bind();
    }

    /**
     * 注册服务端核心观测指标。
     */
    private void bind() {
        counter("tmc.server.messages.consumed", "Kafka 消费消息总数", snapshot -> snapshot.getMessagesConsumed());
        counter("tmc.server.messages.invalid", "非法访问事件消息数", snapshot -> snapshot.getMessagesInvalid());
        counter("tmc.server.messages.failed", "消费处理失败数", snapshot -> snapshot.getMessagesFailed());
        counter("tmc.server.access.events.accumulated", "进入热点累加器的访问事件数",
                snapshot -> snapshot.getAccessEventsAccumulated());
        counter("tmc.server.mapping.runs", "滑窗映射任务执行次数", snapshot -> snapshot.getMappingRuns());
        counter("tmc.server.mapping.failed", "滑窗映射任务失败次数", snapshot -> snapshot.getMappingFailed());
        counter("tmc.server.hot.key.publish.succeeded", "热点快照发布成功次数",
                snapshot -> snapshot.getHotKeyPublishSucceeded());
        counter("tmc.server.hot.key.publish.failed", "热点快照发布失败次数",
                snapshot -> snapshot.getHotKeyPublishFailed());
        gauge("tmc.server.tracked.apps", "当前追踪 app 数", snapshot -> snapshot.getTrackedApps());
        gauge("tmc.server.tracked.keys", "当前追踪 key 数", snapshot -> snapshot.getTrackedKeys());
        gauge("tmc.server.hot.keys.detected", "最近一次识别出的热点 key 数", snapshot -> snapshot.getHotKeysDetected());
    }

    private void counter(String name, String description, ToDoubleFunction<TmcServerMetricsSnapshot> value) {
        FunctionCounter.builder(name, metrics, currentMetrics -> value.applyAsDouble(currentMetrics.snapshot()))
                .description(description)
                .register(registry);
    }

    private void gauge(String name, String description, ToDoubleFunction<TmcServerMetricsSnapshot> value) {
        Gauge.builder(name, metrics, currentMetrics -> value.applyAsDouble(currentMetrics.snapshot()))
                .description(description)
                .register(registry);
    }
}

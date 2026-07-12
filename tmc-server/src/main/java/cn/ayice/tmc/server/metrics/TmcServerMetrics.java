package cn.ayice.tmc.server.metrics;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * tmc-server 内存指标。
 *
 * <p>当前先用 AtomicLong 保存核心计数，后续接入 Micrometer/Prometheus 时可以把这些
 * 指标注册出去。指标必须是旁路能力，不能影响 Kafka 消费和热点探测主流程。</p>
 */
@Component
public class TmcServerMetrics {

    private final AtomicLong messagesConsumed = new AtomicLong();
    private final AtomicLong messagesInvalid = new AtomicLong();
    private final AtomicLong messagesFailed = new AtomicLong();
    private final AtomicLong accessEventsAccumulated = new AtomicLong();
    private final AtomicLong mappingRuns = new AtomicLong();
    private final AtomicLong mappingFailed = new AtomicLong();
    private final AtomicLong trackedApps = new AtomicLong();
    private final AtomicLong trackedKeys = new AtomicLong();
    private final AtomicLong hotKeysDetected = new AtomicLong();

    public void incrementMessagesConsumed() {
        messagesConsumed.incrementAndGet();
    }

    public void incrementMessagesInvalid() {
        messagesInvalid.incrementAndGet();
    }

    public void incrementMessagesFailed() {
        messagesFailed.incrementAndGet();
    }

    public void incrementAccessEventsAccumulated() {
        accessEventsAccumulated.incrementAndGet();
    }

    public void incrementMappingRuns() {
        mappingRuns.incrementAndGet();
    }

    public void incrementMappingFailed() {
        mappingFailed.incrementAndGet();
    }

    public void updateTracked(long appCount, long keyCount) {
        trackedApps.set(appCount);
        trackedKeys.set(keyCount);
    }

    public void setHotKeysDetected(long value) {
        hotKeysDetected.set(value);
    }

    public TmcServerMetricsSnapshot snapshot() {
        return new TmcServerMetricsSnapshot(
                messagesConsumed.get(),
                messagesInvalid.get(),
                messagesFailed.get(),
                accessEventsAccumulated.get(),
                mappingRuns.get(),
                mappingFailed.get(),
                trackedApps.get(),
                trackedKeys.get(),
                hotKeysDetected.get()
        );
    }
}

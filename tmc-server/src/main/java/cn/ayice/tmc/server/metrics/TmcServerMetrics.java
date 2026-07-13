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

    /**
     * Kafka listener 收到的消息数量。
     */
    private final AtomicLong messagesConsumed = new AtomicLong();

    /**
     * JSON 或字段校验失败的消息数量。
     */
    private final AtomicLong messagesInvalid = new AtomicLong();

    /**
     * 消费过程中发生非预期异常的消息数量。
     */
    private final AtomicLong messagesFailed = new AtomicLong();

    /**
     * 成功进入访问热度累加器的事件数量。
     */
    private final AtomicLong accessEventsAccumulated = new AtomicLong();

    /**
     * 热点映射调度成功执行次数。
     */
    private final AtomicLong mappingRuns = new AtomicLong();

    /**
     * 热点映射调度失败次数。
     */
    private final AtomicLong mappingFailed = new AtomicLong();

    /**
     * 当前服务端追踪的应用数量。
     */
    private final AtomicLong trackedApps = new AtomicLong();

    /**
     * 当前服务端追踪的 key 数量。
     */
    private final AtomicLong trackedKeys = new AtomicLong();

    /**
     * 最近一次探测得到的热点 key 数量。
     */
    private final AtomicLong hotKeysDetected = new AtomicLong();

    /**
     * 热点快照成功写入 etcd 的次数。
     */
    private final AtomicLong hotKeyPublishSucceeded = new AtomicLong();

    /**
     * 热点快照写入 etcd 失败的次数。
     */
    private final AtomicLong hotKeyPublishFailed = new AtomicLong();

    /**
     * 热点快照从 etcd 删除成功的次数。
     */
    private final AtomicLong hotKeyDeleteSucceeded = new AtomicLong();

    /**
     * 热点快照从 etcd 删除失败的次数。
     */
    private final AtomicLong hotKeyDeleteFailed = new AtomicLong();

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

    public void incrementHotKeyPublishSucceeded() {
        hotKeyPublishSucceeded.incrementAndGet();
    }

    public void incrementHotKeyPublishFailed() {
        hotKeyPublishFailed.incrementAndGet();
    }

    public void incrementHotKeyDeleteSucceeded() {
        hotKeyDeleteSucceeded.incrementAndGet();
    }

    public void incrementHotKeyDeleteFailed() {
        hotKeyDeleteFailed.incrementAndGet();
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
                hotKeysDetected.get(),
                hotKeyPublishSucceeded.get(),
                hotKeyPublishFailed.get(),
                hotKeyDeleteSucceeded.get(),
                hotKeyDeleteFailed.get()
        );
    }
}

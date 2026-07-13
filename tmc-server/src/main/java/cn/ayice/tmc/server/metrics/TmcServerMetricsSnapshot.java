package cn.ayice.tmc.server.metrics;

/**
 * tmc-server 指标快照。
 *
 * <p>使用不可变对象承载一次读取结果，避免调用方读到一半时各个 AtomicLong 又发生变化。</p>
 */
public class TmcServerMetricsSnapshot {

    /**
     * Kafka listener 收到的消息数量。
     */
    private final long messagesConsumed;

    /**
     * JSON 或字段校验失败的消息数量。
     */
    private final long messagesInvalid;

    /**
     * 消费过程中发生非预期异常的消息数量。
     */
    private final long messagesFailed;

    /**
     * 成功进入访问热度累加器的事件数量。
     */
    private final long accessEventsAccumulated;

    /**
     * 热点映射调度成功执行次数。
     */
    private final long mappingRuns;

    /**
     * 热点映射调度失败次数。
     */
    private final long mappingFailed;

    /**
     * 当前追踪应用数量。
     */
    private final long trackedApps;

    /**
     * 当前追踪 key 数量。
     */
    private final long trackedKeys;

    /**
     * 最近一次探测得到的热点 key 数量。
     */
    private final long hotKeysDetected;

    /**
     * 热点快照发布成功次数。
     */
    private final long hotKeyPublishSucceeded;

    /**
     * 热点快照发布失败次数。
     */
    private final long hotKeyPublishFailed;

    /**
     * 热点快照删除成功次数。
     */
    private final long hotKeyDeleteSucceeded;

    /**
     * 热点快照删除失败次数。
     */
    private final long hotKeyDeleteFailed;

    public TmcServerMetricsSnapshot(
            long messagesConsumed,
            long messagesInvalid,
            long messagesFailed,
            long accessEventsAccumulated,
            long mappingRuns,
            long mappingFailed,
            long trackedApps,
            long trackedKeys,
            long hotKeysDetected,
            long hotKeyPublishSucceeded,
            long hotKeyPublishFailed,
            long hotKeyDeleteSucceeded,
            long hotKeyDeleteFailed
    ) {
        this.messagesConsumed = messagesConsumed;
        this.messagesInvalid = messagesInvalid;
        this.messagesFailed = messagesFailed;
        this.accessEventsAccumulated = accessEventsAccumulated;
        this.mappingRuns = mappingRuns;
        this.mappingFailed = mappingFailed;
        this.trackedApps = trackedApps;
        this.trackedKeys = trackedKeys;
        this.hotKeysDetected = hotKeysDetected;
        this.hotKeyPublishSucceeded = hotKeyPublishSucceeded;
        this.hotKeyPublishFailed = hotKeyPublishFailed;
        this.hotKeyDeleteSucceeded = hotKeyDeleteSucceeded;
        this.hotKeyDeleteFailed = hotKeyDeleteFailed;
    }

    public long getMessagesConsumed() {
        return messagesConsumed;
    }

    public long getMessagesInvalid() {
        return messagesInvalid;
    }

    public long getMessagesFailed() {
        return messagesFailed;
    }

    public long getAccessEventsAccumulated() {
        return accessEventsAccumulated;
    }

    public long getMappingRuns() {
        return mappingRuns;
    }

    public long getMappingFailed() {
        return mappingFailed;
    }

    public long getTrackedApps() {
        return trackedApps;
    }

    public long getTrackedKeys() {
        return trackedKeys;
    }

    public long getHotKeysDetected() {
        return hotKeysDetected;
    }

    public long getHotKeyPublishSucceeded() {
        return hotKeyPublishSucceeded;
    }

    public long getHotKeyPublishFailed() {
        return hotKeyPublishFailed;
    }

    public long getHotKeyDeleteSucceeded() {
        return hotKeyDeleteSucceeded;
    }

    public long getHotKeyDeleteFailed() {
        return hotKeyDeleteFailed;
    }
}

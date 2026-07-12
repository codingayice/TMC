package cn.ayice.tmc.server.metrics;

/**
 * tmc-server 指标快照。
 *
 * <p>使用不可变对象承载一次读取结果，避免调用方读到一半时各个 AtomicLong 又发生变化。</p>
 */
public class TmcServerMetricsSnapshot {

    private final long messagesConsumed;
    private final long messagesInvalid;
    private final long messagesFailed;
    private final long accessEventsAccumulated;
    private final long mappingRuns;
    private final long mappingFailed;
    private final long trackedApps;
    private final long trackedKeys;
    private final long hotKeysDetected;

    public TmcServerMetricsSnapshot(
            long messagesConsumed,
            long messagesInvalid,
            long messagesFailed,
            long accessEventsAccumulated,
            long mappingRuns,
            long mappingFailed,
            long trackedApps,
            long trackedKeys,
            long hotKeysDetected
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
}

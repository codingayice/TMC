package cn.ayice.tmc.communication;

/**
 * SDK 本地缓存失效广播配置。
 *
 * <p>失效广播用于在某个客户端写 Redis 成功后通知其他客户端删除本地缓存。
 * 该能力是旁路一致性能力，可以独立关闭发布或监听，但不能影响业务 Redis 写路径。</p>
 */
public class InvalidationProperties {

    /**
     * 是否启用失效广播整体能力。
     */
    private boolean enabled = true;

    /**
     * 是否发布当前节点写操作产生的失效事件。
     */
    private boolean reportEnabled = true;

    /**
     * 是否监听其他节点发布的失效事件。
     */
    private boolean listenEnabled = true;

    /**
     * 失效事件在 etcd 中保留的时间，单位秒。
     */
    private long eventTtlSeconds = 30L;

    /**
     * etcd watch 失败后的重连等待时间，单位毫秒。
     */
    private long reconnectDelayMillis = 3000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isReportEnabled() {
        return reportEnabled;
    }

    public void setReportEnabled(boolean reportEnabled) {
        this.reportEnabled = reportEnabled;
    }

    public boolean isListenEnabled() {
        return listenEnabled;
    }

    public void setListenEnabled(boolean listenEnabled) {
        this.listenEnabled = listenEnabled;
    }

    public long getEventTtlSeconds() {
        return eventTtlSeconds;
    }

    public void setEventTtlSeconds(long eventTtlSeconds) {
        if (eventTtlSeconds <= 0) {
            throw new IllegalArgumentException("eventTtlSeconds must be positive");
        }
        this.eventTtlSeconds = eventTtlSeconds;
    }

    public long getReconnectDelayMillis() {
        return reconnectDelayMillis;
    }

    public void setReconnectDelayMillis(long reconnectDelayMillis) {
        if (reconnectDelayMillis <= 0) {
            throw new IllegalArgumentException("reconnectDelayMillis must be positive");
        }
        this.reconnectDelayMillis = reconnectDelayMillis;
    }
}

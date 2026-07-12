package cn.ayice.tmc.communication;

/**
 * 访问事件上报配置。
 *
 * <p>SDK 业务线程只负责把事件放入有界队列，后台线程再批量写入 rsyslog。
 * 这些参数决定队列容量、批量大小和后台线程空闲等待时间。</p>
 */
public class AccessReportProperties {

    /**
     * 是否启用访问事件上报。
     */
    private boolean enabled = true;

    /**
     * 有界队列容量。队列满时丢弃访问事件，不能阻塞业务读请求。
     */
    private int queueCapacity = 10000;

    /**
     * 后台线程单次最多写出多少条访问事件。
     */
    private int batchSize = 100;

    /**
     * 队列空闲时后台线程最多等待多久再检查一次。
     */
    private long flushIntervalMillis = 100;

    /**
     * rsyslog TCP 写入配置。
     */
    private RsyslogProperties rsyslog = new RsyslogProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        this.queueCapacity = queueCapacity;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
    }

    public long getFlushIntervalMillis() {
        return flushIntervalMillis;
    }

    public void setFlushIntervalMillis(long flushIntervalMillis) {
        if (flushIntervalMillis <= 0) {
            throw new IllegalArgumentException("flushIntervalMillis must be positive");
        }
        this.flushIntervalMillis = flushIntervalMillis;
    }

    public RsyslogProperties getRsyslog() {
        return rsyslog;
    }

    public void setRsyslog(RsyslogProperties rsyslog) {
        if (rsyslog == null) {
            throw new IllegalArgumentException("rsyslog must not be null");
        }
        this.rsyslog = rsyslog;
    }
}

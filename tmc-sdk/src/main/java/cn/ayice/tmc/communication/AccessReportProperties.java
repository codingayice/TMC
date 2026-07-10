package cn.ayice.tmc.communication;

public class AccessReportProperties {

    private boolean enabled = true;

    private int queueCapacity = 10000;

    private int batchSize = 100;

    private long flushIntervalMillis = 100;

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

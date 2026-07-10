package cn.ayice.tmc.hotkey;

public class HotKeyProperties {

    /**
     * 本地热key过期时间
     */
    private long ttlMillis = 30_000;

    public long getTtlMillis() {
        return ttlMillis;
    }

    public void setTtlMillis(long ttlMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttlMillis must be positive");
        }
        this.ttlMillis = ttlMillis;
    }
}

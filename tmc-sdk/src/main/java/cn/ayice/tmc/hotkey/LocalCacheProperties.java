package cn.ayice.tmc.hotkey;

public class LocalCacheProperties {

    /**
     * 是否启用本地缓存
     */
    private boolean enabled = true;

    /**
     * 本地缓存最多能放多少key
     */
    private long maximumSize = 10_000;

    /**
     * 本地缓存value写入后多久过期
     */
    private long expireAfterWriteMillis = 30_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        this.maximumSize = maximumSize;
    }

    public long getExpireAfterWriteMillis() {
        return expireAfterWriteMillis;
    }

    public void setExpireAfterWriteMillis(long expireAfterWriteMillis) {
        if (expireAfterWriteMillis <= 0) {
            throw new IllegalArgumentException("expireAfterWriteMillis must be positive");
        }
        this.expireAfterWriteMillis = expireAfterWriteMillis;
    }
}

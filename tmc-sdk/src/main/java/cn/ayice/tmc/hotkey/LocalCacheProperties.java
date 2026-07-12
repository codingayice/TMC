package cn.ayice.tmc.hotkey;

/**
 * SDK 本地缓存配置。
 *
 * <p>本地缓存只缓存热点 key 的值，用来降低 Redis 压力。</p>
 */
public class LocalCacheProperties {

    /**
     * 是否启用本地缓存。
     */
    private boolean enabled = true;

    /**
     * 本地缓存最多能放多少 key。
     */
    private long maximumSize = 10_000;

    /**
     * 本地缓存 value 写入后多久过期。
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

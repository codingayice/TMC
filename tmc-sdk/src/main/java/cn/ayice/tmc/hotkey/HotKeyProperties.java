package cn.ayice.tmc.hotkey;

/**
 * SDK 侧热点 key 配置。
 */
public class HotKeyProperties {

    /**
     * 本地热点 key 过期时间。
     *
     * <p>即使服务端没有及时下发新快照，旧热点也会在 SDK 本地自然过期。</p>
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

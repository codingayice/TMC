package cn.ayice.tmc.hotkey;

import cn.ayice.tmc.communication.HotKeyDiscoveryProperties;

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

    /**
     * 热点发现监听配置。
     */
    private HotKeyDiscoveryProperties discovery = new HotKeyDiscoveryProperties();

    public long getTtlMillis() {
        return ttlMillis;
    }

    public void setTtlMillis(long ttlMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttlMillis must be positive");
        }
        this.ttlMillis = ttlMillis;
    }

    public HotKeyDiscoveryProperties getDiscovery() {
        return discovery;
    }

    public void setDiscovery(HotKeyDiscoveryProperties discovery) {
        if (discovery == null) {
            throw new IllegalArgumentException("discovery must not be null");
        }
        this.discovery = discovery;
    }
}

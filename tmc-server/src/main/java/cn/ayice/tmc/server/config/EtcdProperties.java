package cn.ayice.tmc.server.config;

import java.util.ArrayList;
import java.util.List;

/**
 * tmc-server 侧 etcd 发布配置。
 *
 * <p>服务端通过 etcd 发布热点快照，SDK 通过相同路径监听快照变化。
 * 配置集中在这里，避免热点调度器直接感知连接细节。</p>
 */
public class EtcdProperties {

    /**
     * 是否启用热点快照发布。
     */
    private boolean enabled = true;

    /**
     * etcd 地址列表。
     */
    private List<String> endpoints = new ArrayList<>(List.of("http://localhost:2379"));

    /**
     * 建立连接的超时时间，单位毫秒。
     */
    private long connectTimeoutMillis = 3000L;

    /**
     * 热点快照 key 的租约 TTL，单位秒。
     */
    private long hotKeyLeaseTtlSeconds = 30L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<String> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("etcd endpoints must not be empty");
        }
        this.endpoints = endpoints;
    }

    public long getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(long connectTimeoutMillis) {
        if (connectTimeoutMillis <= 0) {
            throw new IllegalArgumentException("connectTimeoutMillis must be positive");
        }
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public long getHotKeyLeaseTtlSeconds() {
        return hotKeyLeaseTtlSeconds;
    }

    public void setHotKeyLeaseTtlSeconds(long hotKeyLeaseTtlSeconds) {
        if (hotKeyLeaseTtlSeconds <= 0) {
            throw new IllegalArgumentException("hotKeyLeaseTtlSeconds must be positive");
        }
        this.hotKeyLeaseTtlSeconds = hotKeyLeaseTtlSeconds;
    }
}

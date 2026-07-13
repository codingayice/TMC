package cn.ayice.tmc.communication;

/**
 * SDK 热点发现监听配置。
 *
 * <p>热点发现负责从 etcd 获取服务端发布的 HotKeySnapshot。它是旁路通信能力，
 * 因此必须允许独立关闭，并且异常时不能阻塞业务读请求。</p>
 */
public class HotKeyDiscoveryProperties {

    /**
     * 是否启用热点发现监听。
     */
    private boolean enabled = true;

    /**
     * 启动监听前是否先读取一次当前快照，避免 watch 启动前已有热点但 SDK 不知道。
     */
    private boolean loadOnStartup = true;

    /**
     * watch 失败后的重连等待时间，单位毫秒。
     */
    private long reconnectDelayMillis = 3000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLoadOnStartup() {
        return loadOnStartup;
    }

    public void setLoadOnStartup(boolean loadOnStartup) {
        this.loadOnStartup = loadOnStartup;
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

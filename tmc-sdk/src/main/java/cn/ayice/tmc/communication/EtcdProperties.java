package cn.ayice.tmc.communication;

import java.util.ArrayList;
import java.util.List;

/**
 * SDK 侧 etcd 连接配置。
 *
 * <p>热点发现监听和后续缓存失效监听都会复用这组连接参数。配置放在通信模块，
 * 因为 etcd 属于 SDK 与服务端之间的数据通道，不属于热点内存管理本身。</p>
 */
public class EtcdProperties {

    /**
     * etcd 服务地址列表，支持多个 endpoint 以便后续接入 etcd 集群。
     */
    private List<String> endpoints = new ArrayList<>(List.of("http://localhost:2379"));

    /**
     * 建立连接的超时时间，单位毫秒。
     */
    private long connectTimeoutMillis = 3000L;

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
}

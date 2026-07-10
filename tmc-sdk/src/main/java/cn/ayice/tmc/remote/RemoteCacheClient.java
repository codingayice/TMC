package cn.ayice.tmc.remote;

/**
 * Redis 客户端统一抽象
 */
public interface RemoteCacheClient {
    String get(String key);
}

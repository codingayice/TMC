package cn.ayice.tmc.cache;

/**
 * 本地缓存接口
 */
public interface LocalCache {

    String getIfPresent(String key);

    void put(String key, String value);

    void invalidate(String key);

    long estimatedSize();
}

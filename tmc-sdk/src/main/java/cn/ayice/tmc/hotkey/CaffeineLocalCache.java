package cn.ayice.tmc.hotkey;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;

public class CaffeineLocalCache {

    private final Cache<String, String> cache;

    public CaffeineLocalCache(long maximumSize, long expireAfterWriteMillis) {
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        if (expireAfterWriteMillis <= 0) {
            throw new IllegalArgumentException("expireAfterWriteMillis must be positive");
        }
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(Duration.ofMillis(expireAfterWriteMillis))
                .build();
    }

    public String getIfPresent(String key) {
        return cache.getIfPresent(key);
    }

    public void put(String key, String value) {
        if (value == null) {
            return;
        }
        cache.put(key, value);
    }

    public void invalidate(String key) {
        cache.invalidate(key);
    }

    public long estimatedSize() {
        return cache.estimatedSize();
    }
}

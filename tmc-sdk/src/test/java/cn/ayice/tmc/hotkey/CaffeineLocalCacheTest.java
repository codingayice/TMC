package cn.ayice.tmc.hotkey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * CaffeineLocalCache 本地缓存测试。
 *
 * <p>保障热点 key 缓存的基础读写、容量限制和过期行为符合 SDK 读路径预期。</p>
 */
class CaffeineLocalCacheTest {

    @Test
    void shouldStoreAndReadValue() {
        CaffeineLocalCache cache = new CaffeineLocalCache(100, 30_000);

        cache.put("product:1", "value-1");

        assertEquals("value-1", cache.getIfPresent("product:1"));
        assertEquals(1, cache.estimatedSize());
    }

    @Test
    void shouldInvalidateValue() {
        CaffeineLocalCache cache = new CaffeineLocalCache(100, 30_000);
        cache.put("product:1", "value-1");

        cache.invalidate("product:1");

        assertNull(cache.getIfPresent("product:1"));
    }

    @Test
    void shouldNotCacheNullValue() {
        CaffeineLocalCache cache = new CaffeineLocalCache(100, 30_000);

        cache.put("product:1", null);

        assertNull(cache.getIfPresent("product:1"));
        assertEquals(0, cache.estimatedSize());
    }
}

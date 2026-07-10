package cn.ayice.tmc.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

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

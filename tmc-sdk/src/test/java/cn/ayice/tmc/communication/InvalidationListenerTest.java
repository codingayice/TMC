package cn.ayice.tmc.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import cn.ayice.tmc.enums.CacheOperation;
import cn.ayice.tmc.hotkey.CaffeineLocalCache;
import cn.ayice.tmc.model.InvalidationEvent;
import cn.ayice.tmc.sdk.TmcMetrics;
import cn.ayice.tmc.util.JsonUtils;
import org.junit.jupiter.api.Test;

/**
 * InvalidationListener 失效事件监听测试。
 *
 * <p>该测试直接验证监听器处理 etcd value 的行为：只处理同 app、其他 client
 * 发布的合法事件；自身事件、其他 app 事件和非法 JSON 都不能误删本地缓存。</p>
 */
class InvalidationListenerTest {

    @Test
    void shouldInvalidateLocalCacheForOtherClientEvent() {
        CaffeineLocalCache localCache = new CaffeineLocalCache(100, 30_000);
        localCache.put("product:1", "old-value");
        TmcMetrics metrics = new TmcMetrics();
        InvalidationListener listener = InvalidationListener.forTest("product-service", "client-a", localCache, metrics);

        listener.applyEventJson(eventJson("product-service", "product:1", "client-b"));

        assertNull(localCache.getIfPresent("product:1"));
        assertEquals(1L, metrics.snapshot().getInvalidationReceived());
    }

    @Test
    void shouldIgnoreSelfEvent() {
        CaffeineLocalCache localCache = new CaffeineLocalCache(100, 30_000);
        localCache.put("product:1", "old-value");
        TmcMetrics metrics = new TmcMetrics();
        InvalidationListener listener = InvalidationListener.forTest("product-service", "client-a", localCache, metrics);

        listener.applyEventJson(eventJson("product-service", "product:1", "client-a"));

        assertEquals("old-value", localCache.getIfPresent("product:1"));
        assertEquals(1L, metrics.snapshot().getInvalidationSelfIgnored());
    }

    @Test
    void shouldIgnoreOtherAppEvent() {
        CaffeineLocalCache localCache = new CaffeineLocalCache(100, 30_000);
        localCache.put("product:1", "old-value");
        TmcMetrics metrics = new TmcMetrics();
        InvalidationListener listener = InvalidationListener.forTest("product-service", "client-a", localCache, metrics);

        listener.applyEventJson(eventJson("order-service", "product:1", "client-b"));

        assertEquals("old-value", localCache.getIfPresent("product:1"));
        assertEquals(0L, metrics.snapshot().getInvalidationReceived());
    }

    @Test
    void shouldRecordInvalidJson() {
        CaffeineLocalCache localCache = new CaffeineLocalCache(100, 30_000);
        TmcMetrics metrics = new TmcMetrics();
        InvalidationListener listener = InvalidationListener.forTest("product-service", "client-a", localCache, metrics);

        listener.applyEventJson("{bad json");

        assertEquals(1L, metrics.snapshot().getInvalidationInvalid());
    }

    private static String eventJson(String appName, String key, String clientId) {
        return JsonUtils.toJson(new InvalidationEvent(
                appName,
                key,
                CacheOperation.SET,
                clientId,
                System.currentTimeMillis(),
                "event-1"
        ));
    }
}

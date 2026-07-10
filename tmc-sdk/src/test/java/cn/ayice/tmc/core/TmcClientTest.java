package cn.ayice.tmc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.ayice.tmc.cache.CaffeineLocalCache;
import cn.ayice.tmc.config.LocalCacheProperties;
import cn.ayice.tmc.config.TmcProperties;
import cn.ayice.tmc.hotkey.HotKeyManager;
import cn.ayice.tmc.metrics.TmcMetrics;
import cn.ayice.tmc.model.HotKey;
import cn.ayice.tmc.remote.RemoteCacheClient;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TmcClientTest {

    @Test
    void shouldReadNonHotKeyFromRemoteOnly() {
        CountingRemoteCacheClient remote = new CountingRemoteCacheClient(Map.of("product:1", "remote-value"));
        TmcClient client = newClient(remote);

        String value = client.get("product:1");

        assertEquals("remote-value", value);
        assertEquals(1, remote.count());
        assertEquals(1, client.metrics().getTotalGets());
        assertEquals(1, client.metrics().getRemoteGets());
    }

    @Test
    void shouldCacheHotKeyAfterRemoteMissAndHitLocalNextTime() {
        CountingRemoteCacheClient remote = new CountingRemoteCacheClient(Map.of("product:1", "remote-value"));
        TmcClient client = newClient(remote);
        client.addHotKey(new HotKey("product-service", "product:1", 100L, System.currentTimeMillis(), 30_000L));

        assertEquals("remote-value", client.get("product:1"));
        assertEquals("remote-value", client.get("product:1"));

        assertEquals(1, remote.count());
        assertEquals(1, client.metrics().getLocalCacheHits());
        assertEquals(1, client.metrics().getLocalCacheMisses());
    }

    @Test
    void shouldNotCacheNullRemoteValue() {
        CountingRemoteCacheClient remote = new CountingRemoteCacheClient(Map.of());
        TmcClient client = newClient(remote);
        client.addHotKey(new HotKey("product-service", "product:1", 100L, System.currentTimeMillis(), 30_000L));

        assertNull(client.get("product:1"));

        assertNull(client.get("product:1"));
        assertEquals(2, remote.count());
    }

    @Test
    void shouldBypassLocalCacheWhenDisabled() {
        CountingRemoteCacheClient remote = new CountingRemoteCacheClient(Map.of("product:1", "remote-value"));
        TmcProperties properties = properties();
        properties.getLocalCache().setEnabled(false);
        TmcClient client = newClient(properties, remote);
        client.addHotKey(new HotKey("product-service", "product:1", 100L, System.currentTimeMillis(), 30_000L));

        client.get("product:1");
        client.get("product:1");

        assertEquals(2, remote.count());
        assertEquals(0, client.metrics().getLocalCacheHits());
    }

    @Test
    void shouldInvalidateLocalValueWithoutRemovingHotKeyState() {
        CountingRemoteCacheClient remote = new CountingRemoteCacheClient(Map.of("product:1", "remote-value"));
        TmcClient client = newClient(remote);
        client.addHotKey(new HotKey("product-service", "product:1", 100L, System.currentTimeMillis(), 30_000L));
        client.get("product:1");

        client.invalidate("product:1");
        client.get("product:1");
        client.get("product:1");

        assertEquals(2, remote.count());
        assertEquals(1, client.metrics().getLocalCacheHits());
        assertEquals(2, client.metrics().getLocalCacheMisses());
    }

    @Test
    void shouldPropagateRemoteException() {
        TmcClient client = newClient(key -> {
            throw new IllegalStateException("redis down");
        });

        assertThrows(IllegalStateException.class, () -> client.get("product:1"));
    }

    private static TmcClient newClient(RemoteCacheClient remote) {
        return newClient(properties(), remote);
    }

    private static TmcClient newClient(TmcProperties properties, RemoteCacheClient remote) {
        return new TmcClient(
                properties,
                remote,
                new HotKeyManager(properties.getAppName(), properties.getHotKey().getTtlMillis()),
                new CaffeineLocalCache(
                        properties.getLocalCache().getMaximumSize(),
                        properties.getLocalCache().getExpireAfterWriteMillis()
                ),
                new TmcMetrics()
        );
    }

    private static TmcProperties properties() {
        TmcProperties properties = new TmcProperties();
        properties.setAppName("product-service");
        properties.setClientId("client-1");
        LocalCacheProperties localCache = new LocalCacheProperties();
        localCache.setEnabled(true);
        localCache.setMaximumSize(100);
        localCache.setExpireAfterWriteMillis(30_000);
        properties.setLocalCache(localCache);
        return properties;
    }

    private static final class CountingRemoteCacheClient implements RemoteCacheClient {

        private final Map<String, String> data = new ConcurrentHashMap<>();
        private final AtomicInteger count = new AtomicInteger();

        private CountingRemoteCacheClient(Map<String, String> data) {
            this.data.putAll(data);
        }

        @Override
        public String get(String key) {
            count.incrementAndGet();
            return data.get(key);
        }

        private int count() {
            return count.get();
        }
    }
}

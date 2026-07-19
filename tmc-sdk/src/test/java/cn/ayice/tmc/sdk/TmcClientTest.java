package cn.ayice.tmc.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.ayice.tmc.hotkey.CaffeineLocalCache;
import cn.ayice.tmc.hotkey.HotKeyManager;
import cn.ayice.tmc.hotkey.LocalCacheProperties;
import cn.ayice.tmc.communication.AccessReportProperties;
import cn.ayice.tmc.communication.AccessReporter;
import cn.ayice.tmc.communication.InvalidationProperties;
import cn.ayice.tmc.communication.InvalidationReporter;
import cn.ayice.tmc.enums.CacheOperation;
import cn.ayice.tmc.model.HotKey;
import cn.ayice.tmc.model.AccessEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * TmcClient 读路径测试。
 *
 * <p>这些测试覆盖热点判断、本地缓存命中/未命中、Redis 回源、降级和访问事件上报。
 * 指标断言只关注核心效果指标，内部通信动作不进入主观测指标。</p>
 */
class TmcClientTest {

    @Test
    void shouldReadNonHotKeyFromRemoteOnly() {
        CountingJedisReader jedis = new CountingJedisReader(Map.of("product:1", "jedis-value"));
        TmcClient client = newClient();

        String value = client.get("product:1", () -> jedis.get("product:1"));

        assertEquals("jedis-value", value);
        assertEquals(1, jedis.count());
        assertEquals(1, client.metrics().getTotalGets());
        assertTrue(client.metrics().getReadDurationNanos() > 0);
    }

    @Test
    void shouldCacheHotKeyAfterRemoteMissAndHitLocalNextTime() {
        CountingJedisReader jedis = new CountingJedisReader(Map.of("product:1", "jedis-value"));
        TmcClient client = newClient();
        client.addHotKey(new HotKey("product-service", "product:1", 100L, System.currentTimeMillis(), 30_000L));

        assertEquals("jedis-value", client.get("product:1", () -> jedis.get("product:1")));
        assertEquals("jedis-value", client.get("product:1", () -> jedis.get("product:1")));

        assertEquals(1, jedis.count());
        assertEquals(1, client.metrics().getLocalCacheHits());
    }

    @Test
    void shouldNotCacheNullRemoteValue() {
        CountingJedisReader jedis = new CountingJedisReader(Map.of());
        TmcClient client = newClient();
        client.addHotKey(new HotKey("product-service", "product:1", 100L, System.currentTimeMillis(), 30_000L));

        assertNull(client.get("product:1", () -> jedis.get("product:1")));

        assertNull(client.get("product:1", () -> jedis.get("product:1")));
        assertEquals(2, jedis.count());
    }

    @Test
    void shouldCoalesceConcurrentRemoteLoadsForHotKeyCacheMiss() throws Exception {
        int threadCount = 16;
        TmcClient client = newClient();
        client.addHotKey(new HotKey("product-service", "product:1", 100L, System.currentTimeMillis(), 30_000L));
        AtomicInteger remoteLoads = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(1, TimeUnit.SECONDS));
                return client.get("product:1", () -> {
                    remoteLoads.incrementAndGet();
                    sleepQuietly(100);
                    return "jedis-value";
                });
            }));
        }

        assertTrue(ready.await(1, TimeUnit.SECONDS));
        start.countDown();
        for (Future<String> future : futures) {
            assertEquals("jedis-value", future.get(1, TimeUnit.SECONDS));
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        assertEquals(1, remoteLoads.get());
    }

    @Test
    void shouldBypassLocalCacheWhenDisabled() {
        CountingJedisReader jedis = new CountingJedisReader(Map.of("product:1", "jedis-value"));
        TmcProperties properties = properties();
        properties.getLocalCache().setEnabled(false);
        TmcClient client = newClient(properties);
        client.addHotKey(new HotKey("product-service", "product:1", 100L, System.currentTimeMillis(), 30_000L));

        client.get("product:1", () -> jedis.get("product:1"));
        client.get("product:1", () -> jedis.get("product:1"));

        assertEquals(2, jedis.count());
        assertEquals(0, client.metrics().getLocalCacheHits());
    }

    @Test
    void shouldInvalidateLocalValueWithoutRemovingHotKeyState() {
        CountingJedisReader jedis = new CountingJedisReader(Map.of("product:1", "jedis-value"));
        TmcClient client = newClient();
        client.addHotKey(new HotKey("product-service", "product:1", 100L, System.currentTimeMillis(), 30_000L));
        client.get("product:1", () -> jedis.get("product:1"));

        client.invalidate("product:1");
        client.get("product:1", () -> jedis.get("product:1"));
        client.get("product:1", () -> jedis.get("product:1"));

        assertEquals(2, jedis.count());
        assertEquals(1, client.metrics().getLocalCacheHits());
    }

    @Test
    void shouldResetLocalHotKeysAndCache() {
        CountingJedisReader jedis = new CountingJedisReader(Map.of("product:1", "jedis-value"));
        TmcClient client = newClient();
        client.addHotKey(new HotKey("product-service", "product:1", 100L, System.currentTimeMillis(), 30_000L));
        client.get("product:1", () -> jedis.get("product:1"));
        client.get("product:1", () -> jedis.get("product:1"));

        client.resetLocalState();
        client.get("product:1", () -> jedis.get("product:1"));
        client.get("product:1", () -> jedis.get("product:1"));

        assertEquals(3, jedis.count());
        assertEquals(1, client.metrics().getLocalCacheHits());
    }

    @Test
    void shouldPropagateRemoteException() {
        TmcClient client = newClient();

        assertThrows(IllegalStateException.class, () -> client.get("product:1", () -> {
            throw new IllegalStateException("redis down");
        }));
    }

    @Test
    void shouldReportAccessEventBeforeReadingJedis() {
        CapturingAccessReporter reporter = new CapturingAccessReporter();
        TmcClient client = newClient(properties(), reporter);

        assertEquals("value", client.get("product:1", () -> "value"));

        assertEquals("product-service", reporter.event.getAppName());
        assertEquals("product:1", reporter.event.getKey());
        assertEquals("client-1", reporter.event.getClientId());
    }

    @Test
    void shouldIgnoreAccessReporterException() {
        TmcClient client = newClient(properties(), new ThrowingAccessReporter());

        assertEquals("value", client.get("product:1", () -> "value"));
        assertEquals(1, client.metrics().getTotalGets());
    }

    @Test
    void shouldInvalidateLocalCacheAndReportInvalidationAfterWrite() {
        CountingInvalidationReporter reporter = new CountingInvalidationReporter();
        TmcClient client = newClient(properties(), null, reporter);
        client.addHotKey(new HotKey("product-service", "product:1", 100L, System.currentTimeMillis(), 30_000L));
        client.get("product:1", () -> "old-value");

        client.invalidateAfterWrite("product:1", CacheOperation.SET);

        assertEquals(1, reporter.count.get());
        assertEquals(CacheOperation.SET, reporter.operation);
        assertEquals("new-value", client.get("product:1", () -> "new-value"));
    }

    @Test
    void shouldIgnoreInvalidationReporterException() {
        TmcClient client = newClient(properties(), null, new ThrowingInvalidationReporter());

        client.invalidateAfterWrite("product:1", CacheOperation.SET);

        assertEquals(0, client.metrics().getLocalCacheHits());
    }

    private static TmcClient newClient() {
        return newClient(properties());
    }

    private static TmcClient newClient(TmcProperties properties) {
        return newClient(properties, null);
    }

    private static TmcClient newClient(TmcProperties properties, AccessReporter reporter) {
        return newClient(properties, reporter, null);
    }

    private static TmcClient newClient(
            TmcProperties properties,
            AccessReporter reporter,
            InvalidationReporter invalidationReporter
    ) {
        return new TmcClient(
                properties,
                new HotKeyManager(properties.getAppName(), properties.getHotKey().getTtlMillis()),
                new CaffeineLocalCache(
                        properties.getLocalCache().getMaximumSize(),
                        properties.getLocalCache().getExpireAfterWriteMillis()
                ),
                new TmcMetrics(),
                reporter,
                invalidationReporter
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

    private static final class CountingJedisReader {

        private final Map<String, String> data = new ConcurrentHashMap<>();
        private final AtomicInteger count = new AtomicInteger();

        private CountingJedisReader(Map<String, String> data) {
            this.data.putAll(data);
        }

        public String get(String key) {
            count.incrementAndGet();
            return data.get(key);
        }

        private int count() {
            return count.get();
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test thread interrupted", e);
        }
    }

    private static final class CapturingAccessReporter extends AccessReporter {
        private AccessEvent event;

        private CapturingAccessReporter() {
            super(disabledReportProperties());
            close();
        }

        @Override
        public void report(AccessEvent event) {
            this.event = event;
        }
    }

    private static final class ThrowingAccessReporter extends AccessReporter {
        private ThrowingAccessReporter() {
            super(disabledReportProperties());
            close();
        }

        @Override
        public void report(AccessEvent event) {
            throw new IllegalStateException("report failed");
        }
    }

    private static final class CountingInvalidationReporter extends InvalidationReporter {
        private final AtomicInteger count = new AtomicInteger();
        private CacheOperation operation;

        private CountingInvalidationReporter() {
            super("product-service", "client-1", new InvalidationProperties());
        }

        @Override
        public void report(String key, CacheOperation operation) {
            count.incrementAndGet();
            this.operation = operation;
        }
    }

    private static final class ThrowingInvalidationReporter extends InvalidationReporter {
        private ThrowingInvalidationReporter() {
            super("product-service", "client-1", new InvalidationProperties());
        }

        @Override
        public void report(String key, CacheOperation operation) {
            throw new IllegalStateException("invalidation failed");
        }
    }

    private static AccessReportProperties disabledReportProperties() {
        AccessReportProperties properties = new AccessReportProperties();
        properties.setEnabled(false);
        return properties;
    }
}

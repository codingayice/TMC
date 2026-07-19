package cn.ayice.tmc.jedis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ayice.tmc.hotkey.CaffeineLocalCache;
import cn.ayice.tmc.hotkey.HotKeyManager;
import cn.ayice.tmc.hotkey.LocalCacheProperties;
import cn.ayice.tmc.model.HotKey;
import cn.ayice.tmc.sdk.TmcClient;
import cn.ayice.tmc.sdk.TmcMetrics;
import cn.ayice.tmc.sdk.TmcProperties;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * TmcJedisPool 池级透明接入测试。
 *
 * <p>这些测试保护一个关键性能边界：业务使用连接池接入 Redis 时，本地缓存命中不能提前借
 * Jedis 连接。否则即使 TMC 已经把热点数据放到 Caffeine，本地命中请求仍会被 JedisPool
 * 连接耗尽、跨机器 Redis 尾延迟等问题拖住。</p>
 */
class TmcJedisPoolTest {

    @Test
    void shouldBorrowJedisOnlyWhenTmcClientNeedsRemoteRead() {
        JedisPool jedisPool = mock(JedisPool.class);
        Jedis jedis = mock(Jedis.class);
        when(jedisPool.getResource()).thenReturn(jedis);
        when(jedis.get("product:1")).thenReturn("jedis-value");
        TmcClient tmcClient = newClient();
        tmcClient.addHotKey(new HotKey("product-service", "product:1", 100L, System.currentTimeMillis(), 30_000L));
        TmcJedisPool tmcJedisPool = new TmcJedisPool(jedisPool, tmcClient);

        assertEquals("jedis-value", tmcJedisPool.get("product:1"));
        assertEquals("jedis-value", tmcJedisPool.get("product:1"));

        verify(jedisPool, times(1)).getResource();
        verify(jedis, times(1)).get("product:1");
        verify(jedis, times(1)).close();
    }

    private static TmcClient newClient() {
        TmcProperties properties = new TmcProperties();
        properties.setAppName("product-service");
        properties.setClientId("client-1");
        LocalCacheProperties localCache = new LocalCacheProperties();
        localCache.setEnabled(true);
        localCache.setMaximumSize(100);
        localCache.setExpireAfterWriteMillis(30_000);
        properties.setLocalCache(localCache);
        return new TmcClient(
                properties,
                new HotKeyManager(properties.getAppName(), properties.getHotKey().getTtlMillis()),
                new CaffeineLocalCache(
                        properties.getLocalCache().getMaximumSize(),
                        properties.getLocalCache().getExpireAfterWriteMillis()
                ),
                new TmcMetrics()
        );
    }
}

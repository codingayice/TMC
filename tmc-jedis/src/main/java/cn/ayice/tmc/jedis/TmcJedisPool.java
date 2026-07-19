package cn.ayice.tmc.jedis;

import cn.ayice.tmc.sdk.TmcClient;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * JedisPool 风格的 TMC 透明接入层。
 *
 * <p>{@link TmcJedis} 适合调用方已经持有短生命周期 {@link Jedis} 的场景，但 Web 高并发读路径
 * 通常从 {@link JedisPool} 借连接。如果业务在进入 TMC 之前就先借 Jedis，本地缓存命中请求也会被
 * 连接池容量和 Redis 网络尾延迟影响。这个类把连接池借用动作放进 {@link TmcClient#get(String, java.util.function.Supplier)}
 * 的回源回调里，只有 TMC 判断确实需要 Redis 时才借连接。</p>
 *
 * <p>读操作会先经过 TMC 的热点判断和本地缓存；写操作必须真实访问 Redis，因此写成功后仍复用
 * {@link TmcJedis} 的写后失效逻辑，通知 SDK 删除本地缓存并广播失效事件。</p>
 */
public class TmcJedisPool {

    /**
     * 真实 Jedis 连接池。每次 Redis 网络访问都会借出一个短生命周期 Jedis，方法结束后立即归还。
     */
    private final JedisPool jedisPool;

    /**
     * TMC SDK 核心客户端，负责热点判断、本地缓存、访问上报和写后失效广播。
     */
    private final TmcClient tmcClient;

    public TmcJedisPool(JedisPool jedisPool, TmcClient tmcClient) {
        if (jedisPool == null) {
            throw new IllegalArgumentException("jedisPool must not be null");
        }
        if (tmcClient == null) {
            throw new IllegalArgumentException("tmcClient must not be null");
        }
        this.jedisPool = jedisPool;
        this.tmcClient = tmcClient;
    }

    /**
     * 透明读取 Redis 字符串值。
     *
     * <p>本地缓存命中时不会调用 {@link JedisPool#getResource()}；只有非热点 key、热点本地 miss、
     * 本地缓存异常等需要回源 Redis 的路径才会借连接。这样热点稳定态不会被 Redis 连接池拖慢。</p>
     */
    public String get(String key) {
        return tmcClient.get(key, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.get(key);
            }
        });
    }

    /**
     * 写入 Redis 字符串值，并在写成功后触发 TMC 本地缓存失效。
     */
    public String set(String key, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            return new TmcJedis(jedis, tmcClient).set(key, value);
        }
    }

    /**
     * 删除 Redis key，并在删除成功后触发 TMC 本地缓存失效。
     */
    public Long del(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return new TmcJedis(jedis, tmcClient).del(key);
        }
    }

    /**
     * 修改 Redis key 的过期时间，并在修改成功后触发 TMC 本地缓存失效。
     */
    public Long expire(String key, int seconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            return new TmcJedis(jedis, tmcClient).expire(key, seconds);
        }
    }
}

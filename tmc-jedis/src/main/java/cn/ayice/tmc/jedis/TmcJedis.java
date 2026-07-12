package cn.ayice.tmc.jedis;

import cn.ayice.tmc.sdk.TmcClient;
import redis.clients.jedis.Jedis;

/**
 * Jedis 风格透明接入层。
 *
 * <p>业务方仍然按 Jedis 的方式调用 get/set/del/expire。读操作委托给 {@link TmcClient}
 * 判断是否命中本地热点缓存；写操作先执行真实 Redis 命令，成功后通知 TMC 失效本地缓存。</p>
 */
public class TmcJedis {

    /**
     * 真实 Jedis 客户端，负责所有 Redis 网络读写。
     */
    private final Jedis jedis;

    /**
     * TMC SDK 核心客户端，负责热点判断、本地缓存和访问事件上报。
     */
    private final TmcClient tmcClient;

    public TmcJedis(Jedis jedis, TmcClient tmcClient) {
        if (jedis == null) {
            throw new IllegalArgumentException("jedis must not be null");
        }
        if (tmcClient == null) {
            throw new IllegalArgumentException("tmcClient must not be null");
        }
        this.jedis = jedis;
        this.tmcClient = tmcClient;
    }

    /**
     * 透明读取入口。
     *
     * <p>这里把原始 {@code jedis.get(key)} 作为回调交给 TmcClient：
     * 非热 key 或本地缓存未命中时，TmcClient 再执行该回调回源 Redis。</p>
     */
    public String get(String key) {
        return tmcClient.get(key, () -> jedis.get(key));
    }

    /**
     * 写入 Redis 成功后触发本地缓存失效。
     */
    public String set(String key, String value) {
        String result = jedis.set(key, value);
        if ("OK".equals(result)) {
            tmcClient.invalidate(key);
        }
        return result;
    }

    /**
     * 删除 Redis key 成功后触发本地缓存失效。
     */
    public Long del(String key) {
        Long deleted = jedis.del(key);
        if (deleted != null && deleted > 0) {
            tmcClient.invalidate(key);
        }
        return deleted;
    }

    /**
     * 修改 Redis key 过期时间成功后触发本地缓存失效。
     */
    public Long expire(String key, int seconds) {
        Long result = jedis.expire(key, seconds);
        if (result != null && result == 1) {
            tmcClient.invalidate(key);
        }
        return result;
    }
}

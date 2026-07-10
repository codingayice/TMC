package cn.ayice.tmc.jedis;

import cn.ayice.tmc.sdk.TmcClient;
import redis.clients.jedis.Jedis;

public class TmcJedis {

    private final Jedis jedis;
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

    public String get(String key) {
        return tmcClient.get(key, () -> jedis.get(key));
    }

    public String set(String key, String value) {
        String result = jedis.set(key, value);
        if ("OK".equals(result)) {
            tmcClient.invalidate(key);
        }
        return result;
    }

    public Long del(String key) {
        Long deleted = jedis.del(key);
        if (deleted != null && deleted > 0) {
            tmcClient.invalidate(key);
        }
        return deleted;
    }

    public Long expire(String key, int seconds) {
        Long result = jedis.expire(key, seconds);
        if (result != null && result == 1) {
            tmcClient.invalidate(key);
        }
        return result;
    }
}

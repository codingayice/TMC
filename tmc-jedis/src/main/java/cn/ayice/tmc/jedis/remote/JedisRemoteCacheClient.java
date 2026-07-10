package cn.ayice.tmc.jedis.remote;

import cn.ayice.tmc.remote.RemoteCacheClient;
import redis.clients.jedis.Jedis;

public class JedisRemoteCacheClient implements RemoteCacheClient {

    private final Jedis jedis;

    public JedisRemoteCacheClient(Jedis jedis) {
        if (jedis == null) {
            throw new IllegalArgumentException("jedis must not be null");
        }
        this.jedis = jedis;
    }

    @Override
    public String get(String key) {
        return jedis.get(key);
    }
}

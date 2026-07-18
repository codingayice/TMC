package cn.ayice.tmc.demo;

import cn.ayice.tmc.sdk.TmcClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * 限时抢购 Demo 基础设施装配。
 *
 * <p>Demo 使用 JedisPool 管理 Redis 连接，每次业务请求取一个短生命周期 Jedis 资源，
 * 再包装成 TmcJedis。这样既保留“业务像使用 Jedis 一样访问 Redis”的透明接入方式，
 * 又避免 Web 并发请求共享单个 Jedis 连接。</p>
 */
@Configuration
public class DemoConfiguration {

    /**
     * 创建 Demo 业务 Redis 连接池。
     */
    @Bean(destroyMethod = "close")
    public JedisPool demoJedisPool(DemoRedisProperties properties) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(32);
        poolConfig.setMaxIdle(16);
        poolConfig.setMinIdle(2);
        return new JedisPool(
                poolConfig,
                properties.getHost(),
                properties.getPort(),
                properties.getTimeoutMillis(),
                blankToNull(properties.getPassword()),
                properties.getDatabase()
        );
    }

    /**
     * 创建 Redis 网关。网关是 Demo 业务和 TmcJedis 的边界。
     */
    @Bean
    public ProductRedisGateway productRedisGateway(
            JedisPool demoJedisPool,
            TmcClient tmcClient,
            ObjectMapper objectMapper
    ) {
        return new ProductRedisGateway(demoJedisPool, tmcClient, objectMapper);
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}

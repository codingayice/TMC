package cn.ayice.tmc.demo;

import cn.ayice.tmc.jedis.TmcJedisPool;
import cn.ayice.tmc.sdk.TmcClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * 限时抢购 Demo 基础设施装配。
 *
 * <p>Demo 使用 JedisPool 管理 Redis 连接，并通过 TmcJedisPool 接入 TMC。
 * 读请求会先经过 SDK 的热点判断和本地缓存，只有确实需要 Redis 回源时才借 Jedis 连接；
 * 写请求仍然真实访问 Redis，并在成功后触发本地缓存失效。</p>
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
     * 创建池级 Jedis 透明适配器。
     *
     * <p>该 Bean 是 Demo 读路径避免提前占用 Redis 连接的关键：本地缓存命中时，
     * TmcClient 不会执行回源回调，因此这里不会从 JedisPool 借连接。</p>
     */
    @Bean
    public TmcJedisPool tmcJedisPool(JedisPool demoJedisPool, TmcClient tmcClient) {
        return new TmcJedisPool(demoJedisPool, tmcClient);
    }

    /**
     * 创建 Redis 网关。网关是 Demo 业务和 tmc-jedis 的边界。
     */
    @Bean
    public ProductRedisGateway productRedisGateway(
            TmcJedisPool tmcJedisPool,
            ObjectMapper objectMapper
    ) {
        return new ProductRedisGateway(tmcJedisPool, objectMapper);
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}

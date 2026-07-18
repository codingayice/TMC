package cn.ayice.tmc.demo;

import cn.ayice.tmc.jedis.TmcJedis;
import cn.ayice.tmc.sdk.TmcClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * 商品详情 Redis 网关。
 *
 * <p>该类是 Demo 业务进入 TMC 链路的唯一位置。读商品详情时使用 {@link TmcJedis#get(String)}，
 * 因此访问事件上报、热点判断、本地缓存命中、Redis 回源都会由 SDK 接管；
 * Grafana 只展示 Key 请求总数、本地缓存命中总数和 RT 等核心效果指标。
 * 初始化或修改商品详情时使用 {@link TmcJedis#set(String, String)}，写成功后会触发本地缓存失效广播。
 * 库存不经过本类缓存，避免把强一致数据放入本地缓存。</p>
 */
public class ProductRedisGateway {

    /**
     * Jedis 连接池。每次方法调用都独立借还连接，避免多线程共享 Jedis。
     */
    private final JedisPool jedisPool;

    /**
     * TMC SDK 客户端，由自动配置创建。
     */
    private final TmcClient tmcClient;

    /**
     * 商品详情 JSON 序列化器。
     */
    private final ObjectMapper objectMapper;

    public ProductRedisGateway(JedisPool jedisPool, TmcClient tmcClient, ObjectMapper objectMapper) {
        this.jedisPool = jedisPool;
        this.tmcClient = tmcClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 通过 TMC 透明读链路读取商品详情。
     */
    public ProductItem getProduct(String productId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = new TmcJedis(jedis, tmcClient).get(redisKey(productId));
            if (json == null || json.trim().isEmpty()) {
                return null;
            }
            return objectMapper.readValue(json, ProductItem.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("商品 JSON 解析失败", e);
        }
    }

    /**
     * 写入商品详情，并借助 TmcJedis 触发 SDK 写后失效。
     *
     * <p>该方法只用于初始化或维护详情数据，不用于库存扣减。</p>
     */
    public void saveProduct(ProductItem product) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = objectMapper.writeValueAsString(product);
            new TmcJedis(jedis, tmcClient).set(product.redisKey(), json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("商品 JSON 序列化失败", e);
        }
    }

    private static String redisKey(String productId) {
        return "product-detail:" + productId;
    }
}

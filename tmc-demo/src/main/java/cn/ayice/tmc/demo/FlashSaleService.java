package cn.ayice.tmc.demo;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 限时抢购 Demo 业务服务。
 *
 * <p>该类刻意只表达业务语义：初始化商品详情、读取商品详情、模拟查看详情、制造热点读流量。
 * 它不直接依赖 Redis，也不感知 Caffeine、rsyslog、Kafka、etcd 等 TMC 基础设施；
 * 这些复杂性都被 ProductRedisGateway 和 tmc-jedis 隐藏起来。</p>
 */
@Service
public class FlashSaleService {

    /**
     * 商品详情存储网关，内部通过 TmcJedis 进入透明缓存链路。
     */
    private final ProductRedisGateway gateway;

    public FlashSaleService(ProductRedisGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * 写入默认商品详情，方便演示环境一键恢复初始状态。
     */
    public List<ProductItem> seedProducts() {
        List<ProductItem> products = defaultProducts();
        for (ProductItem product : products) {
            gateway.saveProduct(product);
        }
        return products;
    }

    /**
     * 读取所有演示商品详情。每个商品详情都会经过 TMC 读路径。
     */
    public List<ProductItem> listProducts() {
        List<ProductItem> result = new ArrayList<>();
        for (ProductItem product : defaultProducts()) {
            ProductItem current = gateway.getProduct(product.getId());
            result.add(current == null ? product : current);
        }
        return result;
    }

    /**
     * 模拟用户点击商品卡片查看详情。
     *
     * <p>这里故意只读商品详情，不扣减库存、不写回 Redis。真实库存属于强一致数据，
     * 应由库存服务、Redis Lua 或数据库事务单独保护，不能作为本地多级缓存对象。
     * Demo 点击按钮只是多产生一次商品详情读取，用来观察热点发现与本地命中。</p>
     */
    public ProductDetailViewResult viewProductDetail(String productId) {
        ProductItem product = requireProduct(productId);
        return new ProductDetailViewResult(true, "已查看商品详情", product);
    }

    /**
     * 连续读取同一个商品详情，制造热点 key 访问事件。
     */
    public TrafficResult generateHotTraffic(String productId, int count) {
        int safeCount = Math.max(1, Math.min(count, 20_000));
        int success = 0;
        for (int i = 0; i < safeCount; i++) {
            if (gateway.getProduct(productId) != null) {
                success++;
            }
        }
        return new TrafficResult(productId, safeCount, success);
    }

    private ProductItem requireProduct(String productId) {
        ProductItem product = gateway.getProduct(productId);
        if (product == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        return product;
    }

    private static List<ProductItem> defaultProducts() {
        return List.of(
                new ProductItem(
                        "1001",
                        "Apple iPhone 15 128GB",
                        "A16芯片 | 6.1英寸 | 5G全网通",
                        4999,
                        5999,
                        "28.6 万人看过",
                        78,
                        "24期免息",
                        "/images/iphone.svg"
                ),
                new ProductItem(
                        "1002",
                        "Apple Watch Series 9",
                        "S9芯片 | 健康监测 | 50米防水",
                        2399,
                        2999,
                        "12.3 万人看过",
                        62,
                        "12期免息",
                        "/images/watch.svg"
                ),
                new ProductItem(
                        "1003",
                        "Apple AirPods Pro 2",
                        "主动降噪 | 通透模式 | 30小时续航",
                        1599,
                        1899,
                        "34.2 万人看过",
                        86,
                        "6期免息",
                        "/images/airpods.svg"
                )
        );
    }
}

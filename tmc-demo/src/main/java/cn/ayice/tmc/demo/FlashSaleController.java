package cn.ayice.tmc.demo;

import cn.ayice.tmc.sdk.TmcClient;
import cn.ayice.tmc.sdk.TmcMetricsSnapshot;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 限时抢购 Demo HTTP API。
 *
 * <p>前端页面只调用这些面向业务的接口，不直接调用 Redis 或 SDK。
 * 这样可以清楚展示 TMC 的价值：业务接口保持普通商品详情读取模型，缓存热点发现、
 * 本地缓存和跨节点失效都作为透明能力在旁路完成。</p>
 */
@RestController
@RequestMapping("/api/flash-sale")
public class FlashSaleController {

    /**
     * 商品详情演示业务服务。
     */
    private final FlashSaleService service;

    /**
     * TMC 客户端，用于给 Demo 页面展示即时指标快照。
     */
    private final TmcClient tmcClient;

    public FlashSaleController(FlashSaleService service, TmcClient tmcClient) {
        this.service = service;
        this.tmcClient = tmcClient;
    }

    @PostMapping("/seed")
    public List<ProductItem> seedProducts() {
        return service.seedProducts();
    }

    @GetMapping("/products")
    public List<ProductItem> listProducts() {
        return service.listProducts();
    }

    @PostMapping("/products/{productId}/detail-view")
    public ProductDetailViewResult viewProductDetail(@PathVariable("productId") String productId) {
        return service.viewProductDetail(productId);
    }

    @PostMapping("/traffic/hot-product/{productId}")
    public TrafficResult generateHotTraffic(
            @PathVariable("productId") String productId,
            @RequestParam(name = "count", defaultValue = "500") int count
    ) {
        return service.generateHotTraffic(productId, count);
    }

    /**
     * 清空当前 Demo 实例的 SDK 本地状态。
     *
     * <p>该接口只影响被请求到的这个 JVM：清空本地热点集合和 Caffeine 本地缓存，
     * 不删除 Redis 商品详情、不清理 etcd 热点快照，也不影响其他 Demo 节点。
     * 它用于演示前把客户端恢复到冷状态，重新观察热点发现链路。</p>
     */
    @PostMapping("/tmc/local-state/reset")
    public Map<String, String> resetLocalTmcState() {
        tmcClient.resetLocalState();
        return Map.of("message", "已清空本节点热点和本地缓存");
    }

    @GetMapping("/metrics")
    public TmcMetricsSnapshot metrics() {
        return tmcClient.metrics();
    }
}

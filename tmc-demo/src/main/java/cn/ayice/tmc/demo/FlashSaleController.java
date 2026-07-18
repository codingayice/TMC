package cn.ayice.tmc.demo;

import cn.ayice.tmc.sdk.TmcClient;
import cn.ayice.tmc.sdk.TmcMetricsSnapshot;
import java.util.List;
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
 * 这样可以清楚展示 TMC 的价值：业务接口保持普通商品读写模型，缓存热点发现、
 * 本地缓存和跨节点失效都作为透明能力在旁路完成。</p>
 */
@RestController
@RequestMapping("/api/flash-sale")
public class FlashSaleController {

    /**
     * 抢购业务服务。
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

    @PostMapping("/products/{productId}/purchase")
    public PurchaseResult purchase(@PathVariable("productId") String productId) {
        return service.purchase(productId);
    }

    @PostMapping("/traffic/hot-product/{productId}")
    public TrafficResult generateHotTraffic(
            @PathVariable("productId") String productId,
            @RequestParam(name = "count", defaultValue = "500") int count
    ) {
        return service.generateHotTraffic(productId, count);
    }

    @GetMapping("/metrics")
    public TmcMetricsSnapshot metrics() {
        return tmcClient.metrics();
    }
}

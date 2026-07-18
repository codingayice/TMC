package cn.ayice.tmc.demo;

/**
 * 抢购接口响应。
 *
 * <p>响应中保留更新后的商品快照，前端无需再次读取 Redis 就能刷新库存进度。
 * 后续下一次列表刷新仍会走 TmcJedis 读链路，用于观察写后失效是否生效。</p>
 */
public class PurchaseResult {

    /**
     * 是否抢购成功。
     */
    private final boolean success;

    /**
     * 给前端展示的结果文案。
     */
    private final String message;

    /**
     * 抢购后的商品快照。
     */
    private final ProductItem product;

    public PurchaseResult(boolean success, String message, ProductItem product) {
        this.success = success;
        this.message = message;
        this.product = product;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public ProductItem getProduct() {
        return product;
    }
}

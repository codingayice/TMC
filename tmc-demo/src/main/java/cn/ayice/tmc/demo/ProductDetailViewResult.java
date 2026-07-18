package cn.ayice.tmc.demo;

/**
 * 商品详情查看接口响应。
 *
 * <p>点击商品卡片时，Demo 只模拟一次商品详情读取，不扣减库存、不写回 Redis。
 * 响应中带回商品详情，是为了让前端能展示用户点击结果，同时保持这条链路纯粹走
 * TMC 的读路径。</p>
 */
public class ProductDetailViewResult {

    /**
     * 是否成功读取到商品详情。
     */
    private final boolean success;

    /**
     * 给前端展示的结果文案。
     */
    private final String message;

    /**
     * 本次读取到的商品详情快照。
     */
    private final ProductItem product;

    public ProductDetailViewResult(boolean success, String message, ProductItem product) {
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

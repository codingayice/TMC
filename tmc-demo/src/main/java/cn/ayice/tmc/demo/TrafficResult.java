package cn.ayice.tmc.demo;

/**
 * 热点流量模拟结果。
 *
 * <p>压测接口会连续读取同一个商品 key，用来驱动 SDK 上报访问事件，
 * 让 tmc-server 在滑动窗口中识别出热点商品。</p>
 */
public class TrafficResult {

    /**
     * 被模拟访问的商品 ID。
     */
    private final String productId;

    /**
     * 请求模拟的读取次数。
     */
    private final int requestedCount;

    /**
     * 实际读取到商品的次数。
     */
    private final int successCount;

    public TrafficResult(String productId, int requestedCount, int successCount) {
        this.productId = productId;
        this.requestedCount = requestedCount;
        this.successCount = successCount;
    }

    public String getProductId() {
        return productId;
    }

    public int getRequestedCount() {
        return requestedCount;
    }

    public int getSuccessCount() {
        return successCount;
    }
}

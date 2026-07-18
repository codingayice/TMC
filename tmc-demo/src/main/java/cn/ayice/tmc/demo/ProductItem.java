package cn.ayice.tmc.demo;

/**
 * 限时抢购商品详情视图模型。
 *
 * <p>Demo 把商品详情序列化成 JSON 存入 Redis 的 {@code product-detail:{id}}。
 * 这里刻意不保存真实库存，因为库存属于强一致、高频写数据，不应该进入本地二级缓存。
 * 商品标题、卖点、价格、营销标签这类详情信息读多写少，更适合用来展示 TMC
 * 从访问上报、热点识别到本地缓存命中的完整链路。</p>
 */
public class ProductItem {

    /**
     * 商品 ID，同时也是 Redis key 的业务后缀。
     */
    private String id;

    /**
     * 商品标题，前端列表和接口响应都会展示。
     */
    private String name;

    /**
     * 商品卖点描述，用于模拟商品详情页中的核心参数。
     */
    private String description;

    /**
     * 活动展示价，单位元。价格在真实业务中也可能变化，本 Demo 把它视为商品详情的一部分。
     */
    private int price;

    /**
     * 划线价，单位元。
     */
    private int originalPrice;

    /**
     * 页面展示的热度文案，例如“28.6 万人看过”。它不是实时库存，不参与一致性判断。
     */
    private String heatText;

    /**
     * 页面展示的热度进度条百分比，只用于 UI 呈现，不代表真实库存。
     */
    private int heatPercent;

    /**
     * 商品促销标签，例如免息信息。
     */
    private String installmentLabel;

    /**
     * 静态图片地址。前端直接使用该字段渲染商品图。
     */
    private String imageUrl;

    public ProductItem() {
    }

    public ProductItem(
            String id,
            String name,
            String description,
            int price,
            int originalPrice,
            String heatText,
            int heatPercent,
            String installmentLabel,
            String imageUrl
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.originalPrice = originalPrice;
        this.heatText = heatText;
        this.heatPercent = heatPercent;
        this.installmentLabel = installmentLabel;
        this.imageUrl = imageUrl;
    }

    public String redisKey() {
        return "product-detail:" + id;
    }

    public int getHeatPercent() {
        return Math.max(0, Math.min(100, heatPercent));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public int getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(int originalPrice) {
        this.originalPrice = originalPrice;
    }

    public String getHeatText() {
        return heatText;
    }

    public void setHeatText(String heatText) {
        this.heatText = heatText;
    }

    public void setHeatPercent(int heatPercent) {
        this.heatPercent = heatPercent;
    }

    public String getInstallmentLabel() {
        return installmentLabel;
    }

    public void setInstallmentLabel(String installmentLabel) {
        this.installmentLabel = installmentLabel;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}

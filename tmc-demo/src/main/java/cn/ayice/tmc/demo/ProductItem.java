package cn.ayice.tmc.demo;

/**
 * 限时抢购商品视图模型。
 *
 * <p>Demo 把整个商品详情序列化成 JSON 存入 Redis 的 {@code product:{id}}。
 * 商品详情是典型读多写少数据，高频读取时最容易形成热点 key，适合展示 TMC
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
     * 抢购价，单位元。
     */
    private int price;

    /**
     * 划线价，单位元。
     */
    private int originalPrice;

    /**
     * 活动总库存。
     */
    private int totalStock;

    /**
     * 已售数量。抢购成功后会写回 Redis，并触发 TMC 本地缓存失效。
     */
    private int soldCount;

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
            int totalStock,
            int soldCount,
            String installmentLabel,
            String imageUrl
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.originalPrice = originalPrice;
        this.totalStock = totalStock;
        this.soldCount = soldCount;
        this.installmentLabel = installmentLabel;
        this.imageUrl = imageUrl;
    }

    /**
     * 创建售出数量递增后的新对象，避免调用方原地修改旧对象造成测试难以判断。
     */
    public ProductItem purchaseOne() {
        return new ProductItem(
                id,
                name,
                description,
                price,
                originalPrice,
                totalStock,
                soldCount + 1,
                installmentLabel,
                imageUrl
        );
    }

    public String redisKey() {
        return "product:" + id;
    }

    public int getRemainCount() {
        return Math.max(totalStock - soldCount, 0);
    }

    public int getProgressPercent() {
        if (totalStock <= 0) {
            return 0;
        }
        return Math.min(100, Math.round(soldCount * 100.0f / totalStock));
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

    public int getTotalStock() {
        return totalStock;
    }

    public void setTotalStock(int totalStock) {
        this.totalStock = totalStock;
    }

    public int getSoldCount() {
        return soldCount;
    }

    public void setSoldCount(int soldCount) {
        this.soldCount = soldCount;
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

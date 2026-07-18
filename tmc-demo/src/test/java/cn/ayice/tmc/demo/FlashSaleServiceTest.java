package cn.ayice.tmc.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 限时抢购 Demo 业务测试。
 *
 * <p>这些测试保护 Demo 的核心业务语义：初始化商品详情、通过 TMC 读取商品详情、
 * 点击商品时只模拟查看详情不扣库存，以及压测接口确实制造高频读流量。</p>
 */
class FlashSaleServiceTest {

    @Test
    void shouldSeedDefaultProductsIntoRedis() {
        ProductRedisGateway gateway = mock(ProductRedisGateway.class);
        FlashSaleService service = new FlashSaleService(gateway);

        List<ProductItem> products = service.seedProducts();

        assertEquals(3, products.size());
        assertEquals("1001", products.get(0).getId());
        assertEquals("Apple iPhone 15 128GB", products.get(0).getName());
        verify(gateway, times(3)).saveProduct(org.mockito.Mockito.any(ProductItem.class));
    }

    @Test
    void shouldViewProductDetailWithoutPersistingInventoryChange() {
        ProductRedisGateway gateway = mock(ProductRedisGateway.class);
        ProductItem product = new ProductItem(
                "1001",
                "Apple iPhone 15 128GB",
                "A16芯片 | 6.1英寸 | 5G全网通",
                4999,
                5999,
                "28.6 万人看过",
                78,
                "24期免息",
                "/images/iphone.svg"
        );
        when(gateway.getProduct("1001")).thenReturn(product);
        FlashSaleService service = new FlashSaleService(gateway);

        ProductDetailViewResult result = service.viewProductDetail("1001");

        assertEquals(true, result.isSuccess());
        assertEquals(product, result.getProduct());
        assertEquals("已查看商品详情", result.getMessage());
        verify(gateway, never()).saveProduct(org.mockito.Mockito.any(ProductItem.class));
    }

    @Test
    void shouldGenerateHotTrafficThroughTmcReadPath() {
        ProductRedisGateway gateway = mock(ProductRedisGateway.class);
        ProductItem product = new ProductItem(
                "1001",
                "Apple iPhone 15 128GB",
                "A16芯片 | 6.1英寸 | 5G全网通",
                4999,
                5999,
                "28.6 万人看过",
                78,
                "24期免息",
                "/images/iphone.svg"
        );
        when(gateway.getProduct("1001")).thenReturn(product);
        FlashSaleService service = new FlashSaleService(gateway);

        TrafficResult result = service.generateHotTraffic("1001", 5);

        assertEquals("1001", result.getProductId());
        assertEquals(5, result.getRequestedCount());
        assertEquals(5, result.getSuccessCount());
        verify(gateway, times(5)).getProduct("1001");
    }
}

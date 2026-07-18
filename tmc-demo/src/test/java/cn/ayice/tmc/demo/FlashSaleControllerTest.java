package cn.ayice.tmc.demo;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.ayice.tmc.sdk.TmcClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 限时抢购 Controller 参数绑定测试。
 *
 * <p>项目当前没有给 javac 全局开启 {@code -parameters}，因此 Spring MVC 注解必须显式写出
 * path variable 和 request parameter 名称。本测试保护打包后接口仍能正确绑定 URL 参数。</p>
 */
@WebMvcTest(FlashSaleController.class)
class FlashSaleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FlashSaleService service;

    @MockBean
    private TmcClient tmcClient;

    @Test
    void shouldBindProductIdWhenPurchase() throws Exception {
        ProductItem product = new ProductItem(
                "1001",
                "Apple iPhone 15 128GB",
                "A16芯片 | 6.1英寸 | 5G全网通",
                4999,
                5999,
                300,
                236,
                "24期免息",
                "/images/iphone.svg"
        );
        when(service.purchase("1001")).thenReturn(new PurchaseResult(true, "抢购成功", product));

        mockMvc.perform(post("/api/flash-sale/products/1001/purchase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.id").value("1001"));

        verify(service).purchase("1001");
    }

    @Test
    void shouldBindProductIdAndCountWhenGeneratingTraffic() throws Exception {
        when(service.generateHotTraffic("1001", 8)).thenReturn(new TrafficResult("1001", 8, 8));

        mockMvc.perform(post("/api/flash-sale/traffic/hot-product/1001?count=8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(8));

        verify(service).generateHotTraffic("1001", 8);
    }
}

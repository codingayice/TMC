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
 * path variable 和 request parameter 名称。本测试保护打包后详情查看和热点流量接口
 * 仍能正确绑定 URL 参数。</p>
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
    void shouldBindProductIdWhenViewingDetail() throws Exception {
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
        when(service.viewProductDetail("1001"))
                .thenReturn(new ProductDetailViewResult(true, "已查看商品详情", product));

        mockMvc.perform(post("/api/flash-sale/products/1001/detail-view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.id").value("1001"));

        verify(service).viewProductDetail("1001");
    }

    @Test
    void shouldBindProductIdAndCountWhenGeneratingTraffic() throws Exception {
        when(service.generateHotTraffic("1001", 8)).thenReturn(new TrafficResult("1001", 8, 8));

        mockMvc.perform(post("/api/flash-sale/traffic/hot-product/1001?count=8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(8));

        verify(service).generateHotTraffic("1001", 8);
    }

    @Test
    void shouldResetLocalTmcState() throws Exception {
        mockMvc.perform(post("/api/flash-sale/tmc/local-state/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("已清空本节点热点和本地缓存"));

        verify(tmcClient).resetLocalState();
    }
}

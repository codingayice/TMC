package cn.ayice.tmc.demo;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import cn.ayice.tmc.sdk.TmcClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Demo 指标启动级测试。
 *
 * <p>该测试使用真实 Spring Boot 自动配置，保护 Demo 引入 Actuator/Prometheus 后能够注册
 * SDK 侧 {@code tmc.client.*} 指标。否则 Grafana 看板虽然存在，却采不到本地缓存命中、
 * Redis 回源和写后失效这些核心数据。</p>
 */
@SpringBootTest(properties = {
        "tmc.app-name=flash-sale-demo-test",
        "tmc.report.enabled=false",
        "tmc.hot-key.discovery.enabled=false",
        "tmc.invalidation.enabled=false"
})
class TmcDemoMetricsBindingTest {

    @Autowired
    private TmcClient tmcClient;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void shouldExposeTmcClientMetricsWhenDemoStartsWithActuator() {
        tmcClient.get("product:1001", () -> "value");

        assertNotNull(meterRegistry.find("tmc.client.total.gets").functionCounter());
    }
}

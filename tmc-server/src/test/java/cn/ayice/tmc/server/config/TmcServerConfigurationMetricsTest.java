package cn.ayice.tmc.server.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.ayice.tmc.server.metrics.TmcServerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * tmc-server 指标装配测试。
 *
 * <p>当前 Grafana 主看板只展示 SDK 侧效果指标，服务端热点探测内部计数不再注册为
 * Prometheus 自定义指标。本测试保护这个边界，避免后续又把 tmc_server_* 指标加回主观测面。</p>
 */
class TmcServerConfigurationMetricsTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TmcServerConfiguration.class)
            .withBean(TmcServerMetrics.class)
            .withBean(SimpleMeterRegistry.class);

    @Test
    void shouldNotRegisterServerTmcMeters() {
        contextRunner.run(context -> {
            SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
            assertTrue(registry.getMeters().stream()
                    .noneMatch(meter -> meter.getId().getName().startsWith("tmc.server")));
        });
    }
}

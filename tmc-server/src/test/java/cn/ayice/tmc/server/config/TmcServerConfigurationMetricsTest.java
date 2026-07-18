package cn.ayice.tmc.server.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import cn.ayice.tmc.server.metrics.TmcServerMetrics;
import cn.ayice.tmc.server.metrics.TmcServerMetricsBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * tmc-server 指标装配测试。
 *
 * <p>保障服务端存在 MeterRegistry 时会自动注册 TMC 指标绑定器，让
 * /actuator/prometheus 能暴露 tmc_server_* 指标。</p>
 */
class TmcServerConfigurationMetricsTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TmcServerConfiguration.class)
            .withBean(TmcServerMetrics.class)
            .withBean(SimpleMeterRegistry.class);

    @Test
    void shouldCreateServerMetricsBinder() {
        contextRunner.run(context -> assertNotNull(context.getBean(TmcServerMetricsBinder.class)));
    }
}

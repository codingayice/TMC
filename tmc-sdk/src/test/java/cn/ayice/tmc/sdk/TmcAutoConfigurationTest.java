package cn.ayice.tmc.sdk;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.ayice.tmc.communication.HotKeyDiscoveryListener;
import cn.ayice.tmc.communication.InvalidationListener;
import cn.ayice.tmc.communication.InvalidationReporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * SDK 自动配置测试。
 *
 * <p>保障业务应用引入 starter 后可以按配置创建默认 Bean，同时尊重关闭开关。</p>
 */
class TmcAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(TmcAutoConfiguration.class))
            .withPropertyValues("tmc.app-name=product-service");

    @Test
    void shouldCreateTmcClient() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("tmcClient"));
            assertNotNull(context.getBean(TmcClient.class));
        });
    }

    @Test
    void shouldNotCreateTmcClientWhenTmcDisabled() {
        contextRunner
                .withPropertyValues("tmc.enabled=false")
                .run(context -> assertTrue(context.getBeansOfType(TmcClient.class).isEmpty()));
    }

    @Test
    void shouldCreateHotKeyDiscoveryListenerByDefault() {
        contextRunner.run(context ->
                assertNotNull(context.getBean(HotKeyDiscoveryListener.class))
        );
    }

    @Test
    void shouldNotCreateHotKeyDiscoveryListenerWhenDisabled() {
        contextRunner
                .withPropertyValues("tmc.hot-key.discovery.enabled=false")
                .run(context -> assertTrue(context.getBeansOfType(HotKeyDiscoveryListener.class).isEmpty()));
    }

    @Test
    void shouldCreateInvalidationComponentsByDefault() {
        contextRunner.run(context -> {
            assertNotNull(context.getBean(InvalidationReporter.class));
            assertNotNull(context.getBean(InvalidationListener.class));
        });
    }

    @Test
    void shouldNotCreateInvalidationComponentsWhenDisabled() {
        contextRunner
                .withPropertyValues("tmc.invalidation.enabled=false")
                .run(context -> {
                    assertTrue(context.getBeansOfType(InvalidationReporter.class).isEmpty());
                    assertTrue(context.getBeansOfType(InvalidationListener.class).isEmpty());
                });
    }

    @Test
    void shouldDisableInvalidationReporterOnly() {
        contextRunner
                .withPropertyValues("tmc.invalidation.report-enabled=false")
                .run(context -> {
                    assertTrue(context.getBeansOfType(InvalidationReporter.class).isEmpty());
                    assertNotNull(context.getBean(InvalidationListener.class));
                });
    }

    @Test
    void shouldDisableInvalidationListenerOnly() {
        contextRunner
                .withPropertyValues("tmc.invalidation.listen-enabled=false")
                .run(context -> {
                    assertNotNull(context.getBean(InvalidationReporter.class));
                    assertTrue(context.getBeansOfType(InvalidationListener.class).isEmpty());
                });
    }

}

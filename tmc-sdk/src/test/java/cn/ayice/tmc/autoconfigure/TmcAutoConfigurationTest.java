package cn.ayice.tmc.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.ayice.tmc.core.TmcClient;
import cn.ayice.tmc.remote.RemoteCacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TmcAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestRemoteCacheConfiguration.class)
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(TmcAutoConfiguration.class))
            .withPropertyValues("tmc.app-name=product-service");

    @Test
    void shouldCreateTmcClientWhenRemoteCacheClientExists() {
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
    void shouldNotCreateTmcClientWithoutRemoteCacheClient() {
        new ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(TmcAutoConfiguration.class))
                .withPropertyValues("tmc.app-name=product-service")
                .run(context -> assertTrue(context.getBeansOfType(TmcClient.class).isEmpty()));
    }

    static class TestRemoteCacheConfiguration {

        @org.springframework.context.annotation.Bean
        RemoteCacheClient remoteCacheClient() {
            return key -> "value";
        }
    }
}

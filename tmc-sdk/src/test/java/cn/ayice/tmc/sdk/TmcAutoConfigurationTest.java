package cn.ayice.tmc.sdk;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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

}

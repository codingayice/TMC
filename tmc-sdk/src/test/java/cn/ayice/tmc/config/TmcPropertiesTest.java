package cn.ayice.tmc.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TmcPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void shouldUseDefaultValues() {
        TmcProperties properties = new TmcProperties();

        assertTrue(properties.isEnabled());
        assertNotNull(properties.getClientId());
        assertTrue(properties.getClientId().startsWith("tmc-client-"));
        assertTrue(properties.getLocalCache().isEnabled());
        assertEquals(10_000, properties.getLocalCache().getMaximumSize());
        assertEquals(30_000, properties.getLocalCache().getExpireAfterWriteMillis());
        assertEquals(30_000, properties.getHotKey().getTtlMillis());
    }

    @Test
    void shouldBindTmcProperties() {
        contextRunner
                .withPropertyValues(
                        "tmc.app-name=product-service",
                        "tmc.client-id=client-1",
                        "tmc.local-cache.enabled=false",
                        "tmc.local-cache.maximum-size=123",
                        "tmc.local-cache.expire-after-write-millis=456",
                        "tmc.hot-key.ttl-millis=789"
                )
                .run(context -> {
                    TmcProperties properties = context.getBean(TmcProperties.class);
                    assertEquals("product-service", properties.getAppName());
                    assertEquals("client-1", properties.getClientId());
                    assertEquals(123, properties.getLocalCache().getMaximumSize());
                    assertEquals(456, properties.getLocalCache().getExpireAfterWriteMillis());
                    assertEquals(789, properties.getHotKey().getTtlMillis());
                });
    }

    @Test
    void shouldRejectInvalidLocalCacheValues() {
        LocalCacheProperties properties = new LocalCacheProperties();

        assertThrows(IllegalArgumentException.class, () -> properties.setMaximumSize(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setExpireAfterWriteMillis(0));
    }

    @EnableConfigurationProperties(TmcProperties.class)
    static class PropertiesConfiguration {
    }
}

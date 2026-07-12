package cn.ayice.tmc.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.ayice.tmc.communication.AccessReportProperties;
import cn.ayice.tmc.communication.RsyslogProperties;
import cn.ayice.tmc.hotkey.LocalCacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * TmcProperties 配置测试。
 *
 * <p>保障 SDK 必要配置校验生效，避免应用名等关键配置缺失时静默启动。</p>
 */
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
        assertTrue(properties.getReport().isEnabled());
        assertEquals(10_000, properties.getReport().getQueueCapacity());
        assertEquals(100, properties.getReport().getBatchSize());
        assertEquals(100, properties.getReport().getFlushIntervalMillis());
        assertEquals("127.0.0.1", properties.getReport().getRsyslog().getHost());
        assertEquals(5514, properties.getReport().getRsyslog().getPort());
        assertEquals(1000, properties.getReport().getRsyslog().getConnectTimeoutMillis());
        assertEquals(1000, properties.getReport().getRsyslog().getWriteTimeoutMillis());
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
                        "tmc.hot-key.ttl-millis=789",
                        "tmc.report.enabled=false",
                        "tmc.report.queue-capacity=111",
                        "tmc.report.batch-size=22",
                        "tmc.report.flush-interval-millis=33",
                        "tmc.report.rsyslog.host=192.168.1.10",
                        "tmc.report.rsyslog.port=5515",
                        "tmc.report.rsyslog.connect-timeout-millis=44",
                        "tmc.report.rsyslog.write-timeout-millis=55"
                )
                .run(context -> {
                    TmcProperties properties = context.getBean(TmcProperties.class);
                    assertEquals("product-service", properties.getAppName());
                    assertEquals("client-1", properties.getClientId());
                    assertEquals(123, properties.getLocalCache().getMaximumSize());
                    assertEquals(456, properties.getLocalCache().getExpireAfterWriteMillis());
                    assertEquals(789, properties.getHotKey().getTtlMillis());
                    assertEquals(111, properties.getReport().getQueueCapacity());
                    assertEquals(22, properties.getReport().getBatchSize());
                    assertEquals(33, properties.getReport().getFlushIntervalMillis());
                    assertEquals("192.168.1.10", properties.getReport().getRsyslog().getHost());
                    assertEquals(5515, properties.getReport().getRsyslog().getPort());
                    assertEquals(44, properties.getReport().getRsyslog().getConnectTimeoutMillis());
                    assertEquals(55, properties.getReport().getRsyslog().getWriteTimeoutMillis());
                });
    }

    @Test
    void shouldRejectInvalidLocalCacheValues() {
        LocalCacheProperties properties = new LocalCacheProperties();

        assertThrows(IllegalArgumentException.class, () -> properties.setMaximumSize(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setExpireAfterWriteMillis(0));
    }

    @Test
    void shouldRejectInvalidReportValues() {
        AccessReportProperties report = new AccessReportProperties();
        RsyslogProperties rsyslog = new RsyslogProperties();

        assertThrows(IllegalArgumentException.class, () -> report.setQueueCapacity(0));
        assertThrows(IllegalArgumentException.class, () -> report.setBatchSize(0));
        assertThrows(IllegalArgumentException.class, () -> report.setFlushIntervalMillis(0));
        assertThrows(IllegalArgumentException.class, () -> report.setRsyslog(null));
        assertThrows(IllegalArgumentException.class, () -> rsyslog.setHost(" "));
        assertThrows(IllegalArgumentException.class, () -> rsyslog.setPort(0));
        assertThrows(IllegalArgumentException.class, () -> rsyslog.setPort(65_536));
        assertThrows(IllegalArgumentException.class, () -> rsyslog.setConnectTimeoutMillis(0));
        assertThrows(IllegalArgumentException.class, () -> rsyslog.setWriteTimeoutMillis(0));
    }

    @EnableConfigurationProperties(TmcProperties.class)
    static class PropertiesConfiguration {
    }
}

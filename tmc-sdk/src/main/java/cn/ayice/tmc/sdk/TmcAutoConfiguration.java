package cn.ayice.tmc.sdk;

import cn.ayice.tmc.communication.AccessReporter;
import cn.ayice.tmc.hotkey.CaffeineLocalCache;
import cn.ayice.tmc.hotkey.HotKeyManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;

@AutoConfiguration
@EnableConfigurationProperties(TmcProperties.class)
@ConditionalOnProperty(prefix = "tmc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TmcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CaffeineLocalCache localCache(TmcProperties properties) {
        return new CaffeineLocalCache(
                properties.getLocalCache().getMaximumSize(),
                properties.getLocalCache().getExpireAfterWriteMillis()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public HotKeyManager hotKeyManager(TmcProperties properties) {
        properties.validate();
        return new HotKeyManager(properties.getAppName(), properties.getHotKey().getTtlMillis());
    }

    @Bean
    @ConditionalOnMissingBean
    public TmcMetrics tmcMetrics() {
        return new TmcMetrics();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "tmc.report", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AccessReporter accessReporter(TmcProperties properties, TmcMetrics tmcMetrics) {
        return new AccessReporter(properties.getReport(), tmcMetrics);
    }

    @Bean
    @ConditionalOnMissingBean
    public TmcClient tmcClient(
            TmcProperties properties,
            HotKeyManager hotKeyManager,
            CaffeineLocalCache localCache,
            TmcMetrics tmcMetrics,
            ObjectProvider<AccessReporter> accessReporter
    ) {
        return new TmcClient(properties, hotKeyManager, localCache, tmcMetrics, accessReporter.getIfAvailable());
    }
}

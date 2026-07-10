package cn.ayice.tmc.autoconfigure;

import cn.ayice.tmc.cache.CaffeineLocalCache;
import cn.ayice.tmc.cache.LocalCache;
import cn.ayice.tmc.config.TmcProperties;
import cn.ayice.tmc.core.TmcClient;
import cn.ayice.tmc.hotkey.HotKeyManager;
import cn.ayice.tmc.metrics.TmcMetrics;
import cn.ayice.tmc.remote.RemoteCacheClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(TmcProperties.class)
@ConditionalOnProperty(prefix = "tmc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TmcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LocalCache localCache(TmcProperties properties) {
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
    @ConditionalOnBean(RemoteCacheClient.class)
    @ConditionalOnMissingBean
    public TmcClient tmcClient(
            TmcProperties properties,
            RemoteCacheClient remoteCacheClient,
            HotKeyManager hotKeyManager,
            LocalCache localCache,
            TmcMetrics tmcMetrics
    ) {
        return new TmcClient(properties, remoteCacheClient, hotKeyManager, localCache, tmcMetrics);
    }
}

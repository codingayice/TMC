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

/**
 * TMC SDK 自动配置。
 *
 * <p>业务应用引入 SDK 后，Spring Boot 会根据 {@code tmc.*} 配置自动创建本地缓存、
 * 热点管理器、访问上报器和 {@link TmcClient}。用户自定义同类型 Bean 时，
 * {@code ConditionalOnMissingBean} 会让自定义 Bean 优先。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(TmcProperties.class)
@ConditionalOnProperty(prefix = "tmc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TmcAutoConfiguration {

    /**
     * 创建 Caffeine 本地缓存实例。
     */
    @Bean
    @ConditionalOnMissingBean
    public CaffeineLocalCache localCache(TmcProperties properties) {
        return new CaffeineLocalCache(
                properties.getLocalCache().getMaximumSize(),
                properties.getLocalCache().getExpireAfterWriteMillis()
        );
    }

    /**
     * 创建热点 key 管理器，用于保存服务端下发的热点集合。
     */
    @Bean
    @ConditionalOnMissingBean
    public HotKeyManager hotKeyManager(TmcProperties properties) {
        properties.validate();
        return new HotKeyManager(properties.getAppName(), properties.getHotKey().getTtlMillis());
    }

    /**
     * 创建 SDK 客户端指标对象。
     */
    @Bean
    @ConditionalOnMissingBean
    public TmcMetrics tmcMetrics() {
        return new TmcMetrics();
    }

    /**
     * 创建访问事件上报器。
     *
     * <p>当 {@code tmc.report.enabled=false} 时不创建该 Bean，TmcClient 会自动跳过上报。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "tmc.report", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AccessReporter accessReporter(TmcProperties properties, TmcMetrics tmcMetrics) {
        return new AccessReporter(properties.getReport(), tmcMetrics);
    }

    /**
     * 创建 SDK 核心客户端。
     *
     * <p>AccessReporter 使用 ObjectProvider 注入，是为了兼容上报关闭时 Bean 不存在的场景。</p>
     */
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

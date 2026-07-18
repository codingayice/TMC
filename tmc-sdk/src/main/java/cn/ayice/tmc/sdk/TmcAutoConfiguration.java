package cn.ayice.tmc.sdk;

import cn.ayice.tmc.communication.AccessReporter;
import cn.ayice.tmc.communication.HotKeyDiscoveryListener;
import cn.ayice.tmc.communication.InvalidationListener;
import cn.ayice.tmc.communication.InvalidationReporter;
import cn.ayice.tmc.hotkey.CaffeineLocalCache;
import cn.ayice.tmc.hotkey.HotKeyManager;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
     * 创建热点发现监听器。
     *
     * <p>监听器属于通信模块，负责从 etcd 感知服务端发布的 HotKeySnapshot。
     * 它只更新 HotKeyManager，不直接影响 TmcClient 的业务读线程。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(HotKeyManager.class)
    @ConditionalOnProperty(
            prefix = "tmc.hot-key.discovery",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public HotKeyDiscoveryListener hotKeyDiscoveryListener(
            TmcProperties properties,
            HotKeyManager hotKeyManager,
            TmcMetrics tmcMetrics
    ) {
        return new HotKeyDiscoveryListener(
                properties.getAppName(),
                properties.getEtcd(),
                properties.getHotKey().getDiscovery(),
                hotKeyManager,
                tmcMetrics
        );
    }

    /**
     * 创建失效事件发布器。
     *
     * <p>写 Redis 成功后，TmcClient 会通过该组件把 InvalidationEvent 写入 etcd，
     * 其他 SDK 节点监听到事件后删除自己的本地缓存。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "tmc.invalidation",
            name = {"enabled", "report-enabled"},
            havingValue = "true",
            matchIfMissing = true
    )
    public InvalidationReporter invalidationReporter(TmcProperties properties, TmcMetrics tmcMetrics) {
        return new InvalidationReporter(
                properties.getAppName(),
                properties.getClientId(),
                properties.getEtcd(),
                properties.getInvalidation(),
                tmcMetrics
        );
    }

    /**
     * 创建失效事件监听器。
     *
     * <p>监听器只处理其他 clientId 发布的事件，避免当前节点重复处理自己刚刚删除过的缓存。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(CaffeineLocalCache.class)
    @ConditionalOnProperty(
            prefix = "tmc.invalidation",
            name = {"enabled", "listen-enabled"},
            havingValue = "true",
            matchIfMissing = true
    )
    public InvalidationListener invalidationListener(
            TmcProperties properties,
            CaffeineLocalCache localCache,
            TmcMetrics tmcMetrics
    ) {
        return new InvalidationListener(
                properties.getAppName(),
                properties.getClientId(),
                properties.getEtcd(),
                properties.getInvalidation(),
                localCache,
                tmcMetrics
        );
    }

    /**
     * 创建 SDK Micrometer 指标绑定器。
     *
     * <p>SDK 本身不强制业务应用引入监控系统；只有业务应用存在 MeterRegistry 时，
     * 才把 TmcMetrics 注册为 tmc_client_* 指标。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MeterRegistry.class)
    public TmcClientMetricsBinder tmcClientMetricsBinder(
            TmcProperties properties,
            TmcMetrics tmcMetrics,
            MeterRegistry meterRegistry
    ) {
        return new TmcClientMetricsBinder(properties, tmcMetrics, meterRegistry);
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
            ObjectProvider<AccessReporter> accessReporter,
            ObjectProvider<InvalidationReporter> invalidationReporter
    ) {
        return new TmcClient(
                properties,
                hotKeyManager,
                localCache,
                tmcMetrics,
                accessReporter.getIfAvailable(),
                invalidationReporter.getIfAvailable()
        );
    }
}

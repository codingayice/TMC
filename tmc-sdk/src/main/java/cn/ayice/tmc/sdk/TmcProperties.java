package cn.ayice.tmc.sdk;

import cn.ayice.tmc.communication.AccessReportProperties;
import cn.ayice.tmc.communication.EtcdProperties;
import cn.ayice.tmc.hotkey.HotKeyProperties;
import cn.ayice.tmc.hotkey.LocalCacheProperties;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SDK 总配置，对应 {@code tmc.*}。
 *
 * <p>业务应用引入 SDK 后，通过 Spring Boot 配置这些参数即可初始化本地缓存、
 * 热点管理器和访问事件上报组件。</p>
 */
@ConfigurationProperties(prefix = "tmc")
public class TmcProperties {

    /**
     * 是否启用 TMC
     */
    private boolean enabled = true;

    /**
     * 业务应用名称
     */
    private String appName;

    /**
     * 客户端唯一标识
     */
    private String clientId = "tmc-client-" + UUID.randomUUID();

    /**
     * 本地缓存配置
     */
    private LocalCacheProperties localCache = new LocalCacheProperties();

    /**
     * 热点 key 配置。
     */
    private HotKeyProperties hotKey = new HotKeyProperties();

    /**
     * 事件上报配置。
     */
    private AccessReportProperties report = new AccessReportProperties();

    /**
     * etcd 通信配置。
     */
    private EtcdProperties etcd = new EtcdProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        if (hasText(clientId)) {
            this.clientId = clientId;
        }
    }

    public LocalCacheProperties getLocalCache() {
        return localCache;
    }

    public void setLocalCache(LocalCacheProperties localCache) {
        if (localCache == null) {
            throw new IllegalArgumentException("localCache must not be null");
        }
        this.localCache = localCache;
    }

    public HotKeyProperties getHotKey() {
        return hotKey;
    }

    public void setHotKey(HotKeyProperties hotKey) {
        if (hotKey == null) {
            throw new IllegalArgumentException("hotKey must not be null");
        }
        this.hotKey = hotKey;
    }

    public AccessReportProperties getReport() {
        return report;
    }

    public void setReport(AccessReportProperties report) {
        if (report == null) {
            throw new IllegalArgumentException("report must not be null");
        }
        this.report = report;
    }

    public EtcdProperties getEtcd() {
        return etcd;
    }

    public void setEtcd(EtcdProperties etcd) {
        if (etcd == null) {
            throw new IllegalArgumentException("etcd must not be null");
        }
        this.etcd = etcd;
    }

    /**
     * 校验 SDK 运行所需的最小配置。
     */
    public void validate() {
        if (enabled && !hasText(appName)) {
            throw new IllegalArgumentException("appName must not be blank when TMC is enabled");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

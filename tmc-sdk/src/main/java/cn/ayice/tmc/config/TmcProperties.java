package cn.ayice.tmc.config;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tmc")
public class TmcProperties {

    private boolean enabled = true;

    private String appName;

    private String clientId = "tmc-client-" + UUID.randomUUID();

    private LocalCacheProperties localCache = new LocalCacheProperties();

    private HotKeyProperties hotKey = new HotKeyProperties();

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

    public void validate() {
        if (enabled && !hasText(appName)) {
            throw new IllegalArgumentException("appName must not be blank when TMC is enabled");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

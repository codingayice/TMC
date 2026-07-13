package cn.ayice.tmc.hotkey;

import cn.ayice.tmc.model.HotKey;
import cn.ayice.tmc.model.HotKeySnapshot;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SDK 侧热点 key 管理器。
 *
 * <p>服务端识别出的热点 key 会下发给 SDK，SDK 在本地维护一份带过期时间的集合。
 * TmcClient 每次 get 都先查询这里：只有命中的 key 才会尝试走 Caffeine 本地缓存。</p>
 */
public class HotKeyManager {

    /**
     * 当前 SDK 所属应用，只接受同 app 的热点快照。
     */
    private final String appName;

    /**
     * 服务端未提供 ttl 时使用的默认本地热点有效期。
     */
    private final long defaultTtlMillis;

    /**
     * key -> 本地热点状态。
     */
    private final Map<String, HotKeyState> hotKeys = new ConcurrentHashMap<>();

    public HotKeyManager(String appName, long defaultTtlMillis) {
        if (isBlank(appName)) {
            throw new IllegalArgumentException("appName must not be blank");
        }
        if (defaultTtlMillis <= 0) {
            throw new IllegalArgumentException("defaultTtlMillis must be positive");
        }
        this.appName = appName;
        this.defaultTtlMillis = defaultTtlMillis;
    }

    /**
     * 增加一个热点 key。
     *
     * <p>如果热点属于其他 app，直接忽略，避免不同业务应用之间互相污染本地缓存。</p>
     */
    public void addHotKey(HotKey hotKey) {
        if (hotKey == null || isBlank(hotKey.getKey())) {
            return;
        }
        if (!appName.equals(hotKey.getAppName())) {
            return;
        }
        long detectedAt = positiveOrDefault(hotKey.getDetectedAt(), System.currentTimeMillis());
        long ttlMillis = positiveOrDefault(hotKey.getTtlMillis(), defaultTtlMillis);
        long score = positiveOrDefault(hotKey.getScore(), 0);
        hotKeys.put(hotKey.getKey(), new HotKeyState(hotKey.getKey(), score, detectedAt, detectedAt + ttlMillis));
    }

    /**
     * 判断 key 当前是否为热点。
     *
     * <p>过期热点会在查询时顺手清理，避免后台线程维护额外清理任务。</p>
     */
    public boolean isHotKey(String key) {
        HotKeyState state = hotKeys.get(key);
        if (state == null) {
            return false;
        }
        if (state.isExpired(System.currentTimeMillis())) {
            hotKeys.remove(key, state);
            return false;
        }
        return true;
    }

    /**
     * 主动移除热点 key。
     */
    public void removeHotKey(String key) {
        hotKeys.remove(key);
    }

    /**
     * 清空当前应用的全部热点 key。
     *
     * <p>服务端删除 etcd 热点快照，或发布空热点状态时，SDK 需要立即停止把旧 key
     * 当作热点处理。该方法只清理热点标记，不直接清理 CaffeineLocalCache 中的数据，
     * 后续读请求会因为热点标记不存在而回源 Redis。</p>
     */
    public void clearHotKeys() {
        hotKeys.clear();
    }

    /**
     * 使用服务端快照替换当前 app 的热点集合。
     */
    public void updateHotKeySnapshot(HotKeySnapshot snapshot) {
        if (snapshot == null || !appName.equals(snapshot.getAppName())) {
            return;
        }
        hotKeys.clear();
        List<HotKey> snapshotHotKeys = snapshot.getHotKeys();
        if (snapshotHotKeys == null || snapshotHotKeys.isEmpty()) {
            return;
        }
        for (HotKey hotKey : snapshotHotKeys) {
            addHotKey(hotKey);
        }
    }

    /**
     * 返回当前未过期热点 key 数量。
     */
    public int hotKeyCount() {
        clearExpiredHotKeys();
        return hotKeys.size();
    }

    /**
     * 清理已经过期的热点 key。
     */
    private void clearExpiredHotKeys() {
        long now = System.currentTimeMillis();
        hotKeys.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    /**
     * 服务端字段为空或非法时使用默认值，保证 SDK 侧不会因为单个字段异常崩掉。
     */
    private static long positiveOrDefault(Long value, long defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 本地保存的热点状态。
     */
    private static final class HotKeyState {

        /**
         * 热点 key。
         */
        private final String key;

        /**
         * 服务端识别时的热度分数，当前阶段主要用于观察。
         */
        private final long score;

        /**
         * 服务端识别出热点的时间。
         */
        private final long detectedAt;

        /**
         * SDK 本地认为热点失效的时间点。
         */
        private final long expireAt;

        private HotKeyState(String key, long score, long detectedAt, long expireAt) {
            this.key = key;
            this.score = score;
            this.detectedAt = detectedAt;
            this.expireAt = expireAt;
        }

        /**
         * 判断热点状态是否过期。
         */
        private boolean isExpired(long now) {
            return now >= expireAt;
        }
    }
}

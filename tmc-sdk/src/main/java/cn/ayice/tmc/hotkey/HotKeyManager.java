package cn.ayice.tmc.hotkey;

import cn.ayice.tmc.model.HotKey;
import cn.ayice.tmc.model.HotKeySnapshot;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HotKeyManager {

    private final String appName;
    private final long defaultTtlMillis;
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

    public void removeHotKey(String key) {
        hotKeys.remove(key);
    }

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

    public int hotKeyCount() {
        clearExpiredHotKeys();
        return hotKeys.size();
    }

    private void clearExpiredHotKeys() {
        long now = System.currentTimeMillis();
        hotKeys.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private static long positiveOrDefault(Long value, long defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class HotKeyState {

        private final String key;
        private final long score;
        private final long detectedAt;
        private final long expireAt;

        private HotKeyState(String key, long score, long detectedAt, long expireAt) {
            this.key = key;
            this.score = score;
            this.detectedAt = detectedAt;
            this.expireAt = expireAt;
        }

        private boolean isExpired(long now) {
            return now >= expireAt;
        }
    }
}

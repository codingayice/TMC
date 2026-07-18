package cn.ayice.tmc.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.ayice.tmc.hotkey.HotKeyManager;
import cn.ayice.tmc.model.HotKey;
import cn.ayice.tmc.model.HotKeySnapshot;
import cn.ayice.tmc.util.JsonUtils;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * HotKeyDiscoveryListener 热点发现监听测试。
 *
 * <p>该测试不连接真实 etcd，而是直接验证监听器收到 etcd value 后的本地行为：
 * 应用新快照、忽略重复版本、忽略其他 app、删除事件清空本地热点集合。</p>
 */
class HotKeyDiscoveryListenerTest {

    @Test
    void shouldApplySnapshotJsonToHotKeyManager() {
        HotKeyManager manager = new HotKeyManager("product-service", 30_000);
        HotKeyDiscoveryListener listener = HotKeyDiscoveryListener.forTest("product-service", manager);
        String json = JsonUtils.toJson(new HotKeySnapshot(
                "v1",
                "product-service",
                List.of(new HotKey("product-service", "product:1", 100L, System.currentTimeMillis(), 30_000L))
        ));

        listener.applySnapshotJson(json);

        assertTrue(manager.isHotKey("product:1"));
    }

    @Test
    void shouldIgnoreDuplicateSnapshotVersion() {
        HotKeyManager manager = new HotKeyManager("product-service", 30_000);
        HotKeyDiscoveryListener listener = HotKeyDiscoveryListener.forTest("product-service", manager);
        String json = JsonUtils.toJson(new HotKeySnapshot(
                "v1",
                "product-service",
                List.of(new HotKey("product-service", "product:1", 100L, System.currentTimeMillis(), 30_000L))
        ));

        listener.applySnapshotJson(json);
        listener.applySnapshotJson(json);

        assertTrue(manager.isHotKey("product:1"));
    }

    @Test
    void shouldIgnoreSnapshotFromOtherApp() {
        HotKeyManager manager = new HotKeyManager("product-service", 30_000);
        HotKeyDiscoveryListener listener = HotKeyDiscoveryListener.forTest("product-service", manager);
        String json = JsonUtils.toJson(new HotKeySnapshot(
                "v1",
                "order-service",
                List.of(new HotKey("order-service", "order:1", 100L, System.currentTimeMillis(), 30_000L))
        ));

        listener.applySnapshotJson(json);

        assertFalse(manager.isHotKey("order:1"));
    }

    @Test
    void shouldClearHotKeysWhenSnapshotDeleted() {
        HotKeyManager manager = new HotKeyManager("product-service", 30_000);
        manager.addHotKey(new HotKey("product-service", "product:1", 100L, System.currentTimeMillis(), 30_000L));
        HotKeyDiscoveryListener listener = HotKeyDiscoveryListener.forTest("product-service", manager);

        listener.handleSnapshotDeleted();

        assertFalse(manager.isHotKey("product:1"));
    }

    @Test
    void shouldRecordInvalidSnapshotJson() {
        HotKeyManager manager = new HotKeyManager("product-service", 30_000);
        HotKeyDiscoveryListener listener = HotKeyDiscoveryListener.forTest("product-service", manager);

        assertDoesNotThrow(() -> listener.applySnapshotJson("{bad json"));
    }
}

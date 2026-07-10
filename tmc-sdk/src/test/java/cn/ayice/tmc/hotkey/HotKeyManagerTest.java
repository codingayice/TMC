package cn.ayice.tmc.hotkey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.ayice.tmc.model.HotKey;
import cn.ayice.tmc.model.HotKeySnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class HotKeyManagerTest {

    @Test
    void shouldAddAndRemoveHotKey() {
        HotKeyManager manager = new HotKeyManager("product-service", 30_000);

        manager.addHotKey(new HotKey("product-service", "product:1", 100L, System.currentTimeMillis(), 30_000L));

        assertTrue(manager.isHotKey("product:1"));
        assertEquals(1, manager.hotKeyCount());

        manager.removeHotKey("product:1");

        assertFalse(manager.isHotKey("product:1"));
        assertEquals(0, manager.hotKeyCount());
    }

    @Test
    void shouldExpireHotKey() {
        HotKeyManager manager = new HotKeyManager("product-service", 30_000);
        long detectedAt = System.currentTimeMillis() - 2_000;

        manager.addHotKey(new HotKey("product-service", "product:1", 100L, detectedAt, 1L));

        assertFalse(manager.isHotKey("product:1"));
        assertEquals(0, manager.hotKeyCount());
    }

    @Test
    void shouldReplaceHotKeysWithSnapshot() {
        HotKeyManager manager = new HotKeyManager("product-service", 30_000);
        manager.addHotKey(new HotKey("product-service", "old", 100L, System.currentTimeMillis(), 30_000L));
        HotKeySnapshot snapshot = new HotKeySnapshot(
                "v1",
                "product-service",
                List.of(new HotKey("product-service", "new", 200L, System.currentTimeMillis(), 30_000L))
        );

        manager.updateHotKeySnapshot(snapshot);

        assertFalse(manager.isHotKey("old"));
        assertTrue(manager.isHotKey("new"));
        assertEquals(1, manager.hotKeyCount());
    }
}

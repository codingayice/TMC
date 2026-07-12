package cn.ayice.tmc.server.hotkey;

import cn.ayice.tmc.model.HotKeySnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * HotKeySnapshotRepository 快照仓库测试。
 *
 * <p>保障每个 app 的最新快照可以保存、查询，并忽略非法快照。</p>
 */
class HotKeySnapshotRepositoryTest {

    @Test
    void shouldSaveAndGetSnapshotByAppName() {
        HotKeySnapshotRepository repository = new HotKeySnapshotRepository();
        HotKeySnapshot snapshot = new HotKeySnapshot("v1", "app-a", List.of());

        repository.save(snapshot);

        assertEquals(snapshot, repository.get("app-a"));
        assertEquals(snapshot, repository.getAll().get("app-a"));
    }

    @Test
    void shouldIgnoreNullSnapshot() {
        HotKeySnapshotRepository repository = new HotKeySnapshotRepository();

        repository.save(null);

        assertNull(repository.get("app-a"));
    }
}

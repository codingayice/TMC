package cn.ayice.tmc.server.hotkey;

import cn.ayice.tmc.model.HotKeySnapshot;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 热点快照仓库。
 *
 * <p>当前阶段先把每个 app 的最新快照保存在内存中，方便测试和调试。
 * 后续 etcd 下发阶段可以从这里读取最新快照并发布给 SDK。</p>
 */
@Component
public class HotKeySnapshotRepository {

    /**
     * appName -> latest snapshot。
     */
    private final ConcurrentHashMap<String, HotKeySnapshot> snapshots = new ConcurrentHashMap<>();

    /**
     * 保存某个 app 的最新热点快照。
     */
    public void save(HotKeySnapshot snapshot) {
        if (snapshot == null || snapshot.getAppName() == null || snapshot.getAppName().isBlank()) {
            return;
        }
        snapshots.put(snapshot.getAppName(), snapshot);
    }

    /**
     * 查询某个 app 的最新快照。
     */
    public HotKeySnapshot get(String appName) {
        return snapshots.get(appName);
    }

    /**
     * 返回快照副本，避免调用方修改内部 map。
     */
    public Map<String, HotKeySnapshot> getAll() {
        return Collections.unmodifiableMap(new HashMap<>(snapshots));
    }

    /**
     * 当 app 下没有任何追踪 key 时，删除旧快照，避免对外暴露过期热点。
     */
    public void remove(String appName) {
        snapshots.remove(appName);
    }
}

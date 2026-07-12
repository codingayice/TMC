package cn.ayice.tmc.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务端识别出来的热点 key 快照信息。
 *
 * <p>快照以 app 为维度发布，SDK 监听自己应用对应的快照即可。
 * version 用于客户端判断新旧快照，避免重复应用相同热点列表。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotKeySnapshot {

    /**
     * 热点key快照的版本号，用于客户端判断是否需要更新本地缓存
     */
    private String version;

    /**
     * 业务应用名称
     */
    private String appName;

    /**
     * 热点key列表
     */
    private List<HotKey> hotKeys;
}

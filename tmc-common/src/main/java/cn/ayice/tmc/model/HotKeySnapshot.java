package cn.ayice.tmc.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务端识别出来的热点key快照信息
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

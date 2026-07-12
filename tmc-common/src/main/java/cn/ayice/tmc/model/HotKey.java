package cn.ayice.tmc.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务端识别出来的热点 key 信息。
 *
 * <p>该对象会进入热点快照并下发给 SDK。SDK 收到后只把这些 key 放入本地缓存候选集合，
 * 普通 key 仍然走 Redis，保持透明接入。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotKey {

    /**
     * 业务应用名称
     */
    private String appName;

    /**
     * 热点key
     */
    private String key;

    /**
     * 当前窗口的热度
     */
    private Long score;

    /**
     * 热点key被识别出来的时间戳，单位毫秒
     */
    private Long detectedAt;

    /**
     * 热点key的过期时间，单位毫秒
     */
    private Long ttlMillis;
}

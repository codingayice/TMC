package cn.ayice.tmc.model;

import cn.ayice.tmc.enums.CacheOperation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本地缓存失效事件。
 *
 * <p>当某个客户端执行写操作时，需要通过 etcd 广播失效事件，其他客户端收到后删除本地缓存，
 * 避免继续读取旧值。本阶段暂未实现广播链路，但模型先放在 common 中供 SDK/server 复用。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvalidationEvent {

    /**
     * 业务应用名称
     */
    private String appName;

    /**
     * 需要失效的key
     */
    private String key;

    /**
     * 使key失效的操作类型
     */
    private CacheOperation operation;

    /**
     * 发出失效事件的客户端ID
     */
    private String clientId;

    /**
     * 失效事件发生的时间戳，单位毫秒
     */
    private Long timestamp;

    /**
     * 失效事件唯一标识
     */
    private String eventId;


}

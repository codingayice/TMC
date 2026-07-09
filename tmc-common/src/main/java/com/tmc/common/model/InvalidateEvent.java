package com.tmc.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * key 失效事件——写入端（set/del/expire）广播给集群其他节点
 *
 * 文章 3-2-2 第 2 步：通过 etcd 广播失效事件达到集群最终一致
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvalidateEvent {
    private String appName;
    private String uniqueKey;
    private InvalidateEventType eventType;
    private long timestamp;

    public enum InvalidateEventType {
        SET,
        DEL,
        EXPIRE
    }
}

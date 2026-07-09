package com.tmc.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * key 访问事件——SDK 上报到 Hermes 服务端的数据协议
 *
 * 对应文章 4-2 节的协议格式：
 *   appName  - 集群节点所属业务应用
 *   uniqueKey- 业务应用 key 访问事件的 key
 *   sendTime - 业务应用 key 访问事件的发生时间
 *   weight   - 业务应用 key 访问事件的访问权值
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyAccessEvent {
    private String appName;
    private String uniqueKey;
    private long sendTime;
    private int weight;
}

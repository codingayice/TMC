package cn.ayice.tmc.model;

import cn.ayice.tmc.enums.CacheOperation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * key 访问事件
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessEvent {

    /**
     * 业务应用名称
     */
    private String appName;

    /**
     * 访问的key
     */
    private String key;

    /**
     * 访问事件发生的时间戳，单位毫秒
     */
    private Long timestamp;

    /**
     * 访问事件的权重，默认为1
     */
    private int weight;

    /**
     * 发出访问事件的客户端ID
     */
    private String clientId;

    /**
     * Redis key操作类型
     */
    private CacheOperation operation;
}

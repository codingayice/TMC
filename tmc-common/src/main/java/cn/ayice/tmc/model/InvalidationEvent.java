package cn.ayice.tmc.model;

import cn.ayice.tmc.enums.CacheOperation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

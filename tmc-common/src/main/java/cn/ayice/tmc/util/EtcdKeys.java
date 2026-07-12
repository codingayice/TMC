package cn.ayice.tmc.util;

import org.apache.commons.lang3.StringUtils;

/**
 * etcd key 路径工具。
 *
 * <p>后续热点快照下发和缓存失效广播都会通过 etcd watch 完成。
 * 所有 etcd 路径统一从这里生成，避免 SDK 和 server 对路径结构理解不一致。</p>
 */
public class EtcdKeys {

    /**
     * 获取某个应用的热点 key 快照路径。
     */
    public static String hotKeysPath(String appName) {
        if (StringUtils.isBlank(appName)) {
            throw new IllegalArgumentException("应用名称不能为空");
        }
        return "/tmc/hotkeys/" + appName;
    }

    /**
     * 获取某个具体失效事件的路径。
     *
     * <p>eventId 用于保证同一个 app 下每次写操作广播都有唯一位置，避免事件互相覆盖。</p>
     */
    public static String invalidationEventPath(String appName, String eventId) {
        if (StringUtils.isBlank(appName)) {
            throw new IllegalArgumentException("应用名称不能为空");
        }
        if (StringUtils.isBlank(eventId)) {
            throw new IllegalArgumentException("事件ID不能为空");
        }
        return "/tmc/invalidation-events/" + appName + "/" + eventId;
    }

    /**
     * 获取某个应用失效事件的 watch 前缀。
     */
    public static String invalidationEventPrefix(String appName) {
        if (StringUtils.isBlank(appName)) {
            throw new IllegalArgumentException("应用名称不能为空");
        }
        return "/tmc/invalidation-events/" + appName + "/";
    }
}

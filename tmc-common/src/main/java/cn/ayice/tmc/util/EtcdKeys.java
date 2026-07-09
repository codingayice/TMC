package cn.ayice.tmc.util;

import org.apache.commons.lang3.StringUtils;

public class EtcdKeys {

    /**
     * 获取应用的热点key快照路径
     * @param appName
     * @return
     */
    public static String hotKeysPath(String appName) {
        if(StringUtils.isBlank(appName)) {
            throw new IllegalArgumentException("应用名称不能为空");
        }
        return "/tmc/hotkeys/" + appName;
    }

    /**
     * 获取应用的失效事件路径
     * @param appName
     * @param eventId
     * @return
     */
    public static String invalidationEventPath(String appName, String eventId) {
        if(StringUtils.isBlank(appName)) {
            throw new IllegalArgumentException("应用名称不能为空");
        }
        if(StringUtils.isBlank(eventId)) {
            throw new IllegalArgumentException("事件ID不能为空");
        }
        return "/tmc/invalidation-events/" + appName + "/" + eventId;
    }

    public static String invalidationEventPrefix(String appName) {
        if(StringUtils.isBlank(appName)) {
            throw new IllegalArgumentException("应用名称不能为空");
        }
        return "/tmc/invalidation-events/" + appName + "/";
    }
}

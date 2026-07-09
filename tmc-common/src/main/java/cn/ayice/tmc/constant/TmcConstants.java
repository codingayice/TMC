package cn.ayice.tmc.constant;

public class TmcConstants {

    /**
     * TMC访问事件主题
     */
    public static final String ACCESS_EVENT_TOPIC = "tmc-access-events";

    /**
     * 时间轮窗口大小，单位秒
     */
    public static final Integer WINDOWS_SECONDS = 30;

    /**
     * 单个时间片的大小，单位秒
     */
    public static final Integer BUCKET_SECONDS = 3;

    /**
     * 时间轮的时间片数量
     */
    public static final Integer BUCKET_COUNT = WINDOWS_SECONDS / BUCKET_SECONDS;

    /**
     * 热点key过期时间，单位秒
     */
    public static final Integer HOT_KEY_TTL_SECONDS = 30000;

    /**
     * 热点key访问事件权重
     */
    public static final Integer ACCESS_EVENT_WEIGHT = 1;
}

package cn.ayice.tmc.constant;

/**
 * TMC 全局常量。
 *
 * <p>这些值贯穿 SDK、Jedis 接入层、rsyslog/Kafka 链路和服务端热点探测。
 * 放在 common 模块中是为了保证各模块使用同一套 topic、窗口和事件权重定义。</p>
 */
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
     * 热点key过期时间，单位秒。
     *
     * <p>注意部分配置类会把该值换算成毫秒使用。</p>
     */
    public static final Integer HOT_KEY_TTL_SECONDS = 30000;

    /**
     * 热点key访问事件权重
     */
    public static final Integer ACCESS_EVENT_WEIGHT = 1;
}

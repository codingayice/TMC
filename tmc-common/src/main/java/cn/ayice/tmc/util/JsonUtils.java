package cn.ayice.tmc.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 工具类。
 *
 * <p>SDK 上报、rsyslog 转发、Kafka 消费都使用同一份 JSON 协议。
 * 该工具统一序列化规则，避免不同模块各自创建 ObjectMapper 导致字段处理不一致。</p>
 */
public final class JsonUtils {

    /**
     * 全局复用 ObjectMapper。
     *
     * <p>忽略未知字段可以让服务端在协议向后兼容时继续消费旧消息或新消息。</p>
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 工具类不允许实例化。
     */
    private JsonUtils() {
    }

    /**
     * 将对象序列化为单行 JSON 字符串。
     */
    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON序列化失败", e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型。
     */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON反序列化失败:" + type.getName(), e);
        }
    }

    /**
     * 将对象序列化为 JSON line。
     *
     * <p>rsyslog 以换行识别一条消息，因此 SDK 写入访问事件时必须带末尾换行。</p>
     */
    public static String toJsonLine(Object value) {
        return toJson(value) + "\n";
    }
}

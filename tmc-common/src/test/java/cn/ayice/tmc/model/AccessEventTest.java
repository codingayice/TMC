package cn.ayice.tmc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.ayice.tmc.enums.CacheOperation;
import cn.ayice.tmc.util.JsonUtils;
import org.junit.jupiter.api.Test;

/**
 * AccessEvent 模型测试。
 *
 * <p>保障 SDK 上报到 Kafka 的访问事件字段可以被 JSON 正确序列化和反序列化。</p>
 */
class AccessEventTest {

    @Test
    void accessEventShouldRoundTripThroughJson() {
        AccessEvent event = new AccessEvent();
        event.setAppName("tmc-demo");
        event.setKey("product:10001");
        event.setTimestamp(1720000000000L);
        event.setWeight(1);
        event.setClientId("demo-1");
        event.setOperation(CacheOperation.GET);

        String json = JsonUtils.toJson(event);
        AccessEvent parsed = JsonUtils.fromJson(json, AccessEvent.class);

        assertEquals("tmc-demo", parsed.getAppName());
        assertEquals("product:10001", parsed.getKey());
        assertEquals(1720000000000L, parsed.getTimestamp());
        assertEquals(1, parsed.getWeight());
        assertEquals("demo-1", parsed.getClientId());
        assertEquals(CacheOperation.GET, parsed.getOperation());
    }
}

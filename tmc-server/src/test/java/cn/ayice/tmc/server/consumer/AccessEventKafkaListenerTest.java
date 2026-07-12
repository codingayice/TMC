package cn.ayice.tmc.server.consumer;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Kafka listener 适配测试。
 *
 * <p>保障 Spring Kafka 入口只做框架适配，真正的业务处理仍委托给 AccessEventConsumer。</p>
 */
class AccessEventKafkaListenerTest {

    @Test
    void shouldDelegateMessageToAccessEventConsumer() {
        AccessEventConsumer consumer = mock(AccessEventConsumer.class);
        AccessEventKafkaListener listener = new AccessEventKafkaListener(consumer);

        listener.onMessage("{\"key\":\"product:1\"}");

        verify(consumer).consume("{\"key\":\"product:1\"}");
    }
}

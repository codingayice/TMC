package cn.ayice.tmc.server.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka 访问事件监听入口。
 *
 * <p>这个类只做框架适配：从 Kafka 拿到原始字符串后立即交给
 * {@link AccessEventConsumer}。业务校验、指标和热点累加都不放在 listener 中，
 * 这样核心逻辑可以被普通单元测试覆盖。</p>
 */
@Component
public class AccessEventKafkaListener {

    private final AccessEventConsumer consumer;

    public AccessEventKafkaListener(AccessEventConsumer consumer) {
        this.consumer = consumer;
    }

    @KafkaListener(
            topics = "${tmc.server.kafka.topic:tmc-access-events}",
            groupId = "${tmc.server.kafka.group-id:tmc-hotkey-server}",
            concurrency = "${tmc.server.kafka.concurrency:1}"
    )
    public void onMessage(String message) {
        // Kafka message value 是 SDK 写出的 JSON line；这里不解析，只转交给业务消费器。
        consumer.consume(message);
    }
}

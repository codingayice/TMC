package cn.ayice.tmc.server.config;

import cn.ayice.tmc.server.hotkey.HotKeyDetector;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

/**
 * tmc-server Spring Bean 装配。
 *
 * <p>这里仅装配基础设施对象和配置对象，热点探测核心逻辑仍然放在普通 concrete class 中，
 * 这样单元测试可以直接构造对象，不需要启动 Spring 容器。</p>
 */
@Configuration
@EnableConfigurationProperties(TmcServerProperties.class)
public class TmcServerConfiguration {

    @Bean
    public KafkaConsumerProperties kafkaConsumerProperties(TmcServerProperties properties) {
        return properties.getKafka();
    }

    @Bean
    public HotKeyDetectProperties hotKeyDetectProperties(TmcServerProperties properties) {
        return properties.getHotKey();
    }

    @Bean
    public HotKeyDetector hotKeyDetector(HotKeyDetectProperties properties) {
        return new HotKeyDetector(properties);
    }

    /**
     * Kafka value 使用 String 反序列化，因为 SDK 写入 Kafka 的协议就是一行 JSON。
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory(KafkaConsumerProperties properties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, properties.getGroupId());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Spring Kafka listener 容器工厂，供 {@code @KafkaListener} 使用。
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaConsumerProperties properties
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(properties.getConcurrency());
        return factory;
    }
}

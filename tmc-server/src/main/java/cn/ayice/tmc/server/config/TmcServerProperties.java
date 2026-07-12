package cn.ayice.tmc.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * tmc-server 配置根对象，对应 {@code tmc.server.*}。
 */
@ConfigurationProperties(prefix = "tmc.server")
public class TmcServerProperties {

    private KafkaConsumerProperties kafka = new KafkaConsumerProperties();
    private HotKeyDetectProperties hotKey = new HotKeyDetectProperties();

    public KafkaConsumerProperties getKafka() {
        return kafka;
    }

    public void setKafka(KafkaConsumerProperties kafka) {
        this.kafka = kafka;
    }

    public HotKeyDetectProperties getHotKey() {
        return hotKey;
    }

    public void setHotKey(HotKeyDetectProperties hotKey) {
        this.hotKey = hotKey;
    }
}

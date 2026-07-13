package cn.ayice.tmc.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * tmc-server 配置根对象，对应 {@code tmc.server.*}。
 */
@ConfigurationProperties(prefix = "tmc.server")
public class TmcServerProperties {

    /**
     * Kafka 访问事件消费配置。
     */
    private KafkaConsumerProperties kafka = new KafkaConsumerProperties();

    /**
     * 服务端热点探测滑窗配置。
     */
    private HotKeyDetectProperties hotKey = new HotKeyDetectProperties();

    /**
     * etcd 热点快照发布配置。
     */
    private EtcdProperties etcd = new EtcdProperties();

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

    public EtcdProperties getEtcd() {
        return etcd;
    }

    public void setEtcd(EtcdProperties etcd) {
        this.etcd = etcd;
    }
}

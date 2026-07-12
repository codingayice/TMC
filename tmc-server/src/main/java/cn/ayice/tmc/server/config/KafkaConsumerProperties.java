package cn.ayice.tmc.server.config;

import cn.ayice.tmc.constant.TmcConstants;

/**
 * Kafka 消费配置。
 *
 * <p>这里描述的是服务端从哪个 Kafka 集群、哪个 topic 消费 SDK 访问事件。
 * 消费到的原始消息会交给 {@code AccessEventConsumer} 做 JSON 解析和业务校验。</p>
 */
public class KafkaConsumerProperties {

    /**
     * Kafka broker 地址，生产环境应配置为应用服务器可访问的 Kafka advertised listener。
     */
    private String bootstrapServers = "localhost:9092";

    /**
     * SDK 访问事件 topic，默认和公共常量保持一致。
     */
    private String topic = TmcConstants.ACCESS_EVENT_TOPIC;

    /**
     * 热点探测服务的消费组。多个 tmc-server 实例使用同一 groupId 时会共享分区消费。
     */
    private String groupId = "tmc-hotkey-server";

    /**
     * Spring Kafka listener 并发数。当前热点统计是内存态，调高前需要评估实例内并发写入压力。
     */
    private int concurrency = 1;

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }
}

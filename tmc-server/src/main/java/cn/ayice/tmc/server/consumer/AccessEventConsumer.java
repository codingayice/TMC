package cn.ayice.tmc.server.consumer;

import cn.ayice.tmc.enums.CacheOperation;
import cn.ayice.tmc.model.AccessEvent;
import cn.ayice.tmc.server.hotkey.AccessEventAccumulator;
import cn.ayice.tmc.server.metrics.TmcServerMetrics;
import cn.ayice.tmc.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 访问事件业务消费器。
 *
 * <p>职责是把 Kafka 中的 JSON 字符串转成 {@link AccessEvent}，做最小必要校验，
 * 然后写入当前周期访问热度累加器。单条坏消息只能影响自身，不能让 Kafka listener
 * 线程退出。</p>
 */
@Component
public class AccessEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessEventConsumer.class);

    private final AccessEventAccumulator accumulator;
    private final TmcServerMetrics metrics;

    public AccessEventConsumer(AccessEventAccumulator accumulator, TmcServerMetrics metrics) {
        this.accumulator = accumulator;
        this.metrics = metrics;
    }

    /**
     * 消费单条 Kafka message。
     *
     * <p>异常在方法内被消化并转成指标，避免脏数据中断后续消息消费。</p>
     */
    public void consume(String message) {
        try {
            AccessEvent event = JsonUtils.fromJson(message, AccessEvent.class);
            if (!isValid(event)) {
                metrics.incrementMessagesInvalid();
                return;
            }
            accumulator.add(event);
            metrics.incrementMessagesConsumed();
            metrics.incrementAccessEventsAccumulated();
        } catch (IllegalArgumentException e) {
            metrics.incrementMessagesInvalid();
            LOGGER.warn("invalid access event message");
        } catch (RuntimeException e) {
            metrics.incrementMessagesFailed();
            LOGGER.error("consume access event failed", e);
        }
    }

    /**
     * 当前阶段只统计 GET 访问热度。写操作会在后续失效广播阶段处理，不进入热点计数。
     */
    private boolean isValid(AccessEvent event) {
        return event != null
                && hasText(event.getAppName())
                && hasText(event.getKey())
                && event.getTimestamp() != null
                && event.getTimestamp() > 0
                && event.getWeight() > 0
                && event.getOperation() == CacheOperation.GET;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

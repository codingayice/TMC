package cn.ayice.tmc.server.hotkey;

import cn.ayice.tmc.model.AccessEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/**
 * 当前周期访问热度累加器。
 *
 * <p>Kafka listener 线程持续调用 {@link #add(AccessEvent)} 写入访问事件；
 * 映射调度线程每个时间片调用 {@link #drainApp(String)} 取出并清空某个 app 的当前周期热度。
 * 这对应有赞原文中的 {@code Map<String, Map<String, LongAdder>>}。</p>
 */
@Component
public class AccessEventAccumulator {

    /**
     * appName -> key -> 当前周期访问热度。
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, LongAdder>> counters = new ConcurrentHashMap<>();

    /**
     * 累加一条合法访问事件的权重。
     */
    public void add(AccessEvent event) {
        if (event == null) {
            return;
        }
        counters.computeIfAbsent(event.getAppName(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(event.getKey(), ignored -> new LongAdder())
                .add(event.getWeight());
    }

    /**
     * 返回当前已经出现过访问事件的 app 名称。
     */
    public Set<String> appNames() {
        return Collections.unmodifiableSet(counters.keySet());
    }

    /**
     * 取出并清空某个 app 当前周期的所有 key 热度。
     *
     * <p>这里直接 remove app 对应的 map，让后续新事件重新创建新 map，
     * 从而降低消费线程和映射线程之间的锁竞争。</p>
     */
    public Map<String, Long> drainApp(String appName) {
        ConcurrentHashMap<String, LongAdder> appCounters = counters.remove(appName);
        if (appCounters == null || appCounters.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Long> result = new HashMap<>();
        appCounters.forEach((key, value) -> result.put(key, value.sum()));
        return result;
    }
}

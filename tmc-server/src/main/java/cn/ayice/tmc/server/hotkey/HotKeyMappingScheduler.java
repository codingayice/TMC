package cn.ayice.tmc.server.hotkey;

import cn.ayice.tmc.model.HotKeySnapshot;
import cn.ayice.tmc.server.config.HotKeyDetectProperties;
import cn.ayice.tmc.server.metrics.TmcServerMetrics;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 热点映射调度器。
 *
 * <p>这是服务端热点发现的核心调度点：每个时间片执行一次，把当前周期累加器中的
 * 访问热度映射到每个 key 的时间轮中，再由 {@link HotKeyDetector} 生成热点快照。</p>
 */
@Component
public class HotKeyMappingScheduler {

    private final AccessEventAccumulator accumulator;
    private final HotKeyDetector detector;
    private final HotKeySnapshotRepository repository;
    private final TmcServerMetrics metrics;
    private final HotKeyDetectProperties properties;

    /**
     * appName -> key -> key 对应的时间轮。
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, TimeWheelCounter>> wheelStore =
            new ConcurrentHashMap<>();

    public HotKeyMappingScheduler(
            AccessEventAccumulator accumulator,
            HotKeyDetector detector,
            HotKeySnapshotRepository repository,
            TmcServerMetrics metrics,
            HotKeyDetectProperties properties
    ) {
        this.accumulator = accumulator;
        this.detector = detector;
        this.repository = repository;
        this.metrics = metrics;
        this.properties = properties;
    }

    /**
     * 推进一次所有 app 的时间轮。
     *
     * <p>方法保持 public 是为了单元测试可以直接触发一次映射；生产环境由 Spring
     * {@link Scheduled} 定时调用。</p>
     */
    @Scheduled(fixedDelayString = "${tmc.server.hot-key.mapping-interval-millis:3000}")
    public void mapOnce() {
        try {
            Set<String> appNames = new HashSet<>(wheelStore.keySet());
            appNames.addAll(accumulator.appNames());
            for (String appName : appNames) {
                mapApp(appName);
            }
            updateTrackedMetrics();
            metrics.incrementMappingRuns();
        } catch (RuntimeException e) {
            metrics.incrementMappingFailed();
        }
    }

    /**
     * 映射单个 app 的当前周期热度。
     */
    private void mapApp(String appName) {
        Map<String, Long> currentScores = accumulator.drainApp(appName);
        ConcurrentHashMap<String, TimeWheelCounter> wheelMap =
                wheelStore.computeIfAbsent(appName, ignored -> new ConcurrentHashMap<>());

        // 先记录旧 key，当前周期有访问的 key 会从集合中移除，剩下的就是本周期零访问 key。
        Set<String> existingKeys = new HashSet<>(wheelMap.keySet());
        for (Map.Entry<String, Long> entry : currentScores.entrySet()) {
            wheelMap.computeIfAbsent(entry.getKey(), ignored -> new TimeWheelCounter(properties.bucketCount()))
                    .addBucket(entry.getValue());
            existingKeys.remove(entry.getKey());
        }
        // 旧 key 即使本周期没有访问，也必须写入 0 来推动窗口滑动，避免旧热度永久残留。
        for (String oldKey : existingKeys) {
            wheelMap.get(oldKey).addBucket(0);
        }
        // 一个完整窗口都没有访问的 key，其时间轮总和会归零，可以从内存中清理。
        wheelMap.entrySet().removeIf(entry -> entry.getValue().sum() == 0);

        if (wheelMap.isEmpty()) {
            wheelStore.remove(appName);
            repository.remove(appName);
            metrics.setHotKeysDetected(0);
            return;
        }

        HotKeySnapshot snapshot = detector.detect(appName, wheelMap);
        repository.save(snapshot);
        metrics.setHotKeysDetected(snapshot.getHotKeys().size());
    }

    /**
     * 更新当前内存中追踪的 app/key 数量，用于观察服务端内存压力。
     */
    private void updateTrackedMetrics() {
        int appCount = wheelStore.size();
        long keyCount = wheelStore.values().stream().mapToLong(Map::size).sum();
        metrics.updateTracked(appCount, keyCount);
    }
}

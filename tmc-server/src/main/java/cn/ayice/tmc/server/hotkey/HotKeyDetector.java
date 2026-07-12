package cn.ayice.tmc.server.hotkey;

import cn.ayice.tmc.model.HotKey;
import cn.ayice.tmc.model.HotKeySnapshot;
import cn.ayice.tmc.server.config.HotKeyDetectProperties;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 热点 key 探测器。
 *
 * <p>输入是某个 app 下所有 key 的时间轮，输出是该 app 的最新热点快照。
 * 判定规则保持简单明确：窗口总热度达到阈值后参与排序，并只保留 TopN。</p>
 */
public class HotKeyDetector {

    private final HotKeyDetectProperties properties;

    public HotKeyDetector(HotKeyDetectProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        this.properties = properties;
    }

    /**
     * 从时间轮集合中生成热点快照。
     */
    public HotKeySnapshot detect(String appName, Map<String, TimeWheelCounter> counters) {
        long now = System.currentTimeMillis();
        List<HotKey> hotKeys = counters.entrySet().stream()
                .map(entry -> toHotKey(appName, entry.getKey(), entry.getValue().sum(), now))
                .filter(hotKey -> hotKey.getScore() >= properties.getThreshold())
                .sorted(Comparator.comparing(HotKey::getScore).reversed().thenComparing(HotKey::getKey))
                .limit(properties.getTopN())
                .toList();
        return new HotKeySnapshot(appName + "-" + now, appName, hotKeys);
    }

    /**
     * 将一个 key 的窗口热度转换为可下发给 SDK 的热点模型。
     */
    private HotKey toHotKey(String appName, String key, long score, long detectedAt) {
        return new HotKey(appName, key, score, detectedAt, properties.getTtlMillis());
    }
}

package cn.ayice.tmc.server.hotkey;

import cn.ayice.tmc.model.HotKey;
import cn.ayice.tmc.model.HotKeySnapshot;
import cn.ayice.tmc.server.config.HotKeyDetectProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HotKeyDetector 热点探测测试。
 *
 * <p>保障阈值过滤、按热度倒序排序和 TopN 截断规则稳定。</p>
 */
class HotKeyDetectorTest {

    @Test
    void shouldFilterKeysBelowThreshold() {
        HotKeyDetector detector = new HotKeyDetector(properties(10, 10, 300000));
        TimeWheelCounter counter = new TimeWheelCounter(10);
        counter.addBucket(9);

        HotKeySnapshot snapshot = detector.detect("app-a", Map.of("product:1", counter));

        assertTrue(snapshot.getHotKeys().isEmpty());
    }

    @Test
    void shouldDetectKeysReachingThreshold() {
        HotKeyDetector detector = new HotKeyDetector(properties(10, 10, 300000));
        TimeWheelCounter counter = new TimeWheelCounter(10);
        counter.addBucket(10);

        HotKeySnapshot snapshot = detector.detect("app-a", Map.of("product:1", counter));

        assertEquals(1, snapshot.getHotKeys().size());
        HotKey hotKey = snapshot.getHotKeys().get(0);
        assertEquals("app-a", hotKey.getAppName());
        assertEquals("product:1", hotKey.getKey());
        assertEquals(10L, hotKey.getScore());
        assertEquals(300000L, hotKey.getTtlMillis());
        assertFalse(snapshot.getVersion().isBlank());
    }

    @Test
    void shouldSortByScoreAndLimitTopN() {
        HotKeyDetector detector = new HotKeyDetector(properties(1, 2, 300000));
        TimeWheelCounter low = new TimeWheelCounter(10);
        low.addBucket(1);
        TimeWheelCounter high = new TimeWheelCounter(10);
        high.addBucket(5);
        TimeWheelCounter middle = new TimeWheelCounter(10);
        middle.addBucket(3);

        HotKeySnapshot snapshot = detector.detect("app-a", Map.of(
                "low", low,
                "high", high,
                "middle", middle
        ));

        assertEquals(2, snapshot.getHotKeys().size());
        assertEquals("high", snapshot.getHotKeys().get(0).getKey());
        assertEquals("middle", snapshot.getHotKeys().get(1).getKey());
    }

    private static HotKeyDetectProperties properties(long threshold, int topN, long ttlMillis) {
        HotKeyDetectProperties properties = new HotKeyDetectProperties();
        properties.setThreshold(threshold);
        properties.setTopN(topN);
        properties.setTtlMillis(ttlMillis);
        return properties;
    }
}

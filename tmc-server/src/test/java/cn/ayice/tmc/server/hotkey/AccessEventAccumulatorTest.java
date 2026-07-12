package cn.ayice.tmc.server.hotkey;

import cn.ayice.tmc.enums.CacheOperation;
import cn.ayice.tmc.model.AccessEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AccessEventAccumulator 周期累加测试。
 *
 * <p>保障 app/key 维度隔离、同 key 累加和 drain 后开启新周期的行为。</p>
 */
class AccessEventAccumulatorTest {

    private final AccessEventAccumulator accumulator = new AccessEventAccumulator();

    @Test
    void shouldAccumulateSameAppAndKey() {
        accumulator.add(event("app-a", "product:1", 1));
        accumulator.add(event("app-a", "product:1", 2));

        Map<String, Long> scores = accumulator.drainApp("app-a");

        assertEquals(3L, scores.get("product:1"));
    }

    @Test
    void shouldIsolateSameKeyInDifferentApps() {
        accumulator.add(event("app-a", "product:1", 1));
        accumulator.add(event("app-b", "product:1", 2));

        assertEquals(1L, accumulator.drainApp("app-a").get("product:1"));
        assertEquals(2L, accumulator.drainApp("app-b").get("product:1"));
    }

    @Test
    void shouldClearScoresAfterDrain() {
        accumulator.add(event("app-a", "product:1", 1));

        accumulator.drainApp("app-a");

        assertTrue(accumulator.drainApp("app-a").isEmpty());
    }

    @Test
    void shouldKeepNewPeriodAfterDrain() {
        accumulator.add(event("app-a", "product:1", 1));
        accumulator.drainApp("app-a");
        accumulator.add(event("app-a", "product:1", 4));

        assertEquals(4L, accumulator.drainApp("app-a").get("product:1"));
    }

    private static AccessEvent event(String appName, String key, int weight) {
        return new AccessEvent(appName, key, System.currentTimeMillis(), weight, "client-1", CacheOperation.GET);
    }
}

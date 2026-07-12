package cn.ayice.tmc.server.hotkey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TimeWheelCounter 时间轮测试。
 *
 * <p>保障 bucket 覆盖、窗口推进和写入 0 清理旧热度的核心语义。</p>
 */
class TimeWheelCounterTest {

    @Test
    void shouldSumSingleBucket() {
        TimeWheelCounter counter = new TimeWheelCounter(10);

        counter.addBucket(1);

        assertEquals(1L, counter.sum());
    }

    @Test
    void shouldSumAllBuckets() {
        TimeWheelCounter counter = new TimeWheelCounter(10);

        for (int i = 1; i <= 10; i++) {
            counter.addBucket(i);
        }

        assertEquals(55L, counter.sum());
    }

    @Test
    void shouldOverwriteOldBucketWhenWindowMoves() {
        TimeWheelCounter counter = new TimeWheelCounter(3);

        counter.addBucket(1);
        counter.addBucket(2);
        counter.addBucket(3);
        counter.addBucket(10);

        assertEquals(15L, counter.sum());
    }

    @Test
    void shouldAdvanceWindowWithZeroBucket() {
        TimeWheelCounter counter = new TimeWheelCounter(2);

        counter.addBucket(5);
        counter.addBucket(0);
        counter.addBucket(0);

        assertEquals(0L, counter.sum());
    }

    @Test
    void shouldRejectInvalidBucketCount() {
        assertThrows(IllegalArgumentException.class, () -> new TimeWheelCounter(0));
    }
}

package cn.ayice.tmc.server.hotkey;

import java.util.Arrays;

/**
 * 单个 key 的时间轮计数器。
 *
 * <p>例如 30 秒窗口、3 秒时间片会创建 10 个 bucket。每次映射任务执行时写入当前
 * bucket，然后指针前进。所有 bucket 的和就是最近一个窗口内的访问热度。</p>
 */
public class TimeWheelCounter {

    /**
     * 固定长度环形数组，每个元素代表一个时间片的访问热度。
     */
    private final long[] buckets;

    /**
     * 下一次写入的 bucket 下标。
     */
    private int currentIndex;

    public TimeWheelCounter(int bucketCount) {
        if (bucketCount <= 0) {
            throw new IllegalArgumentException("bucketCount must be positive");
        }
        this.buckets = new long[bucketCount];
    }

    /**
     * 写入一个时间片的热度。
     *
     * <p>注意这里是覆盖当前 bucket，不是累加。因为同一个 bucket 位置再次被写入时，
     * 它代表的是新一轮窗口中的当前时间片，旧值必须被淘汰。</p>
     */
    public synchronized void addBucket(long score) {
        buckets[currentIndex] = score;
        currentIndex = (currentIndex + 1) % buckets.length;
    }

    /**
     * 返回当前滑动窗口内的总热度。
     */
    public synchronized long sum() {
        return Arrays.stream(buckets).sum();
    }
}

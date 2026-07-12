package cn.ayice.tmc.server.config;

import cn.ayice.tmc.constant.TmcConstants;

/**
 * 热点探测配置。
 *
 * <p>默认复现有赞文章中的 30 秒窗口、3 秒时间片设计：
 * {@code windowSeconds / bucketSeconds = 10}，即每个 key 维护 10 个 bucket。</p>
 */
public class HotKeyDetectProperties {

    /**
     * 滑动窗口总长度，单位秒。
     */
    private int windowSeconds = TmcConstants.WINDOWS_SECONDS;

    /**
     * 单个时间片长度，单位秒。调度器每次执行时会推进一个时间片。
     */
    private int bucketSeconds = TmcConstants.BUCKET_SECONDS;

    /**
     * 30 秒窗口内访问热度达到该值后，key 才会被认为是热点。
     */
    private long threshold = 100;

    /**
     * 每个 app 最多输出多少个热点 key，防止一次快照过大。
     */
    private int topN = 100;

    /**
     * 热点 key 在客户端本地缓存中的建议有效期，单位毫秒。
     */
    private long ttlMillis = 300000L;

    /**
     * 映射任务执行周期，默认和 bucketSeconds 对齐。
     */
    private long mappingIntervalMillis = TmcConstants.BUCKET_SECONDS * 1000L;

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getBucketSeconds() {
        return bucketSeconds;
    }

    public void setBucketSeconds(int bucketSeconds) {
        this.bucketSeconds = bucketSeconds;
    }

    public long getThreshold() {
        return threshold;
    }

    public void setThreshold(long threshold) {
        this.threshold = threshold;
    }

    public int getTopN() {
        return topN;
    }

    public void setTopN(int topN) {
        this.topN = topN;
    }

    public long getTtlMillis() {
        return ttlMillis;
    }

    public void setTtlMillis(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public long getMappingIntervalMillis() {
        return mappingIntervalMillis;
    }

    public void setMappingIntervalMillis(long mappingIntervalMillis) {
        this.mappingIntervalMillis = mappingIntervalMillis;
    }

    /**
     * 计算时间轮 bucket 数量，并在启动或使用时校验窗口配置是否合法。
     */
    public int bucketCount() {
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be positive");
        }
        if (bucketSeconds <= 0) {
            throw new IllegalArgumentException("bucketSeconds must be positive");
        }
        if (windowSeconds % bucketSeconds != 0) {
            throw new IllegalArgumentException("windowSeconds must be divisible by bucketSeconds");
        }
        return windowSeconds / bucketSeconds;
    }
}

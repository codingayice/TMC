package com.tmc.common.constant;

/**
 * TMC 全局常量
 */
public interface TmcConstants {

    /** ======== 时间轮配置（文章 4-3-1） ======== */
    int TIME_WHEEL_SLOTS = 10;
    int SLOT_INTERVAL_SECONDS = 3;
    int SLOT_INTERVAL_MS = 3_000;
    int WINDOW_SECONDS = 30;
    int DETECT_INTERVAL_SECONDS = 3;

    /** ======== 本地缓存配置（文章 3-2-3） ======== */
    long LOCAL_CACHE_MAX_BYTES = 64 * 1024 * 1024L;   // 64MB
    int LOCAL_CACHE_MAX_ENTRIES = 10_000;

    /** ======== etcd 路径前缀 ======== */
    String ETCD_HOTKEY_PREFIX = "/tmc/hotkeys/";
    String ETCD_INVALIDATE_PREFIX = "/tmc/invalidate/";

    /** ======== 默认配置值 ======== */
    long DEFAULT_HOT_THRESHOLD = 100;
    int DEFAULT_TOP_N = 100;
}

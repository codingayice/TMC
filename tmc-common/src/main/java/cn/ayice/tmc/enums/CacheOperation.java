package cn.ayice.tmc.enums;

import lombok.Getter;

/**
 * Redis 操作类型。
 *
 * <p>SDK 和服务端会根据操作类型区分读请求和写请求：读请求用于热点探测，
 * 写请求后续用于本地缓存失效广播。</p>
 */
@Getter
public enum CacheOperation {

    GET("get"),
    MGET("mget"),
    SET("set"),
    DEL("del"),
    EXPIRE("expire");

    private final String value;

    CacheOperation(String value) {
        this.value = value;
    }


    /**
     * 判断当前操作是否为读操作。只有读操作才应该参与访问热度统计。
     */
    public  boolean isReadOperation() {
        return this == GET || this == MGET;
    }

    /**
     * 判断当前操作是否为写操作。写操作会改变 Redis 数据，需要通知其他客户端失效本地缓存。
     */
    public  boolean isWriteOperation() {
        return this == SET || this == DEL || this == EXPIRE;
    }
}

package cn.ayice.tmc.enums;

import lombok.Getter;

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
     * 判断当前操作是否为读操作
     * @return
     */
    public  boolean isReadOperation() {
        return this == GET || this == MGET;
    }

    /**
     * 判断当前操作是否为写操作,写操作需要通知其他客户端进行缓存失效
     * @return
     */
    public  boolean isWriteOperation() {
        return this == SET || this == DEL || this == EXPIRE;
    }
}

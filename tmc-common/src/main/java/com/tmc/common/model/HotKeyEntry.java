package com.tmc.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点 key——服务端探测到后推送给 SDK 的数据模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotKeyEntry {
    private String appName;
    private String uniqueKey;
    private long heat;
    private long expireAt;
}

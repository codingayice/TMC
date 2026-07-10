# Phase 3：SDK 本地缓存核心设计

## 1. 阶段目标

Phase 3 的目标是在 `tmc-sdk` 中实现客户端热点 key 本地缓存能力。

完成后，SDK 应具备以下能力：

1. 根据配置初始化客户端组件。
2. 维护当前应用的热点 key 表。
3. 对热点 key 优先读取本地 Caffeine 缓存。
4. 本地缓存未命中时回源远端缓存。
5. 回源成功后把热点 key 的值写入本地缓存。
6. 对非热点 key 直接回源，不污染本地缓存。
7. 支持本地缓存失效。
8. 记录基础读路径指标。
9. SDK 本地异常时降级回源，避免影响业务读请求。

本阶段只实现 `tmc-sdk` 的本地缓存核心，不实现 Jedis 接入层、访问事件上报、Kafka 消费、etcd watch 和服务端热点探测。

## 2. 阶段边界

### 2.1 本阶段实现

本阶段实现：

- SDK 配置绑定。
- 本地 Caffeine 缓存封装。
- 热点 key 管理。
- 远端缓存读取抽象。
- `TmcClient` 核心读路径。
- 本地缓存失效入口。
- 客户端基础指标。
- SDK 初始化配置。
- 单元测试。

### 2.2 本阶段不实现

本阶段不实现：

- `tmc-jedis`。
- Jedis 命令适配。
- rsyslog 访问事件上报。
- Kafka 链路。
- etcd 热点下发。
- etcd 失效广播。
- server 端热点探测。

## 3. 设计原则

### 3.1 SDK 提供完整客户端能力

`tmc-sdk` 不是一组零散工具类，而是客户端核心能力模块。

它需要负责：

- 读取 TMC 客户端配置。
- 初始化本地缓存。
- 初始化热点 key 管理器。
- 初始化指标组件。
- 暴露统一的 `TmcClient` 入口。

业务侧或后续接入层只需要面向 `TmcClient` 使用 SDK 能力。

### 3.2 核心逻辑和初始化逻辑分离

SDK 需要能被应用正常初始化，但初始化逻辑不能吞掉核心读路径。

建议边界：

```text
config/autoconfigure
  -> 负责配置绑定和组件初始化

core/cache/hotkey/metrics/remote
  -> 负责 TMC 核心逻辑
```

这样后续调试问题时，可以清楚地区分：

- 是配置没有生效。
- 是组件没有创建。
- 是热点判断有问题。
- 是本地缓存读写有问题。
- 是远端缓存回源有问题。

### 3.3 SDK 不直接绑定 Jedis

`tmc-sdk` 不直接依赖 Jedis。

本阶段只定义远端缓存读取抽象：

```java
public interface RemoteCacheClient {
    String get(String key);
}
```

原因：

- `tmc-sdk` 负责 TMC 核心能力。
- `tmc-jedis` 负责 Jedis 风格接入。
- 后续如果支持 Lettuce 或其他 Redis 客户端，可以继续复用 `tmc-sdk`。

### 3.4 读路径优先稳定

TMC 是增强能力，不能因为 SDK 内部异常扩大业务故障。

要求：

- 热点判断异常时回源。
- 本地缓存读取异常时回源。
- 本地缓存写入异常时返回已读取到的远端结果。
- 指标统计异常不能影响业务返回。
- 远端缓存异常继续向外抛出。

## 4. 包结构

建议在 `tmc-sdk` 中建立以下包：

```text
cn.ayice.tmc.sdk.autoconfigure
cn.ayice.tmc.sdk.config
cn.ayice.tmc.sdk.core
cn.ayice.tmc.sdk.cache
cn.ayice.tmc.sdk.hotkey
cn.ayice.tmc.sdk.metrics
cn.ayice.tmc.sdk.remote
```

职责：

| 包 | 职责 |
|---|---|
| `autoconfigure` | SDK 初始化配置 |
| `config` | 客户端配置对象 |
| `core` | `TmcClient` 主入口 |
| `cache` | 本地缓存接口和 Caffeine 实现 |
| `hotkey` | 热点 key 管理 |
| `metrics` | 客户端指标 |
| `remote` | 远端缓存读取抽象 |

## 5. 配置设计

### 5.1 配置示例

```yaml
tmc:
  enabled: true
  app-name: product-service
  client-id: product-service-1
  local-cache:
    enabled: true
    maximum-size: 10000
    expire-after-write-millis: 30000
  hot-key:
    ttl-millis: 30000
```

### 5.2 `TmcProperties`

绑定 `tmc.*`。

建议字段：

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | `boolean` | `true` | 是否启用 TMC |
| `appName` | `String` | 无 | 应用名 |
| `clientId` | `String` | 自动生成 | 当前客户端实例 ID |
| `localCache` | `LocalCacheProperties` | 默认对象 | 本地缓存配置 |
| `hotKey` | `HotKeyProperties` | 默认对象 | 热点 key 配置 |

约束：

- `enabled=true` 时 `appName` 必须非空。
- `clientId` 为空时生成一个稳定的进程内 ID。
- 嵌套配置对象不能为空。

### 5.3 `LocalCacheProperties`

绑定 `tmc.local-cache.*`。

建议字段：

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | `boolean` | `true` | 是否启用本地缓存 |
| `maximumSize` | `long` | `10000` | 本地缓存最大 key 数 |
| `expireAfterWriteMillis` | `long` | `30000` | value 写入后的过期时间 |

约束：

- `maximumSize > 0`。
- `expireAfterWriteMillis > 0`。

### 5.4 `HotKeyProperties`

绑定 `tmc.hot-key.*`。

建议字段：

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ttlMillis` | `long` | `30000` | 热点状态默认有效期 |

约束：

- `ttlMillis > 0`。

## 6. SDK 初始化设计

### 6.1 初始化对象

SDK 根据配置初始化以下对象：

```text
TmcProperties
LocalCache
HotKeyManager
TmcMetrics
TmcClient
```

其中 `TmcClient` 依赖远端缓存读取能力。

### 6.2 远端缓存依赖

本阶段 `tmc-sdk` 只消费 `RemoteCacheClient`，不负责创建具体 Redis 客户端。

后续关系：

```text
tmc-jedis
  -> 提供 JedisRemoteCacheClient
  -> 注入给 TmcClient
```

测试或 demo 可以先提供 fake 实现：

```java
class FakeRemoteCacheClient implements RemoteCacheClient {
    private final Map<String, String> data = new ConcurrentHashMap<>();

    @Override
    public String get(String key) {
        return data.get(key);
    }
}
```

### 6.3 Spring Boot 3 注册文件

如果使用 Spring Boot 3，需要提供自动配置注册文件：

```text
tmc-sdk/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

内容：

```text
cn.ayice.tmc.sdk.autoconfigure.TmcAutoConfiguration
```

这个文件属于 SDK 初始化基础设施，不承载业务逻辑。

## 7. 核心组件设计

### 7.1 `RemoteCacheClient`

远端缓存读取抽象。

```java
public interface RemoteCacheClient {
    String get(String key);
}
```

约束：

- 返回 `null` 表示远端不存在该 key。
- 抛出异常表示远端缓存读取失败。
- 本阶段不定义写接口。

### 7.2 `LocalCache`

本地缓存接口。

```java
public interface LocalCache {
    String getIfPresent(String key);

    void put(String key, String value);

    void invalidate(String key);

    long estimatedSize();
}
```

约束：

- 不缓存 `null`。
- `invalidate` 对不存在的 key 幂等。
- 不向外暴露 Caffeine 类型。

### 7.3 `CaffeineLocalCache`

`LocalCache` 的 Caffeine 实现。

配置：

```text
maximumSize = tmc.local-cache.maximum-size
expireAfterWrite = tmc.local-cache.expire-after-write-millis
```

职责：

- 维护热点 key 的本地 value。
- 控制本地缓存容量。
- 控制本地 value 过期。

### 7.4 `HotKeyManager`

维护当前应用的热点 key 表。

建议方法：

```java
void addHotKey(HotKey hotKey);

void removeHotKey(String key);

boolean isHotKey(String key);

void updateSnapshot(HotKeySnapshot snapshot);

int hotKeyCount();
```

内部结构：

```text
ConcurrentHashMap<String, HotKeyState>
```

`HotKeyState` 保存：

- `key`
- `score`
- `detectedAt`
- `expireAt`

热点过期规则：

```text
expireAt = detectedAt + ttlMillis
```

`isHotKey(key)` 发现热点过期时，可以删除该 key 并返回 `false`。

### 7.5 `TmcMetrics`

记录 SDK 读路径指标。

建议指标：

| 指标 | 含义 |
|---|---|
| `totalGets` | 总 get 次数 |
| `hotKeyGets` | 热点 key get 次数 |
| `localCacheHits` | 本地缓存命中次数 |
| `localCacheMisses` | 本地缓存未命中次数 |
| `remoteGets` | 回源远端缓存次数 |
| `fallbackGets` | SDK 本地异常降级回源次数 |

实现建议：

- 使用 `LongAdder`。
- 提供 `snapshot()`。
- 指标更新失败不能影响读路径。

### 7.6 `TmcClient`

`TmcClient` 是 SDK 主入口。

建议构造依赖：

```text
TmcProperties
RemoteCacheClient
HotKeyManager
LocalCache
TmcMetrics
```

建议方法：

```java
String get(String key);

void addHotKey(HotKey hotKey);

void removeHotKey(String key);

void invalidate(String key);

TmcMetricsSnapshot metrics();
```

说明：

- `get` 是读路径入口。
- `addHotKey` 和 `removeHotKey` 便于当前阶段验证热点缓存。
- `invalidate` 为后续写操作和失效广播预留。
- `metrics` 为 demo 和 benchmark 预留。

## 8. 读流程设计

### 8.1 非热点 key

```text
TmcClient.get(key)
  -> totalGets + 1
  -> HotKeyManager.isHotKey(key) 返回 false
  -> RemoteCacheClient.get(key)
  -> remoteGets + 1
  -> 返回远端结果
```

非热点 key 不写本地缓存。

### 8.2 热点 key 首次读取

```text
TmcClient.get(key)
  -> totalGets + 1
  -> HotKeyManager.isHotKey(key) 返回 true
  -> hotKeyGets + 1
  -> LocalCache.getIfPresent(key) 返回 null
  -> localCacheMisses + 1
  -> RemoteCacheClient.get(key)
  -> remoteGets + 1
  -> 远端结果非 null 时 LocalCache.put(key, value)
  -> 返回远端结果
```

远端返回 `null` 时不写本地缓存。

### 8.3 热点 key 本地命中

```text
TmcClient.get(key)
  -> totalGets + 1
  -> HotKeyManager.isHotKey(key) 返回 true
  -> hotKeyGets + 1
  -> LocalCache.getIfPresent(key) 返回 value
  -> localCacheHits + 1
  -> 返回 value
```

本地命中时不能访问远端缓存。

### 8.4 本地缓存关闭

```text
TmcClient.get(key)
  -> totalGets + 1
  -> local-cache.enabled=false
  -> RemoteCacheClient.get(key)
  -> remoteGets + 1
  -> 返回远端结果
```

关闭本地缓存后，无论是否热点，都直接回源。

## 9. 本地失效设计

本阶段实现当前进程内失效。

建议方法：

```java
void invalidate(String key);
```

执行逻辑：

```text
LocalCache.invalidate(key)
```

是否同时移除热点状态需要谨慎：

- 写操作导致的是 value 失效，不一定代表 key 不再是热点。
- 因此 `invalidate(key)` 默认只删除本地缓存值。
- `removeHotKey(key)` 专门用于移除热点状态。

后续 Phase 10 的跨节点失效广播也应调用 `invalidate(key)`，而不是删除热点状态。

## 10. 异常降级设计

### 10.1 SDK 本地异常

以下异常属于 SDK 本地增强能力异常：

- 热点判断异常。
- 本地缓存读取异常。
- 本地缓存写入异常。
- 指标统计异常。

处理原则：

- 读请求尽量回源。
- 本地缓存写入失败时返回已读取到的远端结果。
- 指标异常不影响返回。
- 记录 `fallbackGets`。

### 10.2 远端缓存异常

`RemoteCacheClient.get` 异常不吞掉。

原因：

- 远端缓存是业务真实依赖。
- SDK 无法构造正确结果。
- 吞掉异常会掩盖真实故障。

## 11. 与 `tmc-jedis` 的关系

`tmc-sdk` 提供 TMC 客户端核心。

`tmc-jedis` 后续负责把 Jedis 风格命令接入 SDK：

```text
业务调用 TmcJedis.get(key)
  -> TmcJedis 调用 TmcClient.get(key)
  -> TmcClient 判断热点、本地缓存、回源
  -> 回源时调用 JedisRemoteCacheClient
  -> JedisRemoteCacheClient 调用真实 jedis.get(key)
```

写操作后续设计：

```text
业务调用 TmcJedis.set(key, value)
  -> 真实 jedis.set(key, value)
  -> 成功后 TmcClient.invalidate(key)
  -> 后续 Phase 10 发布 etcd 失效事件
```

因此：

- `tmc-sdk` 负责热点缓存核心能力。
- `tmc-jedis` 负责 Jedis 风格透明接入。

## 12. 测试设计

建议测试类：

```text
TmcPropertiesTest
TmcAutoConfigurationTest
TmcClientTest
HotKeyManagerTest
CaffeineLocalCacheTest
TmcMetricsTest
```

### 12.1 `TmcPropertiesTest`

覆盖：

1. 默认配置值正确。
2. `tmc.*` 配置可以绑定。
3. 非法容量和 TTL 会被拒绝。
4. `clientId` 为空时可以生成。

### 12.2 `TmcAutoConfigurationTest`

覆盖：

1. SDK 基础组件可以初始化。
2. 提供 `RemoteCacheClient` 后可以获得 `TmcClient`。
3. `tmc.enabled=false` 时不启用 TMC 组件。

该测试只验证 SDK 初始化，不测试业务读路径。

### 12.3 `TmcClientTest`

覆盖：

1. 非热点 key 直接回源。
2. 热点 key 首次读取本地 miss 并回源。
3. 热点 key 第二次读取本地 hit，不再回源。
4. 远端返回 `null` 时不写本地缓存。
5. 本地缓存关闭时热点 key 也直接回源。
6. 本地缓存异常时降级回源。
7. 远端缓存异常时向外抛出。
8. `invalidate` 删除本地缓存值但不移除热点状态。

### 12.4 `HotKeyManagerTest`

覆盖：

1. `addHotKey` 后 `isHotKey` 返回 true。
2. `removeHotKey` 后 `isHotKey` 返回 false。
3. 热点 TTL 过期后 `isHotKey` 返回 false。
4. `updateSnapshot` 可以批量刷新热点表。
5. 空快照可以清空热点表。
6. 不同 appName 的快照不会污染当前应用。

### 12.5 `CaffeineLocalCacheTest`

覆盖：

1. `put` 后可以 `getIfPresent`。
2. `invalidate` 后读不到。
3. `null` value 不进入缓存。
4. 超过最大容量时缓存大小受限制。

### 12.6 `TmcMetricsTest`

覆盖：

1. 指标初始值为 `0`。
2. 各类 increment 生效。
3. `snapshot` 返回稳定读数。

## 13. 开发顺序

建议按以下顺序开发：

1. 在 `tmc-sdk/pom.xml` 中引入 `tmc-common`、Caffeine、Spring Boot autoconfigure、configuration processor、test 依赖。
2. 创建 `TmcProperties`、`LocalCacheProperties`、`HotKeyProperties`。
3. 创建 `RemoteCacheClient`。
4. 创建 `LocalCache`。
5. 实现 `CaffeineLocalCache`。
6. 实现 `HotKeyManager`。
7. 实现 `TmcMetrics` 和 `TmcMetricsSnapshot`。
8. 实现 `TmcClient`。
9. 实现 SDK 初始化配置。
10. 创建 Spring Boot 3 自动配置注册文件。
11. 编写配置和初始化测试。
12. 编写核心读路径测试。
13. 运行 `mvn -pl tmc-sdk -am test`。
14. 运行 `mvn test`。

## 14. 验收清单

Phase 3 完成时逐项检查：

- [ ] `tmc-sdk` 依赖 `tmc-common`。
- [ ] `tmc-sdk` 引入 Caffeine。
- [ ] 存在 `TmcProperties` 并绑定 `tmc.*`。
- [ ] 存在 `RemoteCacheClient`。
- [ ] 存在 `LocalCache`。
- [ ] 存在 `CaffeineLocalCache`。
- [ ] 存在 `HotKeyManager`。
- [ ] 存在 `TmcMetrics` 和 `TmcMetricsSnapshot`。
- [ ] 存在 `TmcClient`。
- [ ] SDK 初始化测试通过。
- [ ] 非热点 key 不进入本地缓存。
- [ ] 热点 key 首次读取会回源并写入本地缓存。
- [ ] 热点 key 二次读取能命中本地缓存。
- [ ] 本地缓存关闭后读请求直接回源。
- [ ] `invalidate` 只删除本地缓存值，不删除热点状态。
- [ ] SDK 本地异常时可以降级回源。
- [ ] 远端缓存异常时不会被 SDK 吞掉。
- [ ] 核心读路径测试通过。
- [ ] `mvn -pl tmc-sdk -am test` 通过。
- [ ] `mvn test` 通过。

## 15. 和后续阶段的关系

Phase 3 完成后，后续阶段可以直接复用：

- Phase 4 的 `tmc-jedis` 提供 `RemoteCacheClient` 实现，并将 Jedis 风格命令转发给 `TmcClient`。
- Phase 5 的访问事件采集可以挂在 `TmcClient.get` 读路径中。
- Phase 9 的 etcd 热点下发可以调用 `HotKeyManager.updateSnapshot`。
- Phase 10 的失效广播可以调用 `TmcClient.invalidate`。
- Phase 11 的 demo 可以使用 `TmcClient` 或 `TmcJedis` 验证业务读写。
- Phase 12 的 benchmark 可以通过 `TmcMetrics` 统计本地缓存效果。

Phase 3 的重点是把客户端读路径做扎实：热点判断、本地缓存、回源、失效、指标和降级都要有清晰边界。

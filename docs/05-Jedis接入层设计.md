# Phase 4：Jedis 接入层设计

## 1. 阶段目标

Phase 4 的目标是在 `tmc-jedis` 中实现 Jedis 风格接入层，让业务读写 Redis 时可以自然经过 TMC 客户端能力。

Phase 3 已经完成 `tmc-sdk` 的本地缓存核心：

- `TmcClient.get(key, () -> jedis.get(key))`。
- `HotKeyManager`。
- `CaffeineLocalCache`。
- `TmcMetrics`。

本阶段要做的是把真实 Jedis 读写命令接到这些能力上。

完成后应具备以下能力：

1. `tmc-jedis` 提供 `TmcJedis`。
2. `TmcJedis.get(key)` 进入 `TmcClient.get(key, () -> jedis.get(key))`。
3. SDK 回源时直接执行 `TmcJedis` 传入的 `jedis.get(key)`。
4. `TmcJedis.set/del/expire` 先执行真实 Jedis 写操作。
5. 写操作成功后调用 `TmcClient.invalidate(key)` 删除当前进程本地缓存值。
6. 写操作失败时不触发本地失效。
7. 单元测试覆盖读路径转发和写后失效。

本阶段不实现 rsyslog 上报、Kafka、etcd 失效广播、服务端热点探测和连接池封装。

## 2. 阶段边界

### 2.1 本阶段实现

本阶段实现：

- `TmcJedis`。
- `get` 读操作接入 SDK。
- `set` 写操作本地失效。
- `del` 删除操作本地失效。
- `expire` 过期操作本地失效。
- 基础构造方式。
- 单元测试。

### 2.2 本阶段不实现

本阶段不实现：

- `TmcJedisPool`。
- Spring Boot 自动创建 Jedis 连接。
- 批量命令。
- pipeline。
- transaction。
- Lua script。
- rsyslog 访问事件上报。
- etcd 跨节点失效广播。

这些能力后续阶段再扩展。

## 3. 设计原则

### 3.1 保持 Jedis 风格

`TmcJedis` 的方法命名和返回值应尽量贴近原生 Jedis。

示例：

```java
String value = tmcJedis.get("product:10001");
String result = tmcJedis.set("product:10001", "value");
Long deleted = tmcJedis.del("product:10001");
Long expireResult = tmcJedis.expire("product:10001", 60);
```

这样后续业务从 Jedis 切换到 TMC 时，调用方式变化最小。

### 3.2 读操作交给 SDK

`TmcJedis.get(key)` 不应该自己判断热点，也不应该直接操作 Caffeine。

读路径必须交给 `TmcClient`：

```text
TmcJedis.get(key)
  -> TmcClient.get(key, () -> jedis.get(key))
  -> HotKeyManager 判断热点
  -> CaffeineLocalCache 查询本地缓存
  -> 必要时执行 jedis.get(key)
```

这样可以保证热点判断、本地缓存、指标和降级逻辑都集中在 `tmc-sdk`。

### 3.3 写操作先落 Redis，再失效本地缓存

写操作顺序必须是：

```text
真实 Jedis 写操作成功
  -> TmcClient.invalidate(key)
```

不能在写 Redis 前先删本地缓存。

原因：

- 如果 Redis 写失败，旧值仍然是远端真实值。
- 此时提前删除本地缓存会导致后续读请求不必要回源。
- 更重要的是，失效事件应该表达“远端值已经变化”。

### 3.4 当前阶段只保证当前进程本地一致

Phase 4 的写后失效只删除当前 JVM 进程内本地缓存。

跨进程一致性依赖后续 Phase 10：

```text
当前节点写成功
  -> 当前节点 TmcClient.invalidate(key)
  -> 发布 etcd InvalidationEvent
  -> 其他节点收到事件
  -> 其他节点 TmcClient.invalidate(key)
```

因此本阶段验收标准只要求当前进程内缓存值被删除。

## 4. 包结构

建议在 `tmc-jedis` 中建立以下包：

```text
cn.ayice.tmc.jedis
```

职责：

| 包 | 职责 |
|---|---|
| `cn.ayice.tmc.jedis` | Jedis 风格客户端入口 |

建议类：

```text
cn.ayice.tmc.jedis.TmcJedis
```

当前已有的占位 `Main` 不属于核心设计，可以删除或保留到后续清理。

## 5. 核心组件设计

### 5.1 `TmcJedis`

`TmcJedis` 是业务侧使用的 Jedis 风格入口。

建议构造依赖：

```text
Jedis
TmcClient
```

建议方法：

```java
String get(String key);

String set(String key, String value);

Long del(String key);

Long expire(String key, int seconds);
```

职责划分：

- `get`：交给 `TmcClient.get(key, () -> jedis.get(key))`。
- `set`：调用 `jedis.set(key, value)`，成功后 `tmcClient.invalidate(key)`。
- `del`：调用 `jedis.del(key)`，成功删除后 `tmcClient.invalidate(key)`。
- `expire`：调用 `jedis.expire(key, seconds)`，成功设置过期后 `tmcClient.invalidate(key)`。

## 6. 读流程设计

### 6.1 非热点 key

```text
业务调用 TmcJedis.get(key)
  -> TmcClient.get(key, () -> jedis.get(key))
  -> HotKeyManager.isHotKey(key) 返回 false
  -> jedis.get(key)
  -> 返回 Redis value
```

非热点 key 不进入本地缓存。

### 6.2 热点 key 本地未命中

```text
业务调用 TmcJedis.get(key)
  -> TmcClient.get(key, () -> jedis.get(key))
  -> HotKeyManager.isHotKey(key) 返回 true
  -> CaffeineLocalCache.getIfPresent(key) 返回 null
  -> jedis.get(key)
  -> CaffeineLocalCache.put(key, value)
  -> 返回 Redis value
```

### 6.3 热点 key 本地命中

```text
业务调用 TmcJedis.get(key)
  -> TmcClient.get(key, () -> jedis.get(key))
  -> HotKeyManager.isHotKey(key) 返回 true
  -> CaffeineLocalCache.getIfPresent(key) 返回 value
  -> 直接返回 value
```

本地命中时不会调用 `jedis.get(key)`。

## 7. 写流程设计

### 7.1 `set`

```text
业务调用 TmcJedis.set(key, value)
  -> jedis.set(key, value)
  -> 返回 OK
  -> TmcClient.invalidate(key)
  -> 返回 OK
```

触发本地失效条件：

```text
result == "OK"
```

如果 Jedis 抛异常：

- 异常继续向外抛出。
- 不调用 `TmcClient.invalidate(key)`。

### 7.2 `del`

```text
业务调用 TmcJedis.del(key)
  -> jedis.del(key)
  -> 返回 deleted count
  -> deleted count > 0 时 TmcClient.invalidate(key)
  -> 返回 deleted count
```

触发本地失效条件：

```text
deleted count > 0
```

如果 key 原本不存在，`jedis.del(key)` 返回 `0`，不需要失效。

### 7.3 `expire`

```text
业务调用 TmcJedis.expire(key, seconds)
  -> jedis.expire(key, seconds)
  -> 返回 expire result
  -> expire result == 1 时 TmcClient.invalidate(key)
  -> 返回 expire result
```

触发本地失效条件：

```text
expire result == 1
```

说明：

- 设置过期时间会改变 key 的生命周期。
- 对热点本地缓存来说，远端 key 的有效期已经变化，所以当前进程本地值应失效。

## 8. 构造方式设计

Phase 4 先支持最小构造方式：

```java
Jedis jedis = new Jedis("127.0.0.1", 6379);
TmcClient tmcClient = ...;
TmcJedis tmcJedis = new TmcJedis(jedis, tmcClient);
```

说明：

- 本阶段不封装连接池。
- 本阶段不处理 Jedis 生命周期，调用方负责关闭 `jedis`。

后续扩展：

```text
TmcJedisPool
  -> 从 JedisPool 获取 Jedis
  -> 包装成 TmcJedis
  -> 处理资源归还
```

## 9. 异常处理设计

### 9.1 Jedis 读异常

`jedis.get(key)` 异常由 `TmcClient` 读路径原样抛出。

原因：

- 远端 Redis 是业务真实依赖。
- SDK 和接入层不能构造正确返回值。

### 9.2 Jedis 写异常

`set/del/expire` 中 Jedis 抛异常时：

- 异常原样抛出。
- 不触发 `TmcClient.invalidate(key)`。

### 9.3 本地失效异常

如果 `TmcClient.invalidate(key)` 抛异常：

建议当前阶段先让异常抛出，测试暴露问题。

后续进入稳定性阶段时，可以评估是否将本地失效异常降级为日志和指标。

## 10. 测试设计

建议测试类：

```text
TmcJedisTest
```

### 10.1 `TmcJedisTest`

覆盖：

1. `get(key)` 调用 `TmcClient.get(key, () -> jedis.get(key))`。
2. `TmcClient` 执行读取动作时会调用真实 `jedis.get(key)`。
3. `set` 返回 `OK` 时调用 `TmcClient.invalidate(key)`。
4. `set` 非 `OK` 时不调用 `invalidate`。
5. `del` 返回大于 `0` 时调用 `invalidate`。
6. `del` 返回 `0` 时不调用 `invalidate`。
7. `expire` 返回 `1` 时调用 `invalidate`。
8. `expire` 返回 `0` 时不调用 `invalidate`。
9. Jedis 写操作抛异常时不调用 `invalidate`。

测试方式：

- 使用 Mockito mock `Jedis` 和 `TmcClient`。
- 验证调用次数和返回值。

## 11. 开发顺序

建议按以下顺序开发：

1. 清理或忽略 `tmc-jedis` 中的占位 `Main`。
2. 创建 `cn.ayice.tmc.jedis` 包。
3. 实现 `TmcJedis` 构造器。
4. 实现 `TmcJedis.get`，向 `TmcClient` 传入 `() -> jedis.get(key)`。
5. 编写 `get` 转发测试。
6. 实现 `set` 和写后失效测试。
7. 实现 `del` 和写后失效测试。
8. 实现 `expire` 和写后失效测试。
9. 运行 `mvn -pl tmc-jedis -am test`。
10. 运行 `mvn test`。

## 12. 验收清单

Phase 4 完成时逐项检查：

- [ ] `tmc-jedis` 依赖 `tmc-sdk`。
- [ ] 存在 `TmcJedis`。
- [ ] `TmcJedis.get` 调用 `TmcClient.get(key, () -> jedis.get(key))`。
- [ ] SDK 回源时会执行真实 `jedis.get(key)`。
- [ ] `TmcJedis.set` 成功后触发 `TmcClient.invalidate`。
- [ ] `TmcJedis.del` 删除成功后触发 `TmcClient.invalidate`。
- [ ] `TmcJedis.expire` 设置成功后触发 `TmcClient.invalidate`。
- [ ] 写操作失败或无效果时不触发本地失效。
- [ ] Jedis 异常不会被接入层吞掉。
- [ ] 单元测试覆盖读转发和写后失效。
- [ ] `mvn -pl tmc-jedis -am test` 通过。
- [ ] `mvn test` 通过。

## 13. 和后续阶段的关系

Phase 4 完成后：

- Phase 5 可以在 `TmcClient.get` 或接入层读路径上补访问事件采集。
- Phase 10 可以在写操作成功后扩展 etcd 失效事件发布。
- Phase 11 demo 可以通过 `TmcJedis` 展示业务读写。
- Phase 12 benchmark 可以比较 direct Jedis 和 TMC Jedis 的 Redis 请求量。

本阶段的重点是把业务 Redis 调用接入 TMC 客户端核心，而不是扩展所有 Jedis 命令。先把 `get/set/del/expire` 的边界做扎实，后续再扩展命令覆盖面。

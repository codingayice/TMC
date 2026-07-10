# Phase 5：SDK 访问事件采集设计

## 1. 阶段目标

Phase 5 的目标是在 `tmc-sdk` 中实现访问事件采集能力。

Phase 4 已经完成业务 Redis 读请求进入 TMC 链路：

```text
业务调用 TmcJedis.get(key)
  -> TmcClient.get(key, () -> jedis.get(key))
  -> 本地缓存或远端 Redis
```

但此时服务端还不知道哪些 key 被访问，因此无法做热点探测。

本阶段要做的是：在 SDK 读路径中生成 `AccessEvent`，通过异步队列写入 rsyslog，让后续 rsyslog + Kafka + server 能消费这些访问事件。

完成后应具备以下能力：

1. `TmcClient.get(key, jedisGetter)` 生成 `AccessEvent`。
2. 访问事件包含 `appName`、`key`、`timestamp`、`weight`、`clientId`、`operation`。
3. 事件上报不阻塞业务读请求。
4. 队列满时丢弃事件，不影响业务读请求。
5. reporter 后台线程批量取出事件。
6. writer 将事件编码成 JSON line。
7. writer 将 JSON line 写入 rsyslog。
8. 上报失败时记录指标，不影响业务读请求。
9. 单元测试覆盖事件生成、队列降级、JSON line 和 writer 行为。

本阶段不实现 Kafka 消费、服务端聚合、滑动窗口探测和 etcd 热点下发。

## 2. 阶段边界

### 2.1 本阶段实现

本阶段实现：

- `AccessReporter`。
- 访问事件配置。
- 访问事件指标。
- `TmcClient.get` 中的事件生成与异步上报。
- reporter 生命周期管理。
- 单元测试。

### 2.2 本阶段不实现

本阶段不实现：

- rsyslog 到 Kafka 链路改造。
- Kafka consumer。
- server 端访问事件聚合。
- 滑动窗口热点探测。
- etcd 热点快照下发。
- 跨节点失效广播。

这些能力在后续阶段接入。

## 3. 设计原则

### 3.1 上报是旁路能力

访问事件上报只服务热点探测，不参与业务正确性。

因此 SDK 必须保证：

```text
上报成功 -> 服务端后续可以探测热点
上报失败 -> 业务 get 仍然返回真实结果
```

不能因为 rsyslog 不可用、队列满、writer 异常导致业务读失败。

### 3.2 业务线程只负责入队

`TmcClient.get(key, jedisGetter)` 所在线程是业务线程。

业务线程不应该直接写 socket，也不应该等待 rsyslog 响应。

推荐流程：

```text
TmcClient.get(key, jedisGetter)
  -> 构造 AccessEvent
  -> reporter.report(event)
  -> report 只尝试入队
  -> 继续执行读路径
```

后台 reporter 线程负责真正写 rsyslog。

### 3.3 队列必须有界

访问事件可能非常高频。

如果队列无界，rsyslog 或网络抖动时会导致 JVM 内存不断上涨。

因此队列必须有界：

```text
队列未满 -> 入队成功
队列已满 -> 丢弃事件，记录 dropped 指标
```

丢弃访问事件会影响热点探测准确性，但不会影响业务正确性。

### 3.4 使用 JSON line

rsyslog 接收的是一行一条消息。

SDK 应使用 `JsonUtils.toJsonLine(event)`：

```json
{"appName":"product-service","key":"product:10001","timestamp":1720000000000,"weight":1,"clientId":"client-1","operation":"GET"}
```

每条消息末尾带 `\n`。

## 4. 模块结构

访问事件采集属于 SDK 内部的通信模块。

本阶段在以下包内扩展通信能力：

```text
cn.ayice.tmc.communication
```

职责：

| 模块 | 包 | 职责 |
|---|---|---|
| 通信模块 | `cn.ayice.tmc.communication` | 访问事件上报、上报配置、rsyslog 写入，后续继续承载 etcd 监听和失效广播。 |

建议类：

```text
cn.ayice.tmc.communication.AccessReporter
cn.ayice.tmc.communication.AccessReportProperties
cn.ayice.tmc.communication.RsyslogProperties
```

## 5. 配置设计

### 5.1 配置示例

```yaml
tmc:
  report:
    enabled: true
    queue-capacity: 10000
    batch-size: 100
    flush-interval-millis: 100
    rsyslog:
      host: 127.0.0.1
      port: 5514
      connect-timeout-millis: 1000
      write-timeout-millis: 1000
```

### 5.2 `AccessReportProperties`

建议挂在 `TmcProperties` 下：

```java
private AccessReportProperties report = new AccessReportProperties();
```

建议字段：

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | `boolean` | `true` | 是否启用访问事件上报 |
| `queueCapacity` | `int` | `10000` | 上报队列容量 |
| `batchSize` | `int` | `100` | 单次批量写入数量 |
| `flushIntervalMillis` | `long` | `100` | 后台线程空闲等待时间 |
| `rsyslog` | `RsyslogProperties` | 默认对象 | rsyslog 写入配置 |

### 5.3 `RsyslogProperties`

建议字段：

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `host` | `String` | `127.0.0.1` | rsyslog 地址 |
| `port` | `int` | `5514` | rsyslog 端口 |
| `connectTimeoutMillis` | `int` | `1000` | 连接超时 |
| `writeTimeoutMillis` | `int` | `1000` | 写超时 |

约束：

- `queueCapacity > 0`。
- `batchSize > 0`。
- `flushIntervalMillis > 0`。
- `host` 不能为空。
- `port` 必须在合法端口范围内。

## 6. 核心组件设计

### 6.1 `AccessReporter`

访问事件上报组件。

建议方法：

```java
public class AccessReporter {
    public void report(AccessEvent event) {
        // 只做入队，不阻塞业务线程
    }

    public void close() {
        // 停止后台线程并释放连接
    }
}
```

职责：

- 接收 SDK 生成的 `AccessEvent`。
- 保证 `report` 方法不阻塞业务读路径。
- 保证异常不影响业务读路径。
- 内部维护有界队列和后台写入线程。
- 内部负责把事件写入 rsyslog。

`tmc.report.enabled=false` 时，不创建真实上报组件，`TmcClient` 侧按配置跳过访问事件上报。这里不为了禁用分支人为制造第二个实现类。

核心依赖：

```text
BlockingQueue<AccessEvent>
rsyslog socket/client
ReportMetrics
worker thread
```

`report(event)` 行为：

```text
event == null
  -> 直接忽略

queue.offer(event) == true
  -> queued + 1

queue.offer(event) == false
  -> dropped + 1
```

注意：

- 使用 `offer`，不能使用阻塞的 `put`。
- `report` 捕获自身异常，不向业务线程抛出。

worker 行为：

```text
while running
  -> 从队列 poll 事件
  -> 收集最多 batchSize 条
  -> 写入 rsyslog
  -> success + batch.size
  -> 失败则 failed + batch.size
```

### 6.2 rsyslog 写入

rsyslog 写入是 `AccessReporter` 的内部能力。

职责：

```text
List<AccessEvent>
  -> JsonUtils.toJsonLine(event)
  -> socket output stream
  -> rsyslog
```

建议先实现 TCP：

- 比 UDP 更容易确认写入语义。
- 和本地 rsyslog 容器端口 `5514` 对齐。
- 后续需要时再扩展 UDP。

连接策略：

- writer 初始化时可以不立即连接。
- 第一次写入时建立 socket。
- 写失败时关闭 socket。
- 下一次写入重新连接。

这样 rsyslog 短暂重启后 SDK 可以恢复。

## 7. 读路径接入设计

### 7.1 事件生成时机

访问事件应在 `TmcClient.get(key, jedisGetter)` 开始时生成并提交上报。

推荐位置：

```text
TmcClient.get(key, jedisGetter)
  -> totalGets + 1
  -> reportAccessEvent(key)
  -> 后续热点判断、本地缓存、回源
```

原因：

- 不论本地命中还是远端回源，业务都发生了一次 key 访问。
- 服务端要统计的是访问热度，不是 Redis 回源次数。

### 7.2 `AccessEvent` 字段

建议生成：

```java
new AccessEvent(
    properties.getAppName(),
    key,
    System.currentTimeMillis(),
    TmcConstants.ACCESS_EVENT_WEIGHT,
    properties.getClientId(),
    CacheOperation.GET
)
```

字段说明：

| 字段 | 来源 |
|---|---|
| `appName` | `TmcProperties.appName` |
| `key` | `TmcClient.get(key, jedisGetter)` 参数 |
| `timestamp` | 当前时间 |
| `weight` | `TmcConstants.ACCESS_EVENT_WEIGHT` |
| `clientId` | `TmcProperties.clientId` |
| `operation` | `CacheOperation.GET` |

### 7.3 上报异常处理

`reportAccessEvent(key)` 必须捕获异常：

```text
reporter.report(event) 成功 -> 正常继续
reporter.report(event) 异常 -> fallback/reportFailed 指标 + 正常继续
```

不能影响后续读取。

## 8. 指标设计

建议在 `TmcMetrics` 中新增访问事件相关指标：

| 指标 | 含义 |
|---|---|
| `reportQueued` | 成功入队事件数 |
| `reportDropped` | 队列满丢弃事件数 |
| `reportSucceeded` | 成功写出事件数 |
| `reportFailed` | 写出失败事件数 |
| `reportQueueSize` | 当前队列长度 |

说明：

- `reportQueueSize` 可以由 reporter 暴露，也可以暂时不放进 `TmcMetricsSnapshot`。
- Phase 5 至少要覆盖 queued、dropped、succeeded、failed。

这些指标用于后续排查：

- 读请求很多但 queued 很少：可能没有接入上报。
- dropped 很高：队列太小或 rsyslog 写入慢。
- failed 很高：rsyslog 不可用或网络异常。

## 9. 生命周期设计

`AccessReporter` 需要启动后台线程。

建议行为：

```text
构造 reporter
  -> 创建队列
  -> 创建 worker thread
  -> start

close
  -> running=false
  -> interrupt worker
  -> 尝试 flush 剩余事件
  -> writer.close
```

注意：

- worker thread 建议设置为 daemon。
- close 可以幂等。
- 测试中需要能够关闭 reporter，避免线程泄漏。

## 10. 异常与降级设计

### 10.1 队列满

队列满时：

```text
report(event)
  -> offer 返回 false
  -> reportDropped + 1
  -> 返回
```

不阻塞，不抛异常。

### 10.2 rsyslog 写入失败

rsyslog 写失败时：

```text
worker 捕获异常
  -> reportFailed + batch.size
  -> 关闭当前 socket
  -> 下一轮重新连接
```

当前批次可以丢弃。

原因：

- 访问事件不是业务正确性数据。
- 重试队列容易放大积压。
- 本项目学习阶段先保持简单。

### 10.3 reporter 内部异常

reporter 内部任何异常都不应该影响 `TmcClient.get`。

`TmcClient` 侧也要兜底：

```java
try {
    reporter.report(event);
} catch (RuntimeException ignored) {
}
```

## 11. 测试设计

建议测试类：

```text
AccessReporterTest
TmcClientAccessEventTest
```

### 11.1 `AccessReporterTest`

覆盖：

1. `report(event)` 可以成功入队。
2. 队列满时事件被丢弃。
3. rsyslog 写出成功后 success 指标增加。
4. rsyslog 写入异常后 failed 指标增加。
5. `close()` 可以停止 worker。

测试方式：

- 优先使用 fake socket/server 或可替换的测试构造参数。
- 单元测试不依赖 Docker rsyslog。

### 11.2 rsyslog 写入测试

覆盖：

1. event 会被编码成 JSON line。
2. 多个 event 会写多行。
3. 写入失败时异常向 worker 抛出。

测试方式：

- 优先用本地 `ServerSocket` 验证写入内容。
- 不依赖 Docker rsyslog。

### 11.3 `TmcClientAccessEventTest`

覆盖：

1. 每次 `get(key)` 会生成一个 `AccessEvent`。
2. 本地缓存命中也会上报访问事件。
3. reporter 异常不影响 `get(key)` 返回。
4. `tmc.report.enabled=false` 时不产生真实写入。

### 11.4 JSON line 测试

可以复用 `JsonUtilsTest`，重点确认：

- `AccessEvent` 能序列化。
- `toJsonLine` 末尾有换行。
- 去掉换行后能反序列化回 `AccessEvent`。

## 12. 开发顺序

建议按以下顺序开发：

1. 在 `TmcProperties` 中增加 `AccessReportProperties`。
2. 实现 `AccessReportProperties` 和 `RsyslogProperties`。
3. 在 `TmcMetrics` 和 `TmcMetricsSnapshot` 中增加 report 指标。
4. 创建 `AccessReporter`。
5. 在 `AccessReporter` 中实现有界队列、后台线程和 rsyslog 写入。
6. 修改 `TmcAutoConfiguration`，根据配置创建 reporter。
7. 修改 `TmcClient` 构造依赖，注入 `AccessReporter`。
8. 在 `TmcClient.get` 中生成并上报 `AccessEvent`。
9. 补 reporter、TmcClient 上报测试。
10. 运行 `mvn -pl tmc-sdk -am test`。
11. 运行 `mvn test`。

## 13. 验收清单

Phase 5 完成时逐项检查：

- [ ] `TmcProperties` 存在 report 配置。
- [ ] 存在 `AccessReporter`。
- [ ] `AccessReporter` 内部使用有界队列。
- [ ] `AccessReporter` 后台线程能写入 rsyslog。
- [ ] `TmcClient.get` 会生成 `AccessEvent`。
- [ ] 本地缓存命中也会生成访问事件。
- [ ] 上报队列有界。
- [ ] 队列满时丢弃事件且业务读请求继续完成。
- [ ] rsyslog 写失败时不影响业务读请求。
- [ ] `AccessEvent` 输出为合法 JSON line。
- [ ] report 相关指标能反映 queued、dropped、succeeded、failed。
- [ ] reporter 能关闭后台线程。
- [ ] `mvn -pl tmc-sdk -am test` 通过。
- [ ] `mvn test` 通过。

## 14. 和后续阶段的关系

Phase 5 完成后，SDK 侧已经能持续产生访问事件。

后续阶段可以继续推进：

- Phase 6：验证 rsyslog 到 Kafka 的真实链路。
- Phase 7：`tmc-server` 消费 Kafka 中的 `AccessEvent`。
- Phase 8：服务端滑动窗口热点探测。
- Phase 9：服务端通过 etcd 下发热点快照。

Phase 5 的重点不是“事件一定不能丢”，而是“事件采集不能影响业务读路径”。访问事件是热点探测的输入，允许在异常时降级丢弃，但不允许拖垮业务请求。

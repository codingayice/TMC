# TMC 复现项目总 PRD

## 1. 背景

TMC 即 Transparent Multilevel Cache，目标是在业务应用和分布式缓存之间增加一层对业务尽量透明的热点治理能力。它解决的问题不是普通缓存读写，而是当少量 key 在短时间内被高频访问时，Redis 等缓存集群会承受不成比例的请求压力，进而影响应用稳定性。

本项目用于学习和复现有赞 TMC 文章中的核心链路。项目重点是把“透明接入、访问事件采集、热点探测、本地缓存、缓存失效广播、效果验证”做成一条完整可运行链路，让开发者能够通过代码吃透多级缓存中间件的设计。

## 2. 产品定位

本项目是一个面向学习和实验的透明多级缓存系统，最终形态包含客户端 SDK、Jedis 接入层、热点探测服务端、消息采集链路、etcd 推送链路、演示应用和压测工具。

系统以 Redis 作为远端缓存存储，以 Caffeine 作为应用进程内本地缓存，以 rsyslog + Kafka 承载访问事件采集，以 etcd 承载热点 key 下发和失效事件广播。

## 3. 核心目标

实现一套可本地运行、可调试、可压测的 TMC 原型，覆盖以下完整链路：

1. 业务应用通过 Jedis 风格客户端读取 Redis。
2. Jedis 接入层拦截读写操作，并将读请求交给客户端 SDK 判断。
3. SDK 判断 key 是否为热点 key。
4. 热点 key 优先读取本地 Caffeine 缓存。
5. 非热点 key 或本地缓存未命中时访问 Redis。
6. SDK 将访问事件写入本地日志或本地 rsyslog 入口。
7. rsyslog 将访问事件转发到 Kafka。
8. tmc-server 消费 Kafka 中的访问事件。
9. tmc-server 按 appName 和 key 聚合访问热度。
10. tmc-server 每 3 秒执行一次滑动窗口热点探测。
11. tmc-server 将热点 key 快照写入 etcd。
12. SDK 通过 etcd watch 接收热点 key 快照。
13. SDK 更新本地热点表，并对热点 key 启用本地缓存。
14. 业务调用 set、del、expire 等写操作后，SDK 同步失效当前节点本地缓存。
15. SDK 将失效事件写入 etcd。
16. 其他客户端节点通过 etcd watch 接收失效事件，并删除本地缓存。
17. demo 和 benchmark 展示 Redis 请求下降、本地命中率上升、热点识别延迟和缓存一致性效果。

## 4. 学习目标

通过开发本项目，掌握以下内容：

- 透明多级缓存的整体架构设计。
- Jedis 客户端接入层如何拦截读写路径。
- 热点 key 探测算法：事件采集、计数聚合、时间片、滑动窗口、TopN。
- 本地缓存的读路径、写路径和失效路径。
- rsyslog + Kafka 在异步事件采集中的作用。
- etcd watch/prefix 在配置推送和事件广播中的作用。
- 客户端 SDK 如何做线程隔离、队列缓冲、异常降级和指标统计。
- 服务端如何做无状态消费、周期性探测和热点结果发布。
- 如何通过 demo 和 benchmark 验证中间件效果。

## 5. 系统架构

### 5.1 架构分层

系统分为四层：

1. 应用层：业务 demo、Jedis 接入层、TMC SDK、本地缓存。
2. 采集层：本地访问事件日志、rsyslog、Kafka。
3. 探测层：tmc-server、访问事件消费、滑动窗口、热点探测。
4. 协调层：etcd 热点 key 下发、etcd 失效事件广播。

### 5.2 核心数据流

读请求数据流：

```text
业务代码
  -> TmcJedis.get(key)
  -> TmcClient.get(key, redisLoader)
  -> HotKeyManager 判断热点
  -> LocalCacheManager 查询本地缓存
  -> Redis 兜底读取
  -> AccessEvent 写入采集链路
```

热点探测数据流：

```text
AccessEvent
  -> 本地日志/rsyslog
  -> Kafka topic
  -> tmc-server consumer
  -> AccessEventCollector
  -> TimeWheel
  -> SlidingWindowHotKeyDetector
  -> HotKeySnapshot
  -> etcd
  -> SDK watch
```

失效广播数据流：

```text
TmcJedis.set/del/expire
  -> Redis 写操作
  -> 当前节点 LocalCache invalidate
  -> InvalidationEvent 写入 etcd
  -> 其他 SDK 节点 watch
  -> 其他节点 LocalCache invalidate
```

## 6. 模块规划

### 6.1 `tmc-common`

公共模型和工具模块。

职责：

- 定义访问事件、失效事件、热点 key、热点快照、配置项等共享模型。
- 提供统一 JSON 序列化工具。
- 提供常量、key 命名规则、时间工具和异常类型。
- 提供事件协议版本号，便于采集链路演进。

建议包结构：

```text
cn.ayice.tmc.common.model
cn.ayice.tmc.common.protocol
cn.ayice.tmc.common.config
cn.ayice.tmc.common.constant
cn.ayice.tmc.common.util
```

核心对象：

- `AccessEvent`
- `InvalidationEvent`
- `HotKey`
- `HotKeySnapshot`
- `TmcClientConfig`
- `TmcServerConfig`
- `EventProtocol`
- `EtcdKeys`

### 6.2 `tmc-sdk`

客户端核心 SDK。

职责：

- 判断 key 是否为热点 key。
- 对热点 key 读取本地缓存。
- 非热点或本地未命中时回调真实 Redis 读取。
- 将访问事件异步写入本地采集通道。
- 接收 etcd 下发的热点 key 快照。
- 维护热点 key 表和本地缓存生命周期。
- 发布和接收 key 失效事件。
- 统计客户端命中率、上报量、失效率和降级次数。

建议包结构：

```text
cn.ayice.tmc.sdk.core
cn.ayice.tmc.sdk.cache
cn.ayice.tmc.sdk.hotkey
cn.ayice.tmc.sdk.report
cn.ayice.tmc.sdk.etcd
cn.ayice.tmc.sdk.invalidate
cn.ayice.tmc.sdk.metrics
```

核心对象：

- `TmcClient`
- `HotKeyManager`
- `LocalCacheManager`
- `AccessEventReporter`
- `RsyslogAccessEventReporter`
- `EtcdHotKeySubscriber`
- `EtcdInvalidationPublisher`
- `EtcdInvalidationSubscriber`
- `TmcMetrics`

### 6.3 `tmc-jedis`

Jedis 透明接入模块。

职责：

- 包装 Jedis 常用读写接口。
- 对 `get`、`mget` 等读操作接入 TMC 读路径。
- 对 `set`、`del`、`expire` 等变更操作触发本地缓存失效。
- 提供 `TmcJedisPool`，对外保留接近 Jedis 的使用方式。
- 提供 Spring Boot 自动配置，降低业务接入成本。

建议包结构：

```text
cn.ayice.tmc.jedis
cn.ayice.tmc.jedis.operation
cn.ayice.tmc.jedis.autoconfigure
```

核心对象：

- `TmcJedis`
- `TmcJedisPool`
- `TmcJedisFactory`
- `TmcJedisProperties`
- `TmcJedisAutoConfiguration`

### 6.4 `tmc-server`

热点探测服务端。

职责：

- 消费 Kafka 中的访问事件。
- 按 appName 和 key 聚合访问热度。
- 每 3 秒执行一次映射和探测任务。
- 使用 10 个时间片维护 30 秒滑动窗口。
- 根据阈值和 TopN 生成热点 key 列表。
- 将热点 key 快照写入 etcd。
- 暴露查询接口，展示当前热点 key、事件消费状态和探测指标。

建议包结构：

```text
cn.ayice.tmc.server
cn.ayice.tmc.server.consumer
cn.ayice.tmc.server.collect
cn.ayice.tmc.server.window
cn.ayice.tmc.server.detect
cn.ayice.tmc.server.publish
cn.ayice.tmc.server.api
cn.ayice.tmc.server.metrics
cn.ayice.tmc.server.config
```

核心对象：

- `TmcServerApplication`
- `KafkaAccessEventConsumer`
- `AccessEventCollector`
- `TimeWheel`
- `TimeBucket`
- `SlidingWindowHotKeyDetector`
- `HotKeyPublisher`
- `HotKeyQueryController`
- `ServerMetrics`

### 6.5 `tmc-demo`

演示应用。

职责：

- 模拟业务系统读取商品详情、活动详情等缓存数据。
- 提供直连 Redis 与 TMC 两种访问路径。
- 提供写入、删除、过期接口验证失效逻辑。
- 提供热点访问模拟接口。
- 展示本地缓存命中次数、Redis 访问次数、热点 key 列表。
- 支持启动多个 demo 实例验证失效广播。

建议接口：

- `GET /demo/direct/{key}`
- `GET /demo/tmc/{key}`
- `POST /demo/value/{key}`
- `DELETE /demo/value/{key}`
- `POST /demo/expire/{key}`
- `POST /demo/hotspot/run`
- `GET /demo/metrics`
- `GET /demo/hotkeys`

### 6.6 `tmc-benchmark`

压测和效果验证模块。

职责：

- 构造热点 key 和普通 key 混合访问。
- 对比直连 Redis 和 TMC 访问效果。
- 输出请求量、平均 RT、P95、P99、本地命中率、Redis 请求下降比例。
- 验证热点探测延迟、失效传播延迟和降级表现。

建议压测场景：

- 单热点 key：一个 key 承担 80% 请求。
- 多热点 key：10 个 key 承担 80% 请求。
- 均匀访问：没有明显热点。
- 写入干扰：热点 key 被周期性更新或删除。
- 多实例访问：两个以上 demo 实例共同访问热点 key。
- 采集链路抖动：验证 SDK 队列、rsyslog、Kafka 的稳定性。

## 7. 事件协议

### 7.1 AccessEvent

字段：

- `protocolVersion`：协议版本。
- `appName`：业务应用名。
- `key`：访问的缓存 key。
- `timestamp`：事件发生时间。
- `weight`：访问权重，默认 1。
- `clientId`：客户端实例标识。
- `operation`：访问操作，初始值为 `GET`。

示例：

```json
{
  "protocolVersion": "1.0",
  "appName": "tmc-demo",
  "key": "product:10001",
  "timestamp": 1720000000000,
  "weight": 1,
  "clientId": "demo-1",
  "operation": "GET"
}
```

### 7.2 HotKey

字段：

- `appName`
- `key`
- `score`
- `detectedAt`
- `ttlMillis`

### 7.3 HotKeySnapshot

字段：

- `appName`
- `version`
- `hotKeys`
- `windowSeconds`
- `publishedAt`

### 7.4 InvalidationEvent

字段：

- `appName`
- `key`
- `operation`
- `clientId`
- `timestamp`

## 8. 配置项设计

### 8.1 客户端配置

建议前缀：`tmc.client`

配置项：

- `enabled`：是否启用 TMC。
- `app-name`：应用名。
- `client-id`：客户端实例 ID。
- `etcd-endpoints`：etcd 地址。
- `local-cache-max-size`：本地缓存最大条目数。
- `local-cache-ttl`：本地缓存 TTL。
- `report-queue-size`：访问事件队列大小。
- `report-batch-size`：访问事件批量写入大小。
- `report-interval`：访问事件写入间隔。
- `rsyslog-host`：rsyslog 地址。
- `rsyslog-port`：rsyslog 端口。
- `degrade-on-error`：异常时是否自动降级。

### 8.2 服务端配置

建议前缀：`tmc.server`

配置项：

- `kafka-bootstrap-servers`：Kafka 地址。
- `kafka-topic`：访问事件 topic。
- `kafka-consumer-group`：消费者组。
- `detect-interval`：探测周期，默认 3 秒。
- `window-bucket-count`：时间片数量，默认 10。
- `hot-key-threshold`：热点阈值。
- `top-n`：每个 app 下发热点 key 数量。
- `etcd-endpoints`：etcd 地址。
- `event-expire-after`：事件计数过期时间。

### 8.3 基础设施配置

需要提供本地开发环境配置：

- Redis。
- etcd。
- Kafka。
- ZooKeeper 或 KRaft Kafka。
- rsyslog。
- tmc-server。
- tmc-demo 多实例。

## 9. 核心流程

### 9.1 读流程

1. 业务调用 `TmcJedis.get(key)`。
2. `TmcJedis` 将 appName、key、Redis 回调传给 `TmcClient`。
3. `TmcClient` 创建 `AccessEvent` 并放入本地有界队列。
4. `AccessEventReporter` 异步将事件写入 rsyslog。
5. `HotKeyManager` 判断 key 是否是热点 key。
6. 非热点 key 直接执行 Redis 回调。
7. 热点 key 查询 `LocalCacheManager`。
8. 本地命中时直接返回本地 value。
9. 本地未命中时执行 Redis 回调，拿到 value 后写入本地缓存。
10. 返回 value 给业务。

### 9.2 访问事件采集流程

1. SDK 将 `AccessEvent` 写入本地队列。
2. reporter 线程批量取出事件。
3. reporter 将事件编码成 JSON line。
4. reporter 写入本地 rsyslog。
5. rsyslog 根据规则转发到 Kafka topic。
6. tmc-server 消费 Kafka topic。
7. tmc-server 反序列化事件并写入 `AccessEventCollector`。

### 9.3 热点探测流程

1. `AccessEventCollector` 将事件聚合为 `Map<appName, Map<key, LongAdder>>`。
2. `tmc-server` 每 3 秒触发一次探测任务。
3. 探测任务读取当前 3 秒内的 key 访问计数。
4. 每个 key 的访问计数写入当前时间片。
5. `TimeWheel` 汇总最近 10 个时间片，得到最近 30 秒热度。
6. `SlidingWindowHotKeyDetector` 按热度排序。
7. 过滤低于阈值的 key。
8. 取 TopN 生成 `HotKeySnapshot`。
9. `HotKeyPublisher` 将热点快照写入 etcd。
10. SDK 通过 etcd watch 收到热点快照并更新本地热点表。

### 9.4 写入和失效流程

1. 业务调用 `TmcJedis.set/del/expire(key)`。
2. `TmcJedis` 执行真实 Redis 写操作。
3. 写操作成功后调用 `TmcClient.invalidate(key)`。
4. 当前节点同步删除本地缓存中的 key。
5. 当前节点向 etcd 发布 `InvalidationEvent`。
6. 其他 SDK 节点通过 etcd watch 收到事件。
7. 其他节点判断事件来源，忽略自己发出的事件。
8. 其他节点删除本地缓存中的 key。

## 10. 一致性策略

本项目采用“当前节点强一致 + 集群最终一致”：

- 当前节点执行写操作成功后，必须同步删除当前 JVM 本地缓存。
- 其他节点通过 etcd 异步收到失效事件后删除本地缓存。
- 热点 key 本地缓存设置 TTL，作为广播延迟或事件丢失时的兜底。
- 读请求本地未命中时始终回源 Redis。
- 写操作以 Redis 成功为准，Redis 写成功后再触发本地和集群失效。

## 11. 稳定性策略

SDK 必须保证 TMC 故障不影响业务基本读写：

- 访问事件上报使用独立线程池。
- 上报队列必须有界。
- 队列满时丢弃访问事件并记录指标。
- rsyslog 写入失败时记录失败次数并继续业务读写。
- Kafka 或 server 异常时不阻塞业务请求。
- etcd watch 异常时自动重连。
- 本地缓存异常时直接访问 Redis。
- server 探测任务异常时保留上一轮热点快照，下一周期继续执行。

## 12. 指标与观测

### 12.1 客户端指标

- 总 get 次数。
- Redis get 次数。
- 本地缓存命中次数。
- 本地缓存未命中次数。
- 当前热点 key 数量。
- 访问事件入队次数。
- 访问事件丢弃次数。
- rsyslog 写入成功次数。
- rsyslog 写入失败次数。
- 失效事件发布次数。
- 失效事件接收次数。
- etcd watch 重连次数。

### 12.2 服务端指标

- Kafka 消费事件数。
- Kafka 消费失败数。
- 每个 app 的 key 数量。
- 每个 app 当前热点 key 数量。
- 探测任务执行次数。
- 探测任务耗时。
- 热点快照发布次数。
- etcd 发布成功次数。
- etcd 发布失败次数。

### 12.3 demo 指标

- direct Redis 请求量。
- TMC 模式请求量。
- 本地缓存命中率。
- Redis 请求下降比例。
- 平均 RT。
- P95 RT。
- P99 RT。
- 热点识别耗时。
- 失效传播耗时。

## 13. 开发计划

### Phase 1：工程基础与本地环境

目标：建立完整项目脚手架和本地运行环境。

任务：

- 补 README 和项目运行说明。
- 补 `.gitignore`。
- 补 Docker Compose，包含 Redis、etcd、Kafka、rsyslog。
- 确认父工程和所有模块可以编译。
- 统一 Java 版本、Maven 插件、测试依赖和基础日志配置。

验收：

- 一条命令启动基础设施。
- 父工程编译通过。
- 每个模块具备标准 `src/main` 和 `src/test` 目录。

### Phase 2：公共模型与协议

目标：定义系统内部统一数据结构和协议格式。

任务：

- 在 `tmc-common` 定义 `AccessEvent`。
- 在 `tmc-common` 定义 `HotKey` 和 `HotKeySnapshot`。
- 在 `tmc-common` 定义 `InvalidationEvent`。
- 定义 etcd key 命名规则。
- 定义事件 JSON 序列化和反序列化工具。
- 为模型和协议增加单元测试。

验收：

- 所有事件对象可稳定序列化和反序列化。
- 协议字段和示例 JSON 对齐。
- etcd key 生成规则有测试覆盖。

### Phase 3：SDK 本地缓存核心

目标：实现客户端本地热点缓存能力。

任务：

- 实现 `TmcClient`。
- 实现 `HotKeyManager`。
- 实现 `LocalCacheManager`。
- 实现 Redis 回调读取机制。
- 实现客户端指标对象。
- 支持手动注入热点 key 进行单元测试。

验收：

- 热点 key 可读取本地缓存。
- 本地未命中时可回源 Redis 回调。
- 非热点 key 直接回源 Redis。
- 本地缓存容量和 TTL 生效。

### Phase 4：Jedis 接入层

目标：让业务通过 Jedis 风格接口使用 TMC。

任务：

- 实现 `TmcJedis.get`。
- 实现 `TmcJedis.mget`。
- 实现 `TmcJedis.set`。
- 实现 `TmcJedis.del`。
- 实现 `TmcJedis.expire`。
- 实现 `TmcJedisPool`。
- 提供 Spring Boot 自动配置。

验收：

- demo 可通过 `TmcJedis` 完成 Redis 读写。
- 读操作进入 SDK 读路径。
- 写操作触发 SDK 失效路径。
- 接入层接口风格接近原生 Jedis。

### Phase 5：SDK 访问事件采集

目标：SDK 将访问事件稳定写入 rsyslog。

任务：

- 实现访问事件有界队列。
- 实现 reporter 独立线程池。
- 实现 JSON line 编码。
- 实现 `RsyslogAccessEventReporter`。
- 增加上报成功、失败、丢弃指标。
- 增加 rsyslog 本地配置示例。

验收：

- 每次 `get` 都能生成 `AccessEvent`。
- 高频访问时业务线程不被上报阻塞。
- rsyslog 能收到 SDK 写入的事件。
- 队列满时事件被丢弃且业务请求继续完成。

### Phase 6：rsyslog 到 Kafka 链路

目标：打通访问事件采集管道。

任务：

- 配置 rsyslog 输入。
- 配置 rsyslog Kafka 输出。
- 创建 Kafka topic。
- 定义 topic 命名规则。
- 提供本地验证脚本。
- 增加采集链路排障文档。

验收：

- SDK 写入 rsyslog 后，Kafka topic 能收到事件。
- Kafka 中的消息为合法 `AccessEvent` JSON。
- 高频写入时消息顺序和吞吐满足本地压测要求。

### Phase 7：服务端 Kafka 消费与聚合

目标：tmc-server 能消费访问事件并按 app/key 聚合计数。

任务：

- 增加 Spring Boot 启动类。
- 引入 Kafka consumer。
- 实现 `KafkaAccessEventConsumer`。
- 实现 `AccessEventCollector`。
- 实现内存计数结构 `Map<appName, Map<key, LongAdder>>`。
- 暴露事件消费和计数查询接口。

验收：

- tmc-server 能消费 Kafka 中的访问事件。
- 同一个 app/key 的访问次数可累加。
- 查询接口能看到当前计数。
- 消费失败有错误指标和日志。

### Phase 8：滑动窗口与热点探测

目标：服务端能够周期性识别热点 key。

任务：

- 实现 `TimeBucket`。
- 实现 `TimeWheel`。
- 实现 10 个时间片、每片 3 秒的窗口。
- 实现周期性探测任务。
- 实现 TopN 排序。
- 实现热点阈值过滤。
- 暴露当前热点 key 查询接口。

验收：

- 单热点场景可在一个探测周期内进入热点列表。
- 多热点场景可按热度排序。
- 均匀访问场景不会产生大量误判热点。
- 探测任务耗时可观测。

### Phase 9：etcd 热点下发

目标：服务端将热点探测结果推送给客户端。

任务：

- 定义热点快照 etcd 路径。
- 实现 `HotKeyPublisher`。
- tmc-server 将 `HotKeySnapshot` 写入 etcd。
- SDK 实现 `EtcdHotKeySubscriber`。
- SDK watch appName 对应热点快照。
- SDK 收到快照后更新 `HotKeyManager`。

验收：

- tmc-server 探测到热点后写入 etcd。
- SDK 能自动接收热点 key。
- 客户端无需手动配置即可启用热点本地缓存。
- Redis 请求量在热点生效后下降。

### Phase 10：失效事件广播

目标：多个客户端节点之间完成热点本地缓存失效。

任务：

- 定义失效事件 etcd 路径。
- SDK 实现 `EtcdInvalidationPublisher`。
- SDK 实现 `EtcdInvalidationSubscriber`。
- `TmcJedis.set/del/expire` 成功后发布失效事件。
- subscriber 忽略当前 clientId 自己发布的事件。
- demo 支持多实例验证。

验收：

- 节点 A 更新热点 key 后，节点 A 本地缓存立即失效。
- 节点 B 收到 etcd 失效事件后删除本地缓存。
- 多实例场景下不会长期读到旧值。
- 失效传播耗时可观测。

### Phase 11：demo 应用

目标：提供完整可操作的业务演示。

任务：

- 实现 demo Spring Boot 启动类。
- 实现 Redis 初始化数据接口。
- 实现 direct Redis 读取接口。
- 实现 TMC 读取接口。
- 实现 set、del、expire 接口。
- 实现热点访问模拟接口。
- 实现 metrics 和 hotkeys 查询接口。

验收：

- 可以通过 HTTP 接口制造热点访问。
- 可以观察热点 key 被自动识别。
- 可以观察本地缓存命中率上升。
- 可以启动两个 demo 实例验证失效广播。

### Phase 12：benchmark 压测

目标：量化 TMC 的效果。

任务：

- 实现直连 Redis 压测模式。
- 实现 TMC 压测模式。
- 实现单热点、多热点、均匀访问、写入干扰、多实例访问场景。
- 输出 QPS、平均 RT、P95、P99。
- 输出 Redis 请求下降比例。
- 输出本地缓存命中率。
- 输出热点识别耗时和失效传播耗时。

验收：

- 热点场景下 TMC 模式 Redis get 次数明显下降。
- 热点场景下本地缓存命中率明显上升。
- 写入干扰场景下缓存失效符合一致性策略。
- 压测报告可以复现实验结果。

### Phase 13：观测与管理接口

目标：让系统运行状态可查询、可定位。

任务：

- tmc-server 暴露热点列表查询接口。
- tmc-server 暴露 Kafka 消费状态。
- tmc-server 暴露探测任务指标。
- SDK 暴露客户端指标。
- demo 汇总展示 direct 与 TMC 对比指标。
- 接入 Spring Boot Actuator。

验收：

- 可以查询当前每个 app 的热点 key。
- 可以查询 Kafka 消费量和失败量。
- 可以查询客户端本地缓存命中率。
- 可以定位热点未生效的链路位置。

### Phase 14：稳定性与异常场景

目标：验证中间件链路在异常下不影响业务基本读写。

任务：

- 验证 rsyslog 不可用。
- 验证 Kafka 不可用。
- 验证 tmc-server 不可用。
- 验证 etcd 短暂不可用。
- 验证本地缓存异常。
- 验证上报队列满。
- 验证热点快照过期。

验收：

- 异常场景下业务 Redis 读写继续可用。
- SDK 不阻塞业务线程。
- 异常恢复后热点探测和下发链路继续工作。
- 所有异常都有指标和日志。

### Phase 15：文档与学习材料

目标：形成可持续学习和复盘的资料。

任务：

- 编写工程骨架设计文档。
- 编写核心流程时序图。
- 编写热点探测算法设计文档。
- 编写 SDK 设计文档。
- 编写 rsyslog + Kafka 链路文档。
- 编写 etcd 推送与广播设计文档。
- 编写 demo 使用文档。
- 编写 benchmark 实验报告模板。

验收：

- 新开发者可以按文档启动项目。
- 新开发者可以按文档理解每个模块职责。
- 每个核心流程都有对应设计说明和验证方式。

## 14. 最终验收标准

项目完成时，应满足：

- 可以通过文档启动 Redis、etcd、Kafka、rsyslog、tmc-server、tmc-demo。
- 业务 demo 可以通过 `TmcJedis` 读写 Redis。
- SDK 可以将访问事件写入 rsyslog。
- rsyslog 可以将访问事件转发到 Kafka。
- tmc-server 可以消费 Kafka 并聚合访问热度。
- tmc-server 可以基于滑动窗口识别热点 key。
- tmc-server 可以通过 etcd 下发热点 key 快照。
- SDK 可以自动接收热点 key 并启用本地缓存。
- 修改、删除或过期热点 key 后，本地缓存能同步失效。
- 至少两个 demo 实例之间能通过 etcd 广播完成最终一致。
- benchmark 能输出直连 Redis 和 TMC 的对比结果。
- 核心算法、核心 SDK、核心服务端流程有单元测试。
- 采集链路、热点下发链路、失效广播链路有集成测试或本地验证脚本。

## 15. 风险与取舍

### 15.1 热点误判

风险：短时抖动可能导致某些 key 被误判为热点。

策略：使用阈值、TopN、热点 TTL 和滑动窗口共同控制影响范围。

### 15.2 本地缓存脏读

风险：其他节点写入后，当前节点在广播到达前可能读到旧值。

策略：当前节点同步失效，集群节点通过 etcd 最终一致，TTL 兜底。

### 15.3 采集链路抖动

风险：rsyslog、Kafka 或 server 异常导致访问事件延迟或丢失。

策略：访问事件只用于热点探测，不进入业务正确性路径；SDK 使用有界队列和异步写入，业务请求不等待采集链路。

### 15.4 探测结果滞后

风险：热点 key 从出现到下发存在探测周期和推送延迟。

策略：探测周期默认 3 秒，窗口默认 30 秒；通过 benchmark 记录热点识别耗时。

### 15.5 本地内存占用

风险：热点 key 本地缓存过多导致 JVM 内存压力。

策略：Caffeine 设置最大容量、TTL 和统计指标，热点列表由 TopN 控制。

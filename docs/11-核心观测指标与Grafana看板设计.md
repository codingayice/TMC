# Phase 10：核心观测指标与 Grafana 看板设计

## 1. 阶段目标

Phase 10 的目标不是把所有内部计数都展示出来，而是建立一套能评估 TMC 是否真正生效的核心观测体系。

本项目最终要能回答这些问题：

```text
1. 热点读请求是否被本地缓存承接？
2. Redis 回源压力是否下降？
3. 一个热点 key 从高频访问到本地缓存生效需要多久？
4. 写操作后，其他节点本地缓存是否及时失效？
5. 上报、监听、发布这些旁路能力异常时，是否没有影响业务读写？
```

本阶段完成后应具备以下能力：

1. `tmc-server` 通过 `/actuator/prometheus` 暴露服务端核心指标。
2. 接入 SDK 的业务应用通过 `/actuator/prometheus` 暴露客户端核心指标。
3. Prometheus 可以采集 `tmc_server_*` 和 `tmc_client_*` 指标。
4. Grafana 可以展示读链路、热点链路、写失效链路的核心面板。
5. 指标设计避免把 Redis key、eventId 等高基数字段放入 Prometheus 标签。
6. 当前热点 key 明细通过 REST API 或 etcd 查询展示，不塞进 Prometheus label。

## 2. 观测原则

本项目的观测指标分为两类：

```text
核心效果指标：
  用来证明 TMC 对业务链路有价值，应该放在 Grafana 主看板最显眼位置。

排障辅助指标：
  用来定位链路问题，可以放在次级面板或告警中，不作为简历项目的主要结果。
```

核心判断标准：

```text
热点被识别后，Redis 回源比例是否下降；
热点被下发后，本地缓存命中率是否上升；
写操作发生后，其他节点本地缓存是否失效；
旁路链路失败时，业务读写是否仍然可用。
```

不建议把以下数据作为 Prometheus 标签：

```text
Redis key
HotKeySnapshot.version
InvalidationEvent.eventId
完整 JSON 事件
用户 ID、商品 ID、订单 ID 等业务高基数字段
```

原因：

```text
这些字段基数不可控，会导致 Prometheus 时间序列膨胀，影响存储、查询和 Grafana 渲染。
```

## 3. 一级核心指标

### 3.1 Redis 回源比例

这是评估 TMC 价值的第一核心指标。

它回答：

```text
多级缓存有没有减少 Redis 压力？
```

依赖指标：

```text
tmc_client_total_gets_total
tmc_client_redis_gets_total
```

计算公式：

```text
redis_get_ratio = redis_gets / total_gets
```

PromQL：

```promql
rate(tmc_client_redis_gets_total[1m])
/
rate(tmc_client_total_gets_total[1m])
```

预期现象：

```text
热点识别前：Redis 回源比例接近 100%
热点识别后：Redis 回源比例明显下降
```

简历可沉淀表达：

```text
热点读场景下 Redis 回源比例从 xx% 降至 xx%。
```

### 3.2 本地缓存命中率

它回答：

```text
热点 key 是否真的被本地缓存承接？
```

依赖指标：

```text
tmc_client_local_cache_hits_total
tmc_client_local_cache_misses_total
```

计算公式：

```text
local_cache_hit_rate =
local_cache_hits / (local_cache_hits + local_cache_misses)
```

PromQL：

```promql
rate(tmc_client_local_cache_hits_total[1m])
/
(
  rate(tmc_client_local_cache_hits_total[1m])
  +
  rate(tmc_client_local_cache_misses_total[1m])
)
```

说明：

```text
这个命中率只统计热点 key 的本地缓存命中情况。
普通 key 本来就不应该进入本地缓存，不应纳入该分母。
```

预期现象：

```text
热点快照应用前：本地缓存命中率接近 0
热点快照应用后：本地缓存命中率持续上升
```

### 3.3 热点发现到生效延迟

它回答：

```text
一个 key 从高频访问，到 SDK 开始命中本地缓存，需要多久？
```

该指标可以先用 Grafana 曲线估算，后续再精确打点。

依赖指标：

```text
tmc_server_hot_keys_detected
tmc_server_hot_key_publish_succeeded_total
tmc_client_hot_key_snapshot_applied_total
tmc_client_local_cache_hits_total
tmc_client_redis_gets_total
```

观测链路：

```text
压测开始
  -> tmc_server_hot_keys_detected > 0
  -> tmc_server_hot_key_publish_succeeded_total 增长
  -> tmc_client_hot_key_snapshot_applied_total 增长
  -> tmc_client_local_cache_hits_total 开始增长
  -> tmc_client_redis_gets_total 增速下降
```

初期 Grafana 面板展示：

```text
同一张图叠加：
  当前热点 key 数
  SDK 热点快照应用速率
  本地缓存命中 QPS
  Redis 回源 QPS
```

后续如需精确统计，可以补充：

```text
tmc_server_last_hot_key_detected_timestamp
tmc_client_last_hot_key_snapshot_applied_timestamp
tmc_client_last_local_cache_hit_timestamp
```

但本阶段不强制引入 timestamp 指标，避免过早复杂化。

### 3.4 写后失效成功率

它回答：

```text
写操作之后，其他节点的本地缓存有没有及时失效？
```

依赖指标：

```text
tmc_client_local_invalidations_total
tmc_client_invalidation_report_succeeded_total
tmc_client_invalidation_report_failed_total
tmc_client_invalidation_received_total
tmc_client_redis_gets_total
tmc_client_local_cache_misses_total
```

双节点验证场景：

```text
demo-a 写热点 key：
  demo-a local_invalidations +1
  demo-a invalidation_report_succeeded +1

demo-b 收到失效广播：
  demo-b invalidation_received +1
  demo-b 下一次读 local_cache_misses +1
  demo-b 下一次读 redis_gets +1
  demo-b 后续再读 local_cache_hits 增长
```

发布成功率 PromQL：

```promql
rate(tmc_client_invalidation_report_succeeded_total[1m])
/
(
  rate(tmc_client_invalidation_report_succeeded_total[1m])
  +
  rate(tmc_client_invalidation_report_failed_total[1m])
)
```

跨节点接收速率 PromQL：

```promql
rate(tmc_client_invalidation_received_total[1m])
```

说明：

```text
写后失效成功率不能只看本节点 local_invalidations。
必须结合其他节点 invalidation_received 和下一次 Redis 回源行为一起判断。
```

## 4. 辅助指标

辅助指标不直接证明缓存效果，但可以解释核心指标为什么变化。

### 4.1 热点发现链路

```text
tmc_server_messages_consumed_total
tmc_server_access_events_accumulated_total
tmc_server_mapping_runs_total
tmc_server_mapping_failed_total
tmc_server_hot_keys_detected
tmc_server_hot_key_publish_succeeded_total
tmc_server_hot_key_publish_failed_total
tmc_client_hot_key_snapshot_applied_total
tmc_client_hot_key_snapshot_invalid_total
```

用途：

```text
判断 SDK 上报是否进入服务端；
判断滑动窗口是否持续执行；
判断热点快照是否成功发布并被 SDK 应用。
```

### 4.2 写失效链路

```text
tmc_client_local_invalidations_total
tmc_client_invalidation_report_succeeded_total
tmc_client_invalidation_report_failed_total
tmc_client_invalidation_received_total
tmc_client_invalidation_self_ignored_total
tmc_client_invalidation_invalid_total
tmc_client_invalidation_watch_failed_total
```

用途：

```text
判断写操作是否触发本地删除；
判断失效事件是否成功发布；
判断其他节点是否收到广播；
判断监听是否稳定。
```

### 4.3 旁路异常链路

```text
tmc_client_fallback_gets_total
tmc_client_report_dropped_total
tmc_client_report_failed_total
tmc_client_hot_key_watch_failed_total
tmc_client_invalidation_watch_failed_total
tmc_server_messages_invalid_total
tmc_server_messages_failed_total
tmc_server_mapping_failed_total
tmc_server_hot_key_publish_failed_total
```

用途：

```text
证明上报、监听、发布失败时，业务读写仍然有降级路径。
```

## 5. 指标命名设计

### 5.1 SDK 指标

| 指标名 | 类型 | 来源 | 含义 |
|---|---|---|---|
| `tmc_client_total_gets_total` | Counter | `TmcMetrics.totalGets` | SDK get 总次数 |
| `tmc_client_hot_key_gets_total` | Counter | `TmcMetrics.hotKeyGets` | 热点 key get 次数 |
| `tmc_client_local_cache_hits_total` | Counter | `TmcMetrics.localCacheHits` | 本地缓存命中次数 |
| `tmc_client_local_cache_misses_total` | Counter | `TmcMetrics.localCacheMisses` | 热点 key 本地缓存未命中次数 |
| `tmc_client_redis_gets_total` | Counter | `TmcMetrics.redisGets` | 真实回源 Redis 次数 |
| `tmc_client_fallback_gets_total` | Counter | `TmcMetrics.fallbackGets` | 旁路异常后降级 Redis 次数 |
| `tmc_client_hot_key_snapshot_applied_total` | Counter | `TmcMetrics.hotKeySnapshotApplied` | SDK 应用热点快照次数 |
| `tmc_client_local_invalidations_total` | Counter | `TmcMetrics.localInvalidations` | 当前节点主动删除本地缓存次数 |
| `tmc_client_invalidation_report_succeeded_total` | Counter | `TmcMetrics.invalidationReportSucceeded` | 失效事件发布成功次数 |
| `tmc_client_invalidation_report_failed_total` | Counter | `TmcMetrics.invalidationReportFailed` | 失效事件发布失败次数 |
| `tmc_client_invalidation_received_total` | Counter | `TmcMetrics.invalidationReceived` | 其他节点失效事件处理次数 |
| `tmc_client_invalidation_self_ignored_total` | Counter | `TmcMetrics.invalidationSelfIgnored` | 自身失效事件忽略次数 |

### 5.2 Server 指标

| 指标名 | 类型 | 来源 | 含义 |
|---|---|---|---|
| `tmc_server_messages_consumed_total` | Counter | `TmcServerMetrics.messagesConsumed` | Kafka 消费消息总数 |
| `tmc_server_access_events_accumulated_total` | Counter | `TmcServerMetrics.accessEventsAccumulated` | 成功进入热度累加器的事件数 |
| `tmc_server_mapping_runs_total` | Counter | `TmcServerMetrics.mappingRuns` | 滑窗映射任务执行次数 |
| `tmc_server_mapping_failed_total` | Counter | `TmcServerMetrics.mappingFailed` | 滑窗映射任务失败次数 |
| `tmc_server_tracked_apps` | Gauge | `TmcServerMetrics.trackedApps` | 当前追踪 app 数 |
| `tmc_server_tracked_keys` | Gauge | `TmcServerMetrics.trackedKeys` | 当前追踪 key 数 |
| `tmc_server_hot_keys_detected` | Gauge | `TmcServerMetrics.hotKeysDetected` | 最近一次识别出的热点 key 数 |
| `tmc_server_hot_key_publish_succeeded_total` | Counter | `TmcServerMetrics.hotKeyPublishSucceeded` | 热点快照发布成功次数 |
| `tmc_server_hot_key_publish_failed_total` | Counter | `TmcServerMetrics.hotKeyPublishFailed` | 热点快照发布失败次数 |

## 6. 标签设计

SDK 指标建议标签：

```text
app_name
client_id
```

示例：

```text
tmc_client_local_cache_hits_total{app_name="product-service",client_id="demo-a"}
```

Server 指标建议标签：

```text
instance
job
```

这些由 Prometheus scrape 自动生成即可。

不建议增加：

```text
key
event_id
snapshot_version
```

热点 key 明细通过单独 API 或 etcd 查询展示。

## 7. 实现方案

### 7.1 保留现有内存 Metrics

当前已有：

```text
tmc-sdk:
  TmcMetrics
  TmcMetricsSnapshot

tmc-server:
  TmcServerMetrics
  TmcServerMetricsSnapshot
```

这些对象继续作为业务代码唯一依赖。

原因：

```text
业务代码只负责 increment；
单元测试可以直接断言 snapshot；
Micrometer/Prometheus 不侵入读写主流程；
后续不用 Prometheus 时仍能通过 metrics() 获取快照。
```

### 7.2 新增 Micrometer Binder

新增：

```text
tmc-sdk/src/main/java/cn/ayice/tmc/sdk/TmcClientMetricsBinder.java
tmc-server/src/main/java/cn/ayice/tmc/server/metrics/TmcServerMetricsBinder.java
```

职责：

```text
读取 TmcMetrics / TmcServerMetrics
注册 Counter / Gauge 到 MeterRegistry
```

约束：

```text
Binder 只做指标注册，不参与业务逻辑；
不要在业务代码里直接调用 MeterRegistry；
不要为单实现 Binder 抽 Java interface。
```

### 7.3 Counter 和 Gauge 选择

使用 `FunctionCounter` 注册累计值：

```text
total_gets
redis_gets
local_cache_hits
messages_consumed
mapping_runs
publish_succeeded
```

使用 `Gauge` 注册当前状态：

```text
tracked_apps
tracked_keys
hot_keys_detected
```

### 7.4 自动配置

SDK：

```text
如果业务应用存在 MeterRegistry
  -> TmcAutoConfiguration 创建 TmcClientMetricsBinder
如果业务应用没有 MeterRegistry
  -> SDK 不注册 Prometheus 指标
```

建议条件：

```java
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnMissingBean
```

Server：

```text
tmc-server 本身已经引入 actuator 和 micrometer-registry-prometheus
启动时创建 TmcServerMetricsBinder
```

## 8. Actuator 配置

### 8.1 tmc-server

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    prometheus:
      enabled: true
```

验证：

```text
GET /actuator/prometheus
```

应能看到：

```text
tmc_server_messages_consumed_total
tmc_server_hot_keys_detected
tmc_server_hot_key_publish_succeeded_total
```

### 8.2 demo 应用

demo 后续需要引入：

```text
spring-boot-starter-actuator
micrometer-registry-prometheus
```

并配置：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  endpoint:
    prometheus:
      enabled: true
```

验证：

```text
GET /actuator/prometheus
```

应能看到：

```text
tmc_client_total_gets_total
tmc_client_redis_gets_total
tmc_client_local_cache_hits_total
tmc_client_invalidation_received_total
```

## 9. Prometheus 配置

示例：

```yaml
scrape_configs:
  - job_name: "tmc-server"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets:
          - "tmc-server:8080"

  - job_name: "tmc-demo"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets:
          - "demo-a:8081"
          - "demo-b:8082"
```

说明：

```text
具体 host 和端口由实际部署决定；
文档和仓库不写入个人服务器 IP、账号或密码。
```

## 10. Grafana 看板设计

### 10.1 主看板

建议一张主看板只放 6 个核心面板：

```text
1. Redis 回源比例
2. 本地缓存命中率
3. 总读 QPS vs Redis 回源 QPS
4. 当前热点 key 数
5. 热点发现到缓存命中的生效趋势
6. 写后失效链路
```

### 10.2 Redis 回源比例

PromQL：

```promql
rate(tmc_client_redis_gets_total[1m])
/
rate(tmc_client_total_gets_total[1m])
```

展示类型：

```text
Time series 或 Stat
```

### 10.3 本地缓存命中率

PromQL：

```promql
rate(tmc_client_local_cache_hits_total[1m])
/
(
  rate(tmc_client_local_cache_hits_total[1m])
  +
  rate(tmc_client_local_cache_misses_total[1m])
)
```

展示类型：

```text
Gauge 或 Time series
```

### 10.4 总读 QPS vs Redis 回源 QPS

PromQL：

```promql
rate(tmc_client_total_gets_total[1m])
```

```promql
rate(tmc_client_redis_gets_total[1m])
```

展示类型：

```text
Time series，两个曲线叠加
```

预期：

```text
热点生效后 total_gets 保持高位，redis_gets 明显低于 total_gets。
```

### 10.5 当前热点 key 数

PromQL：

```promql
tmc_server_hot_keys_detected
```

展示类型：

```text
Stat 或 Time series
```

### 10.6 热点发现到缓存命中的生效趋势

PromQL：

```promql
tmc_server_hot_keys_detected
```

```promql
rate(tmc_client_hot_key_snapshot_applied_total[1m])
```

```promql
rate(tmc_client_local_cache_hits_total[1m])
```

```promql
rate(tmc_client_redis_gets_total[1m])
```

展示类型：

```text
Time series，多曲线观察趋势先后关系
```

### 10.7 写后失效链路

PromQL：

```promql
rate(tmc_client_local_invalidations_total[1m])
```

```promql
rate(tmc_client_invalidation_report_succeeded_total[1m])
```

```promql
rate(tmc_client_invalidation_received_total[1m])
```

展示类型：

```text
Time series 或 Stat
```

验证方式：

```text
demo-a 写热点 key 后：
  demo-a local_invalidations 增长
  demo-a invalidation_report_succeeded 增长
  demo-b invalidation_received 增长
```

## 11. 热点明细展示

热点 key 明细不进入 Prometheus 标签。

推荐方式：

```text
tmc-server 提供 REST API：
  GET /api/hotkeys
  GET /api/hotkeys/{appName}
```

Grafana 如需展示热点明细，可以使用 JSON API / Infinity datasource 读取该接口。

表格字段：

```text
appName
key
score
detectedAt
ttlMillis
```

如果本阶段只做核心指标，可以先不实现 Grafana 热点明细表，后续 demo 联调阶段再补。

## 12. 文件结构

建议新增或修改：

```text
tmc-sdk/src/main/java/cn/ayice/tmc/sdk/TmcClientMetricsBinder.java
tmc-sdk/src/main/java/cn/ayice/tmc/sdk/TmcAutoConfiguration.java
tmc-sdk/src/test/java/cn/ayice/tmc/sdk/TmcClientMetricsBinderTest.java
tmc-sdk/src/test/java/cn/ayice/tmc/sdk/TmcAutoConfigurationTest.java

tmc-server/src/main/java/cn/ayice/tmc/server/metrics/TmcServerMetricsBinder.java
tmc-server/src/main/java/cn/ayice/tmc/server/config/TmcServerConfiguration.java
tmc-server/src/main/resources/application.yml
tmc-server/src/test/java/cn/ayice/tmc/server/metrics/TmcServerMetricsBinderTest.java

infra/prometheus/prometheus.yml
infra/grafana/dashboards/tmc-core-dashboard.json
infra/grafana/provisioning/datasources/prometheus.yml
infra/grafana/provisioning/dashboards/tmc.yml
docker-compose.yml
```

说明：

```text
如果当前 docker-compose 已经有基础设施服务，则只追加 prometheus/grafana，不重写已有服务。
```

## 13. 开发顺序

建议按以下顺序开发：

1. 编写 `TmcServerMetricsBinderTest`。
2. 实现 `TmcServerMetricsBinder`，注册服务端核心指标。
3. 修改 `tmc-server/application.yml`，打开 `/actuator/prometheus`。
4. 编写 `TmcClientMetricsBinderTest`。
5. 实现 `TmcClientMetricsBinder`，注册 SDK 核心指标。
6. 修改 `TmcAutoConfigurationTest`，验证存在 `MeterRegistry` 时创建 binder。
7. 修改 `TmcAutoConfiguration`，自动装配 SDK 指标 binder。
8. 编写 Prometheus 配置文件。
9. 编写 Grafana datasource provisioning。
10. 编写 Grafana dashboard JSON。
11. 修改 `docker-compose.yml`，增加 Prometheus 和 Grafana 服务。
12. 运行 `mvn -s D:\apache-maven-3.9.9\conf\settings.xml -pl tmc-server,tmc-sdk -am test`。
13. 运行 `mvn -s D:\apache-maven-3.9.9\conf\settings.xml test`。
14. 启动 Prometheus/Grafana，验证 scrape target 正常。
15. 打开 Grafana Dashboard，确认核心面板能看到数据。

## 14. 测试设计

### 14.1 `TmcServerMetricsBinderTest`

覆盖：

1. 注册后可以从 `MeterRegistry` 查询 `tmc_server_messages_consumed_total`。
2. `TmcServerMetrics.incrementMessagesConsumed()` 后，Meter 值随之变化。
3. `tmc_server_hot_keys_detected` 使用 Gauge，`setHotKeysDetected(2)` 后读取值为 2。
4. 不注册 key 标签，避免高基数。

### 14.2 `TmcClientMetricsBinderTest`

覆盖：

1. 注册后可以从 `MeterRegistry` 查询 `tmc_client_total_gets_total`。
2. `TmcMetrics.incrementTotalGets()` 后，Meter 值随之变化。
3. `app_name` 和 `client_id` 标签存在。
4. 不注册 key、eventId、snapshotVersion 标签。
5. 失效广播指标可以注册并读取。

### 14.3 `TmcAutoConfigurationTest`

补充：

1. 存在 `MeterRegistry` 时创建 `TmcClientMetricsBinder`。
2. 不存在 `MeterRegistry` 时不创建 binder，SDK 正常启动。
3. 用户自定义 `TmcClientMetricsBinder` 时不覆盖。

## 15. 验收清单

Phase 10 完成时逐项检查：

- [ ] `tmc-server` 暴露 `/actuator/prometheus`。
- [ ] `tmc-server` 能看到 `tmc_server_*` 指标。
- [ ] 接入 SDK 的 demo 能看到 `tmc_client_*` 指标。
- [ ] SDK 指标带 `app_name` 和 `client_id` 标签。
- [ ] Prometheus 能 scrape tmc-server。
- [ ] Prometheus 能 scrape demo-a 和 demo-b。
- [ ] Grafana 有 Redis 回源比例面板。
- [ ] Grafana 有本地缓存命中率面板。
- [ ] Grafana 有总读 QPS vs Redis 回源 QPS 面板。
- [ ] Grafana 有当前热点 key 数面板。
- [ ] Grafana 有热点发现到缓存命中的趋势面板。
- [ ] Grafana 有写后失效链路面板。
- [ ] 指标中没有 Redis key、eventId 等高基数标签。
- [ ] `mvn -s D:\apache-maven-3.9.9\conf\settings.xml test` 通过。

## 16. 面试表达口径

项目可以这样描述观测体系：

```text
我没有把所有内部事件都塞进 Prometheus，而是按 TMC 的核心价值设计指标：
Redis 回源比例、本地缓存命中率、热点发现到生效趋势、写后失效成功情况。
核心趋势指标进入 Prometheus + Grafana；热点 key 明细不作为 label，
避免高基数问题，而是通过服务端 API 或 etcd 查询展示。
```

可以沉淀到简历中的结果：

```text
通过 Prometheus + Grafana 建立 TMC 核心观测看板，监控 Redis 回源比例、
本地缓存命中率、热点发现生效趋势和跨节点失效事件，验证热点读场景下
Redis 回源请求下降 xx%，本地缓存命中率达到 xx%。
```


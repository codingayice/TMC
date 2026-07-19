# Phase 10：核心效果指标与 Grafana 看板设计

## 1. 目标

本阶段只保留能直观看出 TMC 运行效果的指标，不再把访问上报、热点监听、失效广播、服务端滑窗等内部动作全部暴露到 Grafana。

看板只回答四个问题：

1. Key 请求总数是多少。
2. 其中有多少请求命中了 SDK 本地缓存。
3. 本地缓存命中率是多少。
4. 当前 QPS 和 RT 是多少。

这四类指标足够支撑演示和面试说明：热点生效后，请求总量继续增长，本地缓存命中总数同步增长，本地缓存命中率上升，用户视角的 HTTP RT 下降或保持稳定。

## 2. 指标最小集合

核心 Grafana 看板只使用以下最小指标集合，其中缓存效果来自 TMC 自定义指标，RT 来自 Spring Boot Actuator 的 HTTP 指标：

| Prometheus 指标 | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `tmc_sdk_key_request_total` | Counter | `TmcMetrics.totalGets` | SDK 处理的 Key 读请求总数 |
| `tmc_sdk_local_cache_hit_total` | Counter | `TmcMetrics.localCacheHits` | 热点 Key 命中 Caffeine 本地缓存总数 |
| `http_server_requests_seconds_count` | Timer | Spring Boot Actuator | HTTP 请求样本数，Grafana 只过滤商品详情读取接口，用于计算 QPS 和 RT |
| `http_server_requests_seconds_sum` | Timer | Spring Boot Actuator | HTTP 请求累计耗时，Prometheus 自动换算为秒 |

不再暴露以下类型的主观测指标：

- 服务端 `tmc_server_*` 自定义指标。
- Redis 回源次数。
- 热点快照应用次数。
- 本地缓存 miss 次数。
- 访问上报 queued/dropped/failed 次数。
- 失效事件 publish/receive/self-ignore 次数。
- etcd watch reconnect/failed 次数。

这些内部事件可以通过日志、测试或后续专门的诊断页面分析，但不进入核心 Grafana 看板。

## 3. Grafana 面板

### 3.1 Key 请求总数 vs 本地缓存命中总数

用于观察热点生效后，本地缓存是否开始承接访问。

```promql
sum(tmc_sdk_key_request_total)
```

```promql
sum(tmc_sdk_local_cache_hit_total)
```

期望现象：

- 冷启动或热点未生效时，Key 请求总数增长，本地缓存命中总数增长慢或不增长。
- 热点下发并被 SDK 应用后，本地缓存命中总数开始明显增长。

### 3.2 本地缓存命中率

用于直接判断 TMC 是否生效。

```promql
sum(rate(tmc_sdk_local_cache_hit_total[1m]))
/
clamp_min(sum(rate(tmc_sdk_key_request_total[1m])), 0.000001)
```

期望现象：

- 热点生效前接近 0。
- 热点生效后明显上升。
- 手动清空本地热点和本地缓存后会下降，重新制造热点后再次上升。

### 3.3 QPS

用于观察用户查看商品详情接口的 HTTP 请求吞吐。该口径和 RT 保持一致，只统计用户点击商品详情产生的请求，不统计初始化商品、刷新商品列表、刷新指标和清空本地状态等演示操作。

```promql
sum(rate(http_server_requests_seconds_count{method="POST",uri="/api/flash-sale/products/{productId}/detail-view"}[1m]))
```

### 3.4 RT

使用平均 RT 展示用户查看商品详情接口的 HTTP 耗时。这个口径更接近用户体验，因为它覆盖 Controller、Service、TMC 读链路、Redis 回源或本地缓存读取、JSON 处理等应用内耗时。

```promql
1000
*
sum(rate(http_server_requests_seconds_sum{method="POST",uri="/api/flash-sale/products/{productId}/detail-view"}[1m]))
/
clamp_min(sum(rate(http_server_requests_seconds_count{method="POST",uri="/api/flash-sale/products/{productId}/detail-view"}[1m])), 0.000001)
```

单位为毫秒。

## 4. 代码设计

### 4.1 TmcMetrics

`TmcMetrics` 只保留三个累计值：

- `totalGets`
- `localCacheHits`
- `readDurationNanos`

`readDurationNanos` 仍然保留在 SDK 内部，便于后续诊断 SDK 读路径本身的耗时。但核心 Grafana 看板的 RT 不再使用该指标，而是使用 Spring Boot Actuator 的 HTTP Timer，这样能直接观察 TMC 对用户请求耗时的影响。

### 4.2 TmcClientMetricsBinder

`TmcClientMetricsBinder` 只注册：

- `FunctionCounter`：`tmc.sdk.key.request`
- `FunctionCounter`：`tmc.sdk.local.cache.hit`
- `FunctionTimer`：`tmc.sdk.read.duration`

Prometheus 会把它们暴露为：

- `tmc_sdk_key_request_total`
- `tmc_sdk_local_cache_hit_total`
- `tmc_sdk_read_duration_seconds_count`
- `tmc_sdk_read_duration_seconds_sum`

其中 `tmc_sdk_read_duration_seconds_*` 不再作为核心 RT 图的数据源，只作为 SDK 内部读路径诊断指标保留。

### 4.3 服务端指标边界

`tmc-server` 仍然保留内部 `TmcServerMetrics`，用于现有热点探测逻辑和单元测试，但不再通过 Micrometer 注册为 Prometheus 自定义指标。

核心看板只展示 SDK 缓存效果指标和商品详情 HTTP RT，因为最终要证明的是业务读请求是否被本地缓存承接，以及用户请求耗时是否因此改善。

## 5. 验收标准

- `/actuator/prometheus` 能看到 `tmc_sdk_key_request_total`。
- `/actuator/prometheus` 能看到 `tmc_sdk_local_cache_hit_total`。
- `/actuator/prometheus` 能看到 `http_server_requests_seconds_count` 和 `http_server_requests_seconds_sum`。
- Grafana 只保留四张核心图：总量对比、命中率、QPS、RT。
- 不再出现 `tmc_server_*`、`tmc_sdk_redis_*`、`tmc_sdk_invalidation_*`、`tmc_sdk_hotkey_*` 等旧的 TMC 自定义指标查询。

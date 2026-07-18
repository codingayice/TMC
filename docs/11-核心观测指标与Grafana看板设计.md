# Phase 10：核心效果指标与 Grafana 看板设计

## 1. 目标

本阶段只保留能直观看出 TMC 运行效果的指标，不再把访问上报、热点监听、失效广播、服务端滑窗等内部动作全部暴露到 Grafana。

看板只回答四个问题：

1. Key 请求总数是多少。
2. 其中有多少请求命中了 SDK 本地缓存。
3. 本地缓存命中率是多少。
4. 当前 QPS 和 RT 是多少。

这四类指标足够支撑演示和面试说明：热点生效后，请求总量继续增长，本地缓存命中总数同步增长，本地缓存命中率上升，SDK 读请求 RT 下降或保持稳定。

## 2. 指标最小集合

生产代码只暴露以下 TMC 自定义指标：

| Prometheus 指标 | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `tmc_sdk_key_request_total` | Counter | `TmcMetrics.totalGets` | SDK 处理的 Key 读请求总数 |
| `tmc_sdk_local_cache_hit_total` | Counter | `TmcMetrics.localCacheHits` | 热点 Key 命中 Caffeine 本地缓存总数 |
| `tmc_sdk_read_duration_seconds_count` | FunctionTimer | `TmcMetrics.totalGets` | SDK 读请求耗时样本数 |
| `tmc_sdk_read_duration_seconds_sum` | FunctionTimer | `TmcMetrics.readDurationNanos` | SDK 读请求累计耗时，Prometheus 自动换算为秒 |

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

用于观察 Demo 当前产生的 SDK 读请求吞吐。

```promql
sum(rate(tmc_sdk_key_request_total[1m]))
```

### 3.4 RT

使用平均 RT 展示 SDK 读路径整体耗时。

```promql
1000
*
sum(rate(tmc_sdk_read_duration_seconds_sum[1m]))
/
clamp_min(sum(rate(tmc_sdk_read_duration_seconds_count[1m])), 0.000001)
```

单位为毫秒。

## 4. 代码设计

### 4.1 TmcMetrics

`TmcMetrics` 只保留三个累计值：

- `totalGets`
- `localCacheHits`
- `readDurationNanos`

`TmcClient.get()` 在每次读请求开始时记录 `System.nanoTime()`，在 `finally` 中记录读耗时，确保 Redis 正常返回、本地缓存命中、异常抛出等路径都会计入 RT。

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

### 4.3 服务端指标边界

`tmc-server` 仍然保留内部 `TmcServerMetrics`，用于现有热点探测逻辑和单元测试，但不再通过 Micrometer 注册为 Prometheus 自定义指标。

核心看板只展示 SDK 效果指标，因为最终要证明的是业务读请求是否被本地缓存承接。

## 5. 验收标准

- `/actuator/prometheus` 能看到 `tmc_sdk_key_request_total`。
- `/actuator/prometheus` 能看到 `tmc_sdk_local_cache_hit_total`。
- `/actuator/prometheus` 能看到 `tmc_sdk_read_duration_seconds_count` 和 `tmc_sdk_read_duration_seconds_sum`。
- Grafana 只保留四张核心图：总量对比、命中率、QPS、RT。
- 不再出现 `tmc_server_*`、`tmc_sdk_redis_*`、`tmc_sdk_invalidation_*`、`tmc_sdk_hotkey_*` 等旧的 TMC 自定义指标查询。

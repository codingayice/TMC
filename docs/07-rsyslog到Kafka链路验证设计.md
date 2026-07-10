# Phase 6：rsyslog 到 Kafka 链路验证设计

## 1. 阶段目标

Phase 6 的目标是验证 SDK 写入 rsyslog 的访问事件，能够通过 rsyslog 转发进入 Kafka topic。

Phase 5 已经完成 SDK 侧访问事件上报：

```text
TmcClient.get(key, () -> jedis.get(key))
  -> 生成 AccessEvent
  -> AccessReporter
  -> TCP 写入 rsyslog 5514
```

本阶段要打通并验证后半段链路：

```text
AccessReporter
  -> 应用服务器 rsyslog TCP 5514
  -> omkafka
  -> Kafka 服务器 topic: tmc-access-events
  -> Kafka consumer 校验 AccessEvent JSON
```

实际部署拓扑：

| 服务器 | 角色 | 说明 |
|---|---|---|
| Kafka 服务器 | Kafka broker | Kafka 通过 Docker 部署，承载 `tmc-access-events` topic。 |
| 应用服务器 | 应用 / SDK / rsyslog | 业务应用和 SDK 部署在这里，SDK 写入本机 rsyslog。 |

因此真实链路是：

```text
业务应用
  -> 应用服务器本机 rsyslog:5514
  -> Kafka 服务器:9092
  -> tmc-access-events
```

完成后应具备以下能力：

1. rsyslog 容器监听 TCP `5514`。
2. rsyslog 通过 `omkafka` 将收到的 JSON line 写入 Kafka。
3. Kafka 中存在 topic `tmc-access-events`。
4. 可以通过脚本向 rsyslog 写入一条访问事件。
5. 可以从 Kafka 消费到同一条访问事件。
6. 消费到的消息仍然是合法 `AccessEvent` JSON。
7. 验证脚本可以作为后续开发前的基础设施自检。

## 2. 阶段边界

### 2.1 本阶段实现

本阶段实现：

- 检查并完善 `infra/rsyslog/rsyslog.conf`。
- 检查并完善 `infra/rsyslog/Dockerfile`。
- 检查 Kafka 服务器 Docker 配置，确保 Kafka 对应用服务器可访问。
- 检查应用服务器 rsyslog 配置，确保 `omkafka` 指向 Kafka 服务器。
- 完善基础设施 smoke 脚本，验证事件从 rsyslog 进入 Kafka。
- 补充 rsyslog/Kafka 链路文档和排障说明。
- 验证 `AccessReporter` 写出的 JSON line 可以进入 Kafka。

## 3. 设计原则

### 3.1 SDK 不直接依赖 Kafka

SDK 只负责把访问事件写入本地 rsyslog 入口。

```text
SDK
  -> rsyslog
  -> Kafka
```

这样可以保持 SDK 轻量，也符合有赞原文中访问事件通过日志采集链路进入消息队列的思路。

### 3.2 rsyslog 是采集转发层

rsyslog 的职责是：

- 接收 SDK 写入的 JSON line。
- 保持消息内容不被改坏。
- 将消息投递到 Kafka topic。

rsyslog 不负责解析热点，不负责聚合统计，也不负责判断 key 是否为热点。

### 3.3 验证必须端到端

只检查 rsyslog 端口能连通不够。

本阶段验收必须从 Kafka 读到事件：

```text
写入 rsyslog 成功
  !=
Kafka 一定收到
```

所以 smoke 脚本必须完成：

```text
TCP 写 rsyslog
  -> Kafka consumer 读取
  -> 校验 key/appName/clientId/operation
```

## 4. 基础设施配置设计

### 4.1 Kafka 服务器

Kafka 部署在独立服务器上，topic 名称沿用公共常量：

```text
tmc-access-events
```

Kafka 服务器需要创建 topic：

```text
kafka-topics.sh
  --bootstrap-server localhost:9092
  --create
  --if-not-exists
  --topic tmc-access-events
  --partitions 3
  --replication-factor 1
```

如果 Kafka 容器只配置了 Docker 内部地址，例如：

```text
KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092
```

应用服务器上的 rsyslog 可能无法正常投递，因为 Kafka 返回给客户端的 broker 地址是 `kafka:9092`，该主机名只在 Kafka 容器网络内可解析。

Kafka 服务器需要提供应用服务器可访问的 advertised listener，例如：

```text
PLAINTEXT://<kafka-host>:9092
```

具体 Docker 配置可以在实现阶段按当前 Kafka 容器实际情况调整。

验收时需要确认：

```powershell
ssh <kafka-user>@<kafka-host>
docker exec -it tmc-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
```

输出中包含：

```text
tmc-access-events
```

### 4.2 应用服务器 rsyslog 输入

rsyslog 部署在应用服务器上，需要加载 TCP 输入模块并监听 `5514`：

```text
module(load="imtcp")
input(type="imtcp" port="5514")
```

SDK 当前通过 TCP 写入，因此 TCP 是本阶段必须验证的入口。SDK 配置应指向应用服务器本机 rsyslog：

```yaml
tmc:
  report:
    rsyslog:
      host: 127.0.0.1
      port: 5514
```

### 4.3 rsyslog 输出 Kafka

应用服务器上的 rsyslog 需要加载 Kafka 输出模块：

```text
module(load="omkafka")
```

并将收到的消息写入 Kafka：

```text
action(
  type="omkafka"
  broker=["<kafka-host>:9092"]
  topic="tmc-access-events"
  template="TmcJsonLine"
)
```

模板需要保证一条消息对应一行 JSON：

```text
template(name="TmcJsonLine" type="string" string="%msg%\n")
```

### 4.4 本地单机模式

当前仓库中的 `docker-compose.yml` 使用 `kafka:9092`，适合本地单机 Docker Compose 验证：

```text
rsyslog 容器
  -> kafka:9092
```

真实双服务器部署时，需要把应用服务器的 rsyslog Kafka broker 改成：

```text
<kafka-host>:9092
```

不要把 `kafka:9092` 直接用于跨服务器部署。

仓库提供了两个配置文件：

- `infra/rsyslog/rsyslog.conf`：本地 Docker Compose 验证使用，broker 为 `kafka:9092`。
- `infra/rsyslog/rsyslog.remote.conf.example`：双服务器部署参考，需要把 `<kafka-host>` 替换成 Kafka 服务器地址。

## 5. 验证脚本设计

### 5.1 基础设施启动

本地单机验证启动命令：

```powershell
docker compose up -d
./scripts/smoke-infra.ps1
```

真实双服务器验证时：

- 在 Kafka 服务器上启动 Kafka。
- 在应用服务器上启动 rsyslog。
- 在应用服务器上执行 smoke 写入。
- 在 Kafka 服务器上消费 Kafka 校验。

脚本调用示例：

```powershell
./scripts/smoke-infra.ps1 `
  -Mode Remote `
  -AppHost <app-host> `
  -AppUser <app-user> `
  -KafkaHost <kafka-host> `
  -KafkaUser <kafka-user> `
  -KafkaContainer <kafka-container>
```

如果 SSH 已经通过 `~/.ssh/config` 配置好用户，也可以省略 `-AppUser` 和 `-KafkaUser`。

验证服务状态：

```powershell
docker compose ps
```

需要重点确认：

- `tmc-kafka` 为 healthy。
- `tmc-kafka-init` 成功退出。
- `tmc-rsyslog` 正常运行。

### 5.2 写入 smoke 事件

脚本在应用服务器向 `127.0.0.1:5514` 写入一条 JSON line：

```json
{"appName":"tmc-smoke","key":"smoke:key:<timestamp>","timestamp":1720000000000,"weight":1,"clientId":"smoke-<timestamp>","operation":"GET"}
```

注意：

- 消息必须以换行结尾。
- 字段名必须和 `AccessEvent` 一致。
- `operation` 使用 `GET`。

### 5.3 从 Kafka 消费校验

在 Kafka 服务器使用 Kafka console consumer 从 topic 读取：

```powershell
docker exec -i tmc-kafka kafka-console-consumer.sh `
  --bootstrap-server localhost:9092 `
  --topic tmc-access-events `
  --from-beginning `
  --timeout-ms 8000
```

验收脚本至少校验：

- 消息中包含 `smoke:key`。
- 消息中包含 `tmc-smoke`。
- 消息中包含 `smoke-1`。
- 消息中包含 `GET`。

## 6. SDK 联调验证设计

基础设施 smoke 通过后，需要用 SDK 真实写入链路再验证一次。

建议新增一个简单联调入口，放在示例应用模块或脚本中：

```text
构造 TmcProperties
  -> report.rsyslog.host=127.0.0.1
  -> report.rsyslog.port=5514
创建 TmcClient
创建 TmcJedis
调用 TmcJedis.get("product:1")
从 Kafka 消费 product:1 访问事件
```

联调重点不是验证本地缓存命中，而是验证：

```text
TmcJedis.get
  -> TmcClient 生成 AccessEvent
  -> AccessReporter 写 rsyslog
  -> Kafka 可消费
```

## 7. 排障设计

### 7.1 Kafka topic 不存在

检查：

```powershell
ssh <kafka-user>@<kafka-host>
docker logs tmc-kafka-init
docker exec -it tmc-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
```

处理：

- 确认 Kafka healthy。
- 重新执行 `docker compose up -d kafka-init`。
- 手动创建 topic。

### 7.2 rsyslog 容器启动失败

检查：

```powershell
ssh <app-user>@<app-host>
docker logs tmc-rsyslog
```

重点看：

- `omkafka` 模块是否存在。
- `/etc/rsyslog.conf` 是否配置错误。
- Kafka 地址 `<kafka-host>:9092` 是否可访问。

### 7.3 TCP 写入成功但 Kafka 消费不到

检查顺序：

1. rsyslog 是否收到连接。
2. rsyslog 是否加载 `omkafka`。
3. 应用服务器是否能访问 `<kafka-host>:9092`。
4. Kafka advertised listener 是否返回了应用服务器可访问的地址。
5. Kafka topic 是否正确。
6. consumer 是否读错 bootstrap server。
7. 消息是否因为历史数据太多被淹没。

建议 smoke 消息使用唯一 key：

```text
smoke:key:<timestamp>
```

### 7.4 Kafka 消费到的 JSON 不合法

检查：

- SDK 是否使用 `JsonUtils.toJsonLine(event)`。
- rsyslog 模板是否额外添加了 syslog 前缀。
- `%msg%` 是否保留原始消息体。

本项目需要 Kafka 中的 value 是纯 JSON line，不能带 syslog header。

## 8. 测试与验证

建议保留两类验证：

### 8.1 基础设施 smoke

脚本：

```powershell
./scripts/smoke-infra.ps1
```

覆盖：

- Redis 可用。
- etcd 可用。
- Kafka topic 存在。
- 应用服务器 rsyslog TCP 端口可写。
- rsyslog 写入后 Kafka 服务器可消费。

双服务器部署时使用：

```powershell
./scripts/smoke-infra.ps1 `
  -Mode Remote `
  -AppHost <app-host> `
  -KafkaHost <kafka-host>
```

脚本不会内置任何服务器账号、密码或 IP；这些信息需要由执行者通过参数、SSH 配置或安全的运维系统提供。

### 8.2 SDK 到 Kafka 联调

覆盖：

- `AccessReporter` 使用真实 rsyslog 地址。
- `TmcClient.get` 生成真实 `AccessEvent`。
- Kafka 中能消费到该事件。

联调可以先作为手动命令或示例应用，不要求放入 Maven 单元测试，因为它依赖 Docker 基础设施。

## 9. 开发顺序

建议按以下顺序开发：

1. 登录 Kafka 服务器 `<kafka-host>`，确认 Kafka 容器运行。
2. 确认 Kafka advertised listener 对应用服务器可访问。
3. 确认 Kafka topic `tmc-access-events` 存在。
4. 登录应用服务器，部署 rsyslog。
5. 配置应用服务器 rsyslog 监听 TCP `5514`。
6. 配置应用服务器 rsyslog `omkafka` broker 为 `<kafka-host>:9092`。
7. 完善 smoke 脚本，支持在应用服务器写入 rsyslog，在 Kafka 服务器消费校验。
8. 增加唯一 smoke key，避免历史消息干扰。
9. 增加 SDK 到 Kafka 的联调入口或说明。
10. 执行 smoke 验证。
11. 执行 `mvn test`，确认代码测试仍通过。

## 10. 验收清单

Phase 6 完成时逐项检查：

- [ ] Kafka topic `tmc-access-events` 存在。
- [ ] Kafka 服务器 `<kafka-host>:9092` 能被应用服务器访问。
- [ ] Kafka advertised listener 不返回 Docker 内部主机名 `kafka`。
- [ ] 应用服务器 rsyslog 容器安装了 `rsyslog-kafka`。
- [ ] 应用服务器 rsyslog 监听 TCP `5514`。
- [ ] 应用服务器 rsyslog 使用 `omkafka` 转发到 Kafka 服务器。
- [ ] rsyslog 转发后的 Kafka message 是纯 JSON。
- [ ] `scripts/smoke-infra.ps1` 能写入 rsyslog 并从 Kafka 读到同一条事件。
- [ ] smoke 事件字段包含 `appName`、`key`、`timestamp`、`weight`、`clientId`、`operation`。
- [ ] SDK 真实 `AccessReporter` 写出的事件能进入 Kafka。
- [ ] rsyslog/Kafka 排障说明完整。
- [ ] `mvn test` 通过。

## 11. 和后续阶段的关系

Phase 6 完成后，访问事件已经进入 Kafka。

后续 `tmc-server` 可以直接消费 `tmc-access-events`，把 `AccessEvent` 作为服务端热点统计的输入。

# rsyslog 配置

该目录保存 TMC 访问事件采集链路使用的 rsyslog 配置。

SDK 会将访问事件作为 JSON line 写入 rsyslog，rsyslog 通过 `omkafka` 将消息转发到 Kafka topic `tmc-access-events`。

## 本地 Docker Compose

`rsyslog.conf` 用于本地 `docker compose` 验证：

```text
rsyslog -> kafka:9092 -> tmc-access-events
```

监听端口：

- TCP `5514`
- UDP `5514`

Kafka broker：

```text
kafka:9092
```

## 双服务器部署

跨服务器部署时不要直接使用 `kafka:9092`，因为该主机名只在 Docker Compose 网络内有效。

可以参考 `rsyslog.remote.conf.example`，把 broker 改成 Kafka 服务器对应用服务器可访问的地址：

```text
broker=["<kafka-host>:9092"]
```

要求 Kafka 的 advertised listener 也返回应用服务器可访问的地址。

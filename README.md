# TMC

TMC 是一个基于有赞技术文章《透明多级缓存解决方案 TMC》实现的透明多级缓存项目。

原文链接：

- https://tech.youzan.com/tmc/

项目按照原文中的核心链路进行实现：

```text
TmcJedis
  -> TmcClient
  -> 本地 Caffeine 缓存
  -> Redis
  -> rsyslog
  -> Kafka
  -> tmc-server
  -> 滑动窗口热点探测
  -> etcd
  -> SDK 热点 key 监听
```

## 模块说明

| 模块 | 职责 |
|---|---|
| `tmc-common` | 公共模型、常量、协议定义和通用工具。 |
| `tmc-sdk` | 客户端热点 key 管理、本地缓存、访问事件上报、etcd 监听、失效处理和指标统计。 |
| `tmc-jedis` | Jedis 风格接入层，将 Redis 读写操作接入 TMC 链路。 |
| `tmc-server` | Kafka 访问事件消费、滑动窗口热点探测和 etcd 热点结果发布。 |
| `tmc-demo` | 演示应用，用于验证直连 Redis 与 TMC 访问链路。 |
| `tmc-benchmark` | 压测模块，用于验证 Redis 压力、本地命中率、延迟和失效行为。 |

## 本地环境

- Java 17
- Maven 3.8+
- Docker
- Docker Compose
- Windows 环境建议使用 PowerShell 7+

## 启动基础设施

在项目根目录执行：

```powershell
docker compose up -d
```

本地基础设施包括：

| 服务 | 端口 | 用途 |
|---|---:|---|
| Redis | `6379` | 集中缓存存储 |
| etcd | `2379` | 热点 key 下发和失效事件广播 |
| Kafka | `9092` | 访问事件消息队列 |
| rsyslog | `5514` | SDK 访问事件采集入口 |

Kafka topic：

```text
tmc-access-events
```

## 环境检查

```powershell
./scripts/check-env.ps1
docker compose up -d
./scripts/smoke-infra.ps1
```

双服务器部署时，可以使用远端模式验证应用服务器 rsyslog 到 Kafka 服务器的链路：

```powershell
./scripts/smoke-infra.ps1 `
  -Mode Remote `
  -AppHost <app-host> `
  -KafkaHost <kafka-host>
```

## 构建与测试

```powershell
mvn -DskipTests compile
mvn test
```

## 文档

- [项目 PRD](docs/00-prd.md)
- [Phase 1：工程基础与本地环境](docs/01-工程骨架设计.md)
- [服务器基础设施说明](docs/02-服务器基础设施说明.md)
- [Phase 2：公共模型与协议](docs/03-公共模型与协议设计.md)
- [Phase 3：SDK 本地缓存核心](docs/04-SDK本地缓存核心设计.md)
- [Phase 4：Jedis 接入层](docs/05-Jedis接入层设计.md)
- [Phase 5：SDK 访问事件采集](docs/06-SDK访问事件采集设计.md)
- [Phase 6：rsyslog 到 Kafka 链路验证](docs/07-rsyslog到Kafka链路验证设计.md)
- [Phase 7：服务端热点发现](docs/08-服务端热点发现设计.md)
- [Phase 8：热点发布与发现监听](docs/09-热点发布与发现监听设计.md)
- [Phase 9：本地缓存失效广播](docs/10-本地缓存失效广播设计.md)

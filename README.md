# TMC

TMC is a learning-oriented reproduction of Youzan's Transparent Multilevel Cache design.

Reference article:

- https://tech.youzan.com/tmc/

The project follows the article's main chain:

```text
TmcJedis
  -> TmcClient
  -> Local Caffeine Cache
  -> Redis
  -> rsyslog
  -> Kafka
  -> tmc-server
  -> Sliding Window Detector
  -> etcd
  -> SDK hot key watch
```

## Modules

| Module | Responsibility |
|---|---|
| `tmc-common` | Shared models, constants, protocol definitions, and utilities. |
| `tmc-sdk` | Client-side hot key management, local cache, reporting, etcd watch, invalidation, and metrics. |
| `tmc-jedis` | Jedis-style integration layer that routes Redis operations through TMC. |
| `tmc-server` | Kafka access-event consumer, sliding-window hot key detector, and etcd publisher. |
| `tmc-demo` | Demo business application for direct Redis and TMC access paths. |
| `tmc-benchmark` | Benchmark scenarios for Redis pressure, hit rate, latency, and invalidation behavior. |

## Local Requirements

- Java 17
- Maven 3.8+
- Docker
- Docker Compose
- PowerShell 7+ recommended on Windows

## Start Infrastructure

From the project root:

```powershell
docker compose up -d
```

The local infrastructure includes:

| Service | Port | Purpose |
|---|---:|---|
| Redis | `6379` | Remote cache storage |
| etcd | `2379` | Hot key publishing and invalidation broadcast |
| Kafka | `9092` | Access event message queue |
| rsyslog | `5514` | SDK access event collection entry |

Kafka topic:

```text
tmc-access-events
```

## Smoke Check

```powershell
./scripts/check-env.ps1
docker compose up -d
./scripts/smoke-infra.ps1
```

## Build

```powershell
mvn -DskipTests compile
mvn test
```

## Documentation

- [Project PRD](docs/00-prd.md)
- [Phase 1 Engineering Foundation](docs/01-工程骨架设计.md)
- [Server Infrastructure](docs/02-服务器基础设施说明.md)
- [Phase 2 Common Models and Protocol](docs/03-公共模型与协议设计.md)

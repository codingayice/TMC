# Kafka

Kafka stores access events reported by SDK nodes through rsyslog.

Default topic:

```text
tmc-access-events
```

Default consumer group for the future server:

```text
tmc-server
```

Useful commands:

```powershell
docker compose exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
docker compose exec kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic tmc-access-events --from-beginning --timeout-ms 5000
```


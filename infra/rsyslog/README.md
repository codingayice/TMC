# rsyslog

This directory contains the local rsyslog configuration for Phase 1.

The SDK will write access events as JSON lines to rsyslog. rsyslog forwards those messages to Kafka topic `tmc-access-events` through `omkafka`.

Local listener:

- TCP `5514`
- UDP `5514`

Kafka target:

- Broker: `kafka:9092`
- Topic: `tmc-access-events`


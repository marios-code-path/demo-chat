# chat-messaging-kafka — Implementation Proposal

**Module:** `chat-messaging-kafka`
**Date:** 2026-03-24
**Status:** draft

---

## Overview

`chat-messaging-kafka` is currently a skeleton with no source files. It must implement
`TopicPubSubService<T, V>` from `chat-core`, the same contract satisfied by `chat-messaging-memory`.

---

## Contract

`TopicPubSubService<T, V>` combines two interfaces:

### `PubSubService<T, V>`

| Method | Kafka mapping |
|---|---|
| `sendMessage(message)` | Produce to topic `message.key.dest.toString()` |
| `listenTo(topic)` | Consume from Kafka topic, bridge to `Flux` |
| `subscribe(member, topic)` | Local membership tracking (not a Kafka concept) |
| `unSubscribe(member, topic)` | Local membership tracking |
| `unSubscribeAll(member)` | Local membership tracking |
| `unSubscribeAllIn(topic)` | Local membership tracking |
| `exists(topic)` | `AdminClient.describeTopics()` |

### `TopicInventoryService<T>`

| Method | Kafka mapping |
|---|---|
| `open(topicId)` | `AdminClient.createTopics()` |
| `close(topicId)` | `AdminClient.deleteTopics()` |
| `getByUser(uid)` | Local membership map |
| `getUsersBy(topicId)` | Local membership map |

---

## File Layout

Mirrors `chat-messaging-memory`:

```
src/main/kotlin/com/demo/chat/
  config/pubsub/kafka/
    KafkaPubSubBeans.kt
  pubsub/kafka/impl/
    KafkaTopicPubSubService.kt
    KafkaTopicAdmin.kt
```

### `KafkaPubSubBeans.kt`

- `@Component`
- `@ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"], havingValue = "kafka")`
- Implements `PubSubServiceBeans<T, V>`
- Wires `ReactiveKafkaProducerTemplate`, `ReactiveKafkaConsumerTemplate`, `KafkaAdmin`

### `KafkaTopicPubSubService.kt`

Core implementation of `TopicPubSubService<T, V>`.

**sendMessage** — delegates to `ReactiveKafkaProducerTemplate`, using
`message.key.dest.toString()` as the Kafka topic name.

**listenTo** — maintains a `ConcurrentHashMap<T, Sinks.Many<Message<T,V>>>`. Each `listenTo(topic)`
call returns `sink.asFlux()`. A single shared `ReactiveKafkaConsumerTemplate` consumer loop feeds
incoming records into the appropriate sink. Sinks use `Sinks.many().multicast().onBackpressureBuffer()`
so multiple callers can subscribe to the same topic concurrently.

**subscribe / unSubscribe / unSubscribeAll / unSubscribeAllIn** — maintained entirely in-memory via
two `ConcurrentHashMap`s (topic→members, member→topics), same as `MemoryTopicPubSubService`.
Kafka does not track application-level member inventory.

### `KafkaTopicAdmin.kt`

Thin wrapper around `KafkaAdmin` / `AdminClient` for:
- `open` → `CreateTopicsResult` (single partition, replication factor 1 for dev)
- `close` → `DeleteTopicsResult`
- `exists` → `DescribeTopicsResult` mapped to `Mono<Boolean>`

---

## pom.xml additions required

```xml
<dependency>
    <groupId>io.projectreactor.kafka</groupId>
    <artifactId>reactor-kafka</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

Both are managed by the Spring Boot 3.3.x BOM — no explicit versions needed.

---

## Docker Compose (KRaft, no Zookeeper)

```yaml
services:
  kafka:
    image: confluentinc/cp-kafka:7.7.1
    hostname: kafka
    ports:
      - "9092:9092"
      - "9093:9093"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
      CLUSTER_ID: "chat-kafka-dev-001"
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 10s
      timeout: 5s
      retries: 5
```

`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"` is deliberate — topic lifecycle is owned by
`open()` / `close()` on `KafkaTopicAdmin`, matching the contract.

---

## Spring Boot configuration

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: chat-messaging
      auto-offset-reset: latest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.demo.chat.domain"
```

---

## Notes

- `Message<T, V>` carries full Jackson type info (`@JsonTypeInfo`, `@JsonSubTypes`) — the
  `JsonSerializer` / `JsonDeserializer` pair from `spring-kafka` handles this without custom
  serializers, provided `trusted.packages` is set.
- Topic names are `T.toString()` — assumes `T` produces a stable, Kafka-legal string
  (UUID and Long both work). If `T` requires custom formatting, add a `TopicNameEncoder<T>` strategy.
- `subscribe` / `unSubscribe` membership is not durable. On restart the maps are empty.
  If durability is needed, back the maps with a KTable or the existing persistence layer.
- The `listenTo` consumer loop must be started on module init (e.g. `@PostConstruct` or
  `ApplicationRunner`). It should subscribe to all currently open topics and dynamically
  add new ones as `open()` is called.

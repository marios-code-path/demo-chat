# `TopicPubSubService` Implementation Architecture

This document describes the class and method architecture needed to build a Kafka-backed `TopicPubSubService<T, V>` in `chat-messaging-kafka`.

It is intentionally design-only. It does not implement the service.

## Goal

Provide a `TopicPubSubService<T, V>` implementation that:

- creates and deletes topics through Kafka admin APIs
- publishes `Message<T, V>` values into Kafka topics
- exposes a reactive `listenTo(topic)` stream per topic
- tracks application-level member subscriptions in memory
- answers inventory queries like `getByUser()` and `getUsersBy()`

## Existing Contract

The target service must satisfy:

- `PubSubService<T, V>`
- `TopicInventoryService<T>`
- combined as `TopicPubSubService<T, V>`

These methods must exist:

- `open(topicId: T): Mono<Void>`
- `close(topicId: T): Mono<Void>`
- `getByUser(uid: T): Flux<T>`
- `getUsersBy(topicId: T): Flux<T>`
- `subscribe(member: T, topic: T): Mono<Void>`
- `unSubscribe(member: T, topic: T): Mono<Void>`
- `unSubscribeAll(member: T): Mono<Void>`
- `unSubscribeAllIn(topic: T): Mono<Void>`
- `sendMessage(message: Message<T, V>): Mono<Void>`
- `listenTo(topic: T): Flux<out Message<T, V>>`
- `exists(topic: T): Mono<Boolean>`

## Required Classes

## 1. `KafkaTopicPubSubService<T, V>`

Primary orchestration class. This is the concrete `TopicPubSubService<T, V>` implementation.

### Dependencies

- `ReactiveKafkaProducerTemplate<String, Message<T, V>>`
- `ReactiveKafkaConsumerTemplate<String, Message<T, V>>` or `KafkaReceiver<String, Message<T, V>>`
- `KafkaTopicAdmin<T>`
- `TypeUtil<T>`

### Responsibility

- delegate Kafka topic lifecycle to `KafkaTopicAdmin`
- publish messages to Kafka
- expose topic-local reactive streams to callers
- maintain member/topic inventory maps
- start and maintain the Kafka consumer loop
- route consumed Kafka records into the correct in-memory sink

### Internal State

- `sinks: MutableMap<T, Sinks.Many<Message<T, V>>>`
  - one multicast sink per topic
- `topicMembers: MutableMap<T, MutableSet<T>>`
  - topic -> members
- `memberTopics: MutableMap<T, MutableSet<T>>`
  - member -> topics
- `openTopics: MutableSet<T>`
  - tracks topics known to be open in this process
- `consumerBindings: MutableMap<T, Disposable>`
  - optional; used if one consumer subscription is managed per topic

## 2. `KafkaTopicAdmin<T>`

Thin admin wrapper.

### Responsibility

- create topic
- delete topic
- check topic existence
- convert logical topic id `T` into Kafka topic name using `TypeUtil<T>`

This class already exists and should remain narrow.

## 3. `KafkaTopicStreamRegistry<T, V>` (recommended helper)

This helper is not strictly required, but it makes `KafkaTopicPubSubService` much easier to reason about.

### Responsibility

- create or return a sink for a topic
- emit a consumed message into the topic sink
- terminate and remove sinks on topic close
- provide `Flux` views for `listenTo(topic)`

### Why extract it

Without this helper, `KafkaTopicPubSubService` ends up mixing:

- Kafka IO
- inventory bookkeeping
- sink lifecycle
- consumer routing

That is too much for one class.

## 4. `KafkaMembershipIndex<T>` (recommended helper)

This helper owns the bidirectional in-memory subscription maps.

### Responsibility

- add subscription `(member, topic)`
- remove subscription `(member, topic)`
- remove all subscriptions for a member
- remove all members in a topic
- return topics by member
- return members by topic

### Why extract it

These operations are independent of Kafka and mirror the memory implementation. Keeping them isolated avoids mixing transport concerns with inventory concerns.

## 5. `KafkaConsumerCoordinator<T, V>` (recommended helper)

This helper owns the receive loop and hands messages to the stream registry.

### Responsibility

- subscribe consumer(s) to active topics
- react to `open(topic)` by registering a topic for consumption
- react to `close(topic)` by stopping consumption for that topic
- deserialize incoming records and route by topic id

### Important note

The current bean wiring only provides a producer and admin client. A full `listenTo(topic)` implementation also needs a receiver-side dependency.

That means `KafkaPubSubBeans` must eventually provide one of:

- `ReactiveKafkaConsumerTemplate<String, Message<T, V>>`
- `KafkaReceiver<String, Message<T, V>>`

Without a consumer-side dependency, `listenTo(topic)` cannot be made real.

## Method Architecture

## `open(topicId: T): Mono<Void>`

### Responsibility

1. create the Kafka topic if needed
2. register local sink state
3. register the topic with the consumer coordinator
4. mark the topic as open locally

### Delegation

- `KafkaTopicAdmin.create(topicId)`
- `KafkaTopicStreamRegistry.ensureTopic(topicId)`
- `KafkaConsumerCoordinator.startTopic(topicId)`

## `close(topicId: T): Mono<Void>`

### Responsibility

1. stop consumer routing for that topic
2. remove all member subscriptions in that topic
3. complete and remove the sink
4. delete the Kafka topic
5. remove from local open-topic tracking

### Delegation

- `KafkaConsumerCoordinator.stopTopic(topicId)`
- `KafkaMembershipIndex.removeTopic(topicId)`
- `KafkaTopicStreamRegistry.closeTopic(topicId)`
- `KafkaTopicAdmin.delete(topicId)`

## `exists(topic: T): Mono<Boolean>`

### Responsibility

- delegate directly to `KafkaTopicAdmin.exists(topic)`

This should not depend on local in-memory state, because Kafka is the system of record for topic existence.

## `listenTo(topic: T): Flux<out Message<T, V>>`

### Responsibility

- return the shared `Flux` for that topic
- create the sink lazily if needed

### Design choice

`listenTo(topic)` should not itself create the Kafka topic. It should only expose a stream. Topic lifecycle remains the job of `open()`.

### Delegation

- `KafkaTopicStreamRegistry.fluxFor(topic)`

## `sendMessage(message: Message<T, V>): Mono<Void>`

### Responsibility

1. derive Kafka topic name from `message.key.dest`
2. optionally verify topic exists
3. send through the producer template

### Delegation

- `KafkaTopicAdmin.exists(dest)` or trust producer failure
- `producer.send(topicName, message)`

### Decision point

Choose one of these behaviors and keep it consistent:

- strict mode: verify topic existence before send and fail fast
- lean mode: send directly and let Kafka produce the failure if the topic is missing

The memory implementation behaves like strict mode.

## `subscribe(member: T, topic: T): Mono<Void>`

### Responsibility

1. verify the topic exists
2. add member to topic inventory
3. add topic to member inventory

### Delegation

- `KafkaTopicAdmin.exists(topic)`
- `KafkaMembershipIndex.add(member, topic)`

No Kafka broker operation is needed here. This is application-local state.

## `unSubscribe(member: T, topic: T): Mono<Void>`

### Responsibility

- remove the bidirectional membership mapping

### Delegation

- `KafkaMembershipIndex.remove(member, topic)`

## `unSubscribeAll(member: T): Mono<Void>`

### Responsibility

- remove the member from all tracked topics

### Delegation

- `KafkaMembershipIndex.removeMember(member)`

## `unSubscribeAllIn(topic: T): Mono<Void>`

### Responsibility

- remove all members tracked in a topic

### Delegation

- `KafkaMembershipIndex.removeTopic(topic)`

## `getByUser(uid: T): Flux<T>`

### Responsibility

- return all topics for the user from local membership state

### Delegation

- `KafkaMembershipIndex.topicsFor(uid)`

## `getUsersBy(topicId: T): Flux<T>`

### Responsibility

- return all members in the topic from local membership state

### Delegation

- `KafkaMembershipIndex.membersFor(topicId)`

## Data Flow

## Outbound

1. caller invokes `sendMessage(message)`
2. service derives Kafka topic name from `message.key.dest`
3. producer publishes to Kafka

## Inbound

1. consumer receives Kafka record for topic `X`
2. consumer coordinator converts topic name back to `T` if necessary
3. coordinator pushes `record.value()` into the sink for topic `X`
4. all callers of `listenTo(X)` receive the message from the shared flux

## Minimal Viable Class Layout

If you want the fewest classes possible, the minimum set is:

- `KafkaTopicPubSubService<T, V>`
- `KafkaTopicAdmin<T>`

In that layout, the following stay as private methods inside `KafkaTopicPubSubService`:

- `topicName(topic: T): String`
- `sinkFor(topic: T): Sinks.Many<Message<T, V>>`
- `topicsFor(member: T): MutableSet<T>`
- `membersFor(topic: T): MutableSet<T>`
- `startConsumer(topic: T): Unit`
- `stopConsumer(topic: T): Unit`
- `routeRecord(topicName: String, message: Message<T, V>): Unit`

This is viable, but it will grow hard to maintain once `listenTo()` and consumer lifecycle are implemented.

## Better Maintained Layout

Recommended split:

- `KafkaTopicPubSubService<T, V>`: public contract only
- `KafkaTopicAdmin<T>`: admin calls only
- `KafkaTopicStreamRegistry<T, V>`: sink lifecycle and routing
- `KafkaMembershipIndex<T>`: subscription bookkeeping
- `KafkaConsumerCoordinator<T, V>`: receiver lifecycle

This separation keeps transport, inventory, and stream state from collapsing into one large class.

## Bean Wiring Needed

`KafkaPubSubBeans` will need to provide enough wiring for both publish and consume paths.

Minimum collaborators:

- producer bean
- admin client bean
- consumer bean
- `TypeUtil<T>`

If the consumer side is omitted, `listenTo(topic)` can only return a local sink and will never receive broker messages.

## Error Handling Expectations

- `open(topic)` should be idempotent
- `close(topic)` should be idempotent
- `subscribe(member, topic)` should fail or no-op if the topic does not exist; choose one behavior explicitly
- `sendMessage(message)` should surface producer failures
- consumer failures should restart or fail visibly; silent sink starvation is the worst outcome

## Concurrency Expectations

Because this service is reactive and map-backed, use concurrent collections:

- `ConcurrentHashMap`
- concurrent set wrappers where needed

Guard against:

- duplicate `open()` calls
- `close()` racing with `listenTo()`
- `unSubscribeAll()` racing with `close()`
- consumer emissions into a sink after the topic was closed

## Implementation Order

Recommended sequence:

1. finish `KafkaTopicAdmin`
2. add consumer bean wiring
3. implement sink registry
4. implement `open`, `close`, `exists`, `listenTo`
5. implement membership methods
6. implement `sendMessage`
7. add consumer coordinator and broker-to-sink routing
8. add tests for topic lifecycle, send/receive, and membership bookkeeping

## Summary

The service needs more than just producer logic. A complete Kafka-backed `TopicPubSubService` requires four concerns to work together:

- Kafka admin for topic lifecycle
- Kafka producer for outbound messages
- Kafka consumer coordination for inbound messages
- local membership indexing for user/topic inventory

The cleanest architecture is to keep `KafkaTopicPubSubService` thin and push stream state, membership state, and consumer lifecycle into dedicated helpers.

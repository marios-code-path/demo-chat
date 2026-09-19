# Pubsub Delivery Design

This design covers the XStream and Kafka delivery failures measured on
`boot4-gate-probe` at `f205f377`. It keeps their repairs separate because each
backend failed at a different boundary.

## Goals

- Deliver each new topic message to listeners that opened the topic first.
- Keep each backend's existing message body shape.
- Keep implementation class names out of Kafka records.
- Keep the Redis Stream receiver local to this service instance.
- Prove delivery with backend tests that inspect the received message and destination.

## XStream: read from a stable stream cursor

`XStreamTopicPubSubService` stores topic messages in Redis Streams. Its current
`XREAD` call reads once and completes. The service also discards a decorated
Flux, so no active reader feeds the local sink.

Use Spring Data Redis `StreamReceiver` for a long-lived, standalone stream read.
When `open(topic)` starts the reader, first read the current last stream ID.
Use `0-0` when the stream has no entries. Start the receiver from that ID. This
preserves the current no-replay behavior while including messages written after
the cursor read.

Keep one receiver subscription and one local sink per open topic. Store the
subscription handle. Dispose it when `close(topic)` runs. Complete the sink when
the topic closes. Make concurrent `open(topic)` calls create only one receiver.
Keep the existing stream trim limit of 50 records.

Do not use `ReadOffset.latest()` for the repeated receiver poll. Spring Data
documents that polling with this offset can skip messages between poll requests.
A specific ID advances as messages arrive. Do not use consumer groups for this
change. The current contract is local fan-out, not cross-instance ownership or
acknowledged delivery.

This design does not guarantee delivery after the stream trim removes an unread
record. It does not add cross-instance delivery. Consumer groups remain a
separate decision if the product needs those guarantees.

### XStream proof

- Keep the shared delivery test subscribed before it sends.
- Assert the received message and its destination against a Redis container.
- Prove repeated `open(topic)` calls do not create duplicate readers.
- Prove concurrent `open(topic)` calls do not create duplicate readers.
- Prove `close(topic)` disposes the reader and completes the listener.
- Keep the existing memory and Redis pubsub tests green.

## Kafka: use the declared `Message` type

Kafka delivery reaches the broker and the consumer fetches the record. The
consumer then fails because the serializer writes a type header for the
anonymous class returned by `Message.create`.

Keep the existing JSON body. Disable type headers on the producer. Configure
the consumer to ignore type headers and decode the declared `Message` type.
Use the Jackson 2 mapper with the chat domain module for this codec. This keeps
the message wrapper and key shapes while removing the generated class name from
the record contract.

The consumer must ignore old type headers too. This lets records written by the
current producer use the fixed `Message` target. The body must still pass the
domain deserializer. Do not add implementation-class mappings or a new wire
envelope in this change.

### Kafka proof

- Use the embedded Kafka broker and the shared delivery test.
- Assert the receiver gets the expected message and destination.
- Assert the producer does not write the anonymous class name as type metadata.
- Decode a record with the old type header while the consumer ignores that header.
- Keep the Kafka sender and receiver contract tests green.
- Keep the memory, Redis pubsub, and XStream delivery tests green.

This change measures the exercised send, consume, and decode path only. It does
not prove transactions, multiple partitions, rebalances, or non-embedded brokers.

## Scope boundaries

- The XStream reader does not add consumer groups or cross-instance guarantees.
- The Kafka repair does not change the JSON body or introduce a transport DTO.
- The Kafka repair does not claim compatibility with every historical payload.
- This work does not change the parked Elasticsearch module or Boot parent branch.

## References

- `CHAT-flhybnhm` owns the XStream live-read repair.
- `CHAT-bshubwsl` owns the Kafka type-header repair.
- [Spring Data Redis stream documentation](https://docs.spring.io/spring-data/redis/reference/redis/redis-streams.html)
- [Spring Kafka serialization documentation](https://docs.spring.io/spring-kafka/reference/kafka/serdes.html)

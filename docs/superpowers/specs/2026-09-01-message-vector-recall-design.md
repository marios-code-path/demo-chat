# Message Vector Recall

Status: design approved in conversation on 2026-09-01.

## Purpose

Demo Chat will become a substrate for agentic workflows.

Agents need semantic recall over prior messages.

This sprint adds message vector recall only.

It does not add topic vectors, user vectors, summary vectors, or graph storage.

## Source APIs

Pin Spring AI with this BOM:

- `org.springframework.ai:spring-ai-bom:1.0.3`

Do not use Spring AI `2.0.0` in this sprint.

The first plan task must compile an API probe against the pinned BOM.

The probe checks these accessors:

- `Document.getId()`
- `Document.getText()`
- `Document.getMetadata()`
- `Document.getScore()`
- `SearchRequest.builder()`
- `SearchRequest.Builder.query(String)`
- `SearchRequest.Builder.topK(Int)`
- `SearchRequest.Builder.similarityThreshold(Double)`
- `SearchRequest.Builder.filterExpression(String)`
- `SearchRequest.Builder.build()`
- `SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL`

Use Spring AI `VectorStore` as the vector abstraction.

Use Spring AI `VectorStoreRetriever` for read-only recall.

Use Spring AI `SearchRequest` for query text, result limit, threshold, and metadata filters.

Use Spring AI `Document` as the stored vector document.

`Document.id` is the message document id.

`Document.text` is the message data.

`Document.metadata` stores message scope.

`Document.score` is the recall score.

Spring AI `VectorStore` supports these operations:

- `add(List<Document>)`
- `delete(List<String>)`
- `delete(Filter.Expression)`
- `similaritySearch(SearchRequest)`
- `similaritySearch(String)`

`VectorStore`, `VectorStoreRetriever`, and `EmbeddingModel` are blocking APIs.

Provider code must bridge them before it returns `Mono` or `Flux`.

Use `Mono.fromCallable { ... }.subscribeOn(Schedulers.boundedElastic())` for each Spring AI call.

Do not call Spring AI blocking methods directly on request or reactor worker threads.

## Contract

Add `MessageRecallService<T>` in `chat-core`.

It has three functions:

```kotlin
interface MessageRecallService<T> {
    fun recallInTopic(req: TopicRecallRequest<T>): Flux<MessageRecallHit<T>>
    fun recallByUser(req: UserRecallRequest<T>): Flux<MessageRecallHit<T>>
    fun recallGlobal(req: GlobalRecallRequest): Flux<MessageRecallHit<T>>
}
```

Add `MessageVectorIndexer<T>` in `chat-core`.

It writes message documents to `VectorStore`.

It does not replace `MessageIndexService`.

```kotlin
interface MessageVectorIndexer<T> {
    fun add(message: Message<T, String>): Mono<Void>
    fun remove(key: Key<T>): Mono<Void>
}
```

The recall service returns keys and scores only.

Existing persistence loads full messages.

```kotlin
data class MessageRecallHit<T>(
    val key: MessageKey<T>,
    val score: Double?
)
```

The hit carries `MessageKey<T>` because the caller must reload the message.

`Key<T>` does not carry `from` or `dest`.

`MessageVectorIndexer.remove(key)` removes by message id only.

It does not need `from` or `dest`.

## Requests

Topic recall:

```kotlin
data class TopicRecallRequest<T>(
    val topicId: T,
    val query: String,
    val limit: Int = 10,
    val threshold: Double = 0.0
)
```

User recall:

```kotlin
data class UserRecallRequest<T>(
    val userId: T,
    val query: String,
    val limit: Int = 10,
    val threshold: Double = 0.0
)
```

Global recall:

```kotlin
data class GlobalRecallRequest(
    val query: String,
    val limit: Int = 10,
    val threshold: Double = 0.0
)
```

Validation rules:

- `query` must not be blank.
- `limit` defaults to `10`.
- `limit` must be at least `1`.
- `limit` must be at most `50`.
- `threshold` defaults to `0.0`.
- `threshold` must be in `0.0..1.0`.

## Document Shape

Store one Spring AI `Document` per persisted message.

Skip messages where `message.record == false`.

Join alerts and leave alerts must not enter the recall corpus.

Document id format:

```text
message:<keyType>:<messageId>
```

Use the same format for `MessageVectorIndexer.remove(key)`.

Message ids are unique per key type.

Metadata fields:

- `kind = "message"`
- `messageId = message.key.id`
- `topicId = message.key.dest`
- `userId = message.key.from`
- `keyType = long | uuid`

Filters:

- Topic recall: `kind == 'message' && keyType == '<keyType>' && topicId == '<id>'`
- User recall: `kind == 'message' && keyType == '<keyType>' && userId == '<id>'`
- Global recall: `kind == 'message' && keyType == '<keyType>'`

## Activation

Add two capabilities:

- `app.service.core.vector`
- `app.service.core.embedding`

Normal chat does not require either capability.

Recall activates only when both capabilities are set.

If one selector is set and the other is unset, startup fails.

The error must name both selectors.

If both selectors are unset, recall stays inactive.

No selector uses `matchIfMissing`.

The capability mechanism does not exist in code yet.

This sprint does not depend on that mechanism.

Provider classes use `@ConditionalOnProperty` in this sprint.

A startup validation bean checks the vector and embedding selector pair.

When the capability mechanism lands, migrate these providers to `@ProvidesCapability`.

That later migration follows capability design decision 3.

Module ownership:

- `chat-core` owns contracts, DTOs, validation, mappers, and mock test fixtures.
- `chat-service-composite` owns send-chain integration and recall service implementation.
- `chat-service-controller` owns RSocket recall mappings.
- `chat-webflux` owns REST recall mappings.
- `chat-index-lucene` stays unchanged.
- `chat-vector-simple` owns `SimpleVectorStore` configuration and tests.
- `chat-vector-redis` owns Redis `VectorStore` configuration and tests.

Vector values:

- `mock`: tests only.
- `simple`: local and integration tests.
- `redis`: shared runtime.

Embedding values:

- `mock`: tests only.

Reserved embedding value:

- `local`: reserved for a local embedding model.
- `gateway`: reserved for AgentGateway embedding integration.

Do not implement `local` in this sprint.

Do not implement `gateway` in this sprint.

`docs/Project_Gateway.md` does not define an embedding endpoint.

Legal matrix:

| vector | embedding | Status |
|---|---|---|
| `mock` | `mock` | unit tests only |
| `simple` | `mock` | local integration tests |
| `redis` | `mock` | shared runtime integration tests |
| `redis` | `gateway` | reserved future pair |

The reserved future pair fails at startup in this sprint.

All other pairs fail at startup.

This sprint implements:

- `mock` embedding for unit tests.
- `simple` vector store for local integration tests.
- `redis` vector store for shared backend tests.

This sprint does not add SQLite.

It does not add a SQLite adapter.

It does not add a local durable vector store.

## Send Flow

If recall is inactive, the current send chain remains unchanged.

If recall is active, message send uses ordered write-through.

Build the message once.

Then run writes in this order:

```kotlin
Flux.concat(
    messagePersistence.add(message),
    messageIndex.add(message),
    messageVectorIndexer.add(message),
    pubsub.sendMessage(message)
)
```

Persistence runs first.

Normal index runs second.

Vector index runs third.

Pubsub runs last.

If vector indexing fails, `send` fails.

The service does not publish the message after vector failure.

The service does not remove earlier successful writes.

If `message.record == false`, vector indexing returns success without a write.

This matches the current backend fanout behavior.

Follow-up issue `CHAT-ruduojeu` records backend fanout semantics.

That issue must cover partial writes, retry, compensation, and repair.

## Recall Flow

Recall does not load full messages.

It returns keys and scores only.

Recall covers only messages sent while recall is active.

This sprint does not backfill older messages.

Threshold mapping:

- `threshold = 0.0` maps to `SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL`.
- `threshold > 0.0` maps to `similarityThreshold(threshold)`.

Flow:

1. Validate the request.
2. Build a `SearchRequest`.
3. Add the metadata filter.
4. Call `VectorStoreRetriever.similaritySearch(request)`.
5. Create `MessageKey` from `messageId`, `userId`, and `topicId` metadata.
6. Return keys and scores.

## API Surface

RSocket routes:

- `message-recall-topic`
- `message-recall-user`
- `message-recall-global`

REST routes:

- `POST /message/recall/topic`
- `POST /message/recall/user`
- `POST /message/recall/global`

REST and RSocket use the same request and response DTOs.

RSocket recall controllers use `@ConditionalOnProperty(prefix = "app.controller", name = ["recall"])`.

REST recall controllers use `@ConditionalOnProperty(prefix = "app.controller", name = ["recall"])`.

No shell commands are in this sprint.

Access control:

- This sprint follows the current controller access pattern.
- It does not add new authorization rules.
- Global recall can expose all indexed message keys to an enabled caller.
- Record that risk in the implementation plan.

Validation errors use `InvalidRecallRequestException`.

The exception extends `ChatException`.

## Backend Plan

Use mock `VectorStore` for service tests.

Use `SimpleVectorStore` for local integration tests.

Use Redis `VectorStore` for shared runtime integration tests.

Redis tests require Redis Stack.

Redis must initialize its vector schema explicitly.

Redis isolation:

- Redis index name is `chat:vector:<keyType>:message`.
- Redis key prefix is `chat:vector:<keyType>:message:`.
- Redis filters also include `keyType`.

The metadata field alone does not isolate Redis indexes.

Do not use Redis as proof for `SimpleVectorStore` behavior.

Do not use `SimpleVectorStore` as proof for shared runtime behavior.

## Test Plan

Mapper tests:

- Message becomes one `Document`.
- `Document.id` uses `message:<keyType>:<messageId>`.
- `Document.text` is message data.
- Metadata contains `kind`, `messageId`, `topicId`, `userId`, and `keyType`.

Request tests:

- Blank `query` fails.
- `limit` defaults to `10`.
- `limit` below `1` fails.
- `limit` above `50` fails.
- `threshold` defaults to `0.0`.
- `threshold` outside `0.0..1.0` fails.

Service tests:

- `send` calls persistence, normal index, vector index, then pubsub.
- Vector failure stops pubsub.
- Vector failure returns an error.
- `record=false` skips vector storage.
- Topic recall builds a topic filter.
- User recall builds a user filter.
- Global recall builds a global filter.
- Recall returns keys and scores only.
- One selector set without the other fails startup.
- Both selectors unset leaves recall inactive.
- An illegal vector and embedding pair fails startup.
- Spring AI calls occur on `Schedulers.boundedElastic()`.

Integration tests:

- The pinned Spring AI API probe compiles.
- `SimpleVectorStore` proves Spring AI behavior without containers.
- Redis vector store proves shared runtime behavior.
- RSocket mappings use the shared DTOs.
- REST mappings use the shared DTOs.
- Build health runs in default mode.
- Build health runs in integration mode.

## Out Of Scope

- Topic vectors.
- User vectors.
- Summary vectors.
- Graph storage.
- SQLite vector storage.
- SQLite adapter work.
- Shell recall commands.
- Backend fanout redesign.
- Message repair or reindex jobs.

## Open Follow-Up

Issue `CHAT-ruduojeu` records backend fanout semantics.

The issue must cover:

- Partial writes.
- Retry.
- Compensation.
- Repair.

Issue `CHAT-oghjsnad` records the vector reindex path. It is scheduled for a
following sprint.

This spec lists message repair and reindex jobs under Out Of Scope. That stays
true for this sprint. The embedded provider added later made the gap concrete:
the store is a derived cache on ephemeral storage, so a lost storage directory
must be rebuilt from the persisted messages.

No rebuild path exists today. `MessageVectorIndexer` declares `add` and `remove`
only. A lost index makes recall return fewer hits. It does not throw and it does
not warn, so a caller cannot tell an empty result from a lost index.

This is safe while recall stays test only. It is not safe once recall serves
users. Check `CHAT-oghjsnad` against `CHAT-ruduojeu` before starting, because
that issue already names repair.

# Message Vector Recall

Status: design approved in conversation on 2026-09-01.

## Purpose

Demo Chat will become a substrate for agentic workflows.

Agents need semantic recall over prior messages.

This sprint adds message vector recall only.

It does not add topic vectors, user vectors, summary vectors, or graph storage.

## Source APIs

Use Spring AI `VectorStore` as the vector abstraction.

Use Spring AI `VectorStoreRetriever` for read-only recall.

Use Spring AI `SearchRequest` for query text, result limit, threshold, and metadata filters.

Use Spring AI `Document` as the stored vector document.

`Document.id` is the message key string.

`Document.text` is the message data.

`Document.metadata` stores message scope.

`Document.score` is the recall score.

Spring AI `VectorStore` supports these operations:

- `add(List<Document>)`
- `delete(List<String>)`
- `delete(Filter.Expression)`
- `similaritySearch(SearchRequest)`
- `similaritySearch(String)`

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
    val key: Key<T>,
    val score: Double?
)
```

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
- `limit` must be at most `50`.
- `threshold` defaults to `0.0`.
- `threshold` must be in `0.0..1.0`.

## Document Shape

Store one Spring AI `Document` per persisted message.

Metadata fields:

- `kind = "message"`
- `messageId = message.key.id`
- `topicId = message.key.dest`
- `userId = message.key.from`
- `keyType = long | uuid`

Filters:

- Topic recall: `kind == 'message' && topicId == '<id>'`
- User recall: `kind == 'message' && userId == '<id>'`
- Global recall: `kind == 'message'`

## Activation

Add two capabilities:

- `app.service.core.vector`
- `app.service.core.embedding`

Normal chat does not require either capability.

Recall activates only when both capabilities are set.

Vector values:

- `mock`: tests only.
- `simple`: local and integration tests.
- `redis`: shared runtime.

Embedding values:

- `mock`: tests only.
- `local`: local model or deterministic test model.
- `gateway`: AgentGateway embedding model, when that path exists.

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

This matches the current backend fanout behavior.

Follow-up issue `CHAT-ruduojeu` records backend fanout semantics.

That issue must cover partial writes, retry, compensation, and repair.

## Recall Flow

Recall does not load full messages.

It returns keys and scores only.

Flow:

1. Validate the request.
2. Build a `SearchRequest`.
3. Add the metadata filter.
4. Call `VectorStoreRetriever.similaritySearch(request)`.
5. Map each `Document` to `MessageRecallHit`.
6. Return keys and scores.

## API Surface

RSocket routes:

- `message.recall.topic`
- `message.recall.user`
- `message.recall.global`

REST routes:

- `POST /message/recall/topic`
- `POST /message/recall/user`
- `POST /message/recall/global`

REST and RSocket use the same request and response DTOs.

No shell commands are in this sprint.

## Backend Plan

Use mock `VectorStore` for service tests.

Use `SimpleVectorStore` for local integration tests.

Use Redis `VectorStore` for shared runtime integration tests.

Redis tests require Redis Stack.

Redis must initialize its vector schema explicitly.

Do not use Redis as proof for `SimpleVectorStore` behavior.

Do not use `SimpleVectorStore` as proof for shared runtime behavior.

## Test Plan

Mapper tests:

- Message becomes one `Document`.
- `Document.id` is the message key string.
- `Document.text` is message data.
- Metadata contains `kind`, `messageId`, `topicId`, `userId`, and `keyType`.

Request tests:

- Blank `query` fails.
- `limit` defaults to `10`.
- `limit` above `50` fails.
- `threshold` defaults to `0.0`.
- `threshold` outside `0.0..1.0` fails.

Service tests:

- `send` calls persistence, normal index, vector index, then pubsub.
- Vector failure stops pubsub.
- Vector failure returns an error.
- Topic recall builds a topic filter.
- User recall builds a user filter.
- Global recall builds a global filter.
- Recall returns keys and scores only.

Integration tests:

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

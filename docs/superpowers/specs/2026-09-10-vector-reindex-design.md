# Message Vector Reindex

Status: design approved through FP brainstorm
`svmdsmyuitzrwjurxcuyjqmrkfiftqvp` on 2026-09-10.

Issue: `CHAT-oghjsnad`.

Related issue: `CHAT-ruduojeu`.

## Purpose

Add an operator-controlled rebuild path for the message vector index.

Persisted messages remain the source of truth.

The embedded vector store remains a derived cache.

Recall results must report whether the process completed one full rebuild.

Recall must return available hits when the index is incomplete.

## Existing Baseline

`MessagePersistence.all()` streams all persisted messages.

Every current persistence provider implements this method.

`MessageDocumentMapper` creates the stable vector document identifier and metadata.

The identifier is `message:<keyType>:<messageId>`.

`MessageVectorIndexer.add()` already ignores messages with `record == false`.

`VectorStoreMessageVectorIndexer` moves blocking Spring AI calls to
`Schedulers.boundedElastic()`.

`MessageRecallServiceImpl` returns a stream of hits without a completeness signal.

The current REST routes produce NDJSON.

The current RSocket routes produce response streams.

No RSocket client proxy calls the recall routes.

The `chat-shell` module does not call recall.

Spring AI `VectorStore` has no count operation.

The process cannot verify a durable completeness mark against stored vectors.

## Selected Approach

Use an explicit operator trigger.

Expose the trigger through a custom Spring Boot actuator endpoint.

Rebuild all recorded messages in one operation.

Do not add topic or time filters.

Keep completeness state inside the process.

A fresh process starts with an incomplete index.

Only a successful full rebuild marks the index complete.

Recall returns available hits with the current completeness flag.

## Rejected Triggers

Do not rebuild automatically during startup.

Real embedding throughput is unknown.

A startup rebuild can delay readiness or create uncontrolled external requests.

Do not rebuild after the first empty recall result.

An empty result is valid and cannot prove index loss.

Concurrent callers could also start duplicate work.

## Completeness Meaning

Complete means this process indexed every recorded message returned by one full persistence scan.

Complete does not mean the vector store has no stale documents.

The current `VectorStore` interface cannot enumerate or count all stored documents.

This issue guarantees coverage of persisted recorded messages.

It does not guarantee removal of unknown stale documents.

A successful live add does not change an incomplete index to complete.

A failed live add changes a complete index to incomplete.

The incomplete state remains until a full rebuild succeeds.

Do not add a separate degraded state.

One missing vector makes the index incomplete by definition.

## Recall Contract

Add this result type in `chat-core`:

```kotlin
data class MessageRecallResult<T>(
    val indexComplete: Boolean,
    val hits: List<MessageRecallHit<T>>,
)
```

Change all three `MessageRecallService` methods to return
`Mono<MessageRecallResult<T>>`.

The methods remain:

- `recallInTopic`
- `recallByUser`
- `recallGlobal`

Each implementation performs the existing vector search.

Each implementation collects the bounded hit stream into one list.

`RecallRequestValidation` keeps the maximum result count at 50.

The service reads the completeness flag after the search completes.

The returned flag describes the state at result creation.

Recall does not wait for a running rebuild.

Recall does not fail because the index is incomplete.

An empty hit list with `indexComplete=false` can mean missing index coverage.

An empty hit list with `indexComplete=true` is a complete search result.

## Transport Changes

The three REST routes return one JSON object.

They no longer produce NDJSON.

The three RSocket routes become request-response routes.

No recall client proxy requires a change.

Controller tests must pin the new object shape and empty-hit flag.

## State Model

Add a typed phase with these values:

- `INCOMPLETE`
- `REBUILDING`
- `COMPLETE`

Add `VectorIndexStatus` in `chat-core`.

The status exposes these values:

- Current phase.
- Derived `complete` value.
- Derived `running` value.
- Last rebuild report.
- Last successful rebuild time.
- Last successful rebuild count.
- Last failure summary.

Add `VectorRebuildReport` in `chat-core`.

The report exposes these values:

- Start time.
- Completion time.
- Attempted message count.
- Indexed message count.
- Skipped message count.
- Failed message count.

The last rebuild report records the most recent finished attempt.

The last successful fields remain null before the first successful rebuild.

A failed attempt does not erase the last successful fields.

A failed attempt still replaces the last rebuild report.

A successful rebuild clears the last failure summary.

The failure summary contains no stack trace.

The application log receives the full error.

Add a `VectorIndexState` interface in `chat-core`.

The interface owns state transitions and one-job exclusion.

Add `InMemoryVectorIndexState` in `chat-service-composite`.

The implementation uses one atomic state value.

## State Transitions

A new state starts as `INCOMPLETE`.

An accepted operator trigger changes the state to `REBUILDING`.

A second trigger during that phase returns the current busy status.

The second trigger starts no work.

A rebuild with zero failures changes the state to `COMPLETE`.

A rebuild with one or more failures changes the state to `INCOMPLETE`.

A persistence scan failure changes the state to `INCOMPLETE`.

A failed live add changes `COMPLETE` to `INCOMPLETE`.

A failed live add keeps `INCOMPLETE` unchanged.

## Live-Traffic Race

The reindex service captures an invalidation generation when the rebuild starts.

Each failed live add advances that generation.

The rebuild can mark complete only when the generation stays unchanged.

This rule prevents a false complete state during live traffic.

A live message can enter persistence after the rebuild scan passes its position.

If its vector add fails, the changed generation keeps the state incomplete.

This rule can produce one safe false negative.

A live add can fail before the rebuild scan reaches the same message.

The rebuild can then index that message successfully.

The changed generation still keeps the state incomplete.

Accept this conservative result.

A later operator rebuild can mark the index complete.

The design prohibits a false complete result.

Repeated successful writes create no duplicate vector document.

The document identifier is deterministic.

## Reindex Service

Add `MessageReindexService<T>` in `chat-core`.

The public methods are:

```kotlin
fun start(): Mono<VectorIndexStatus>
fun status(): VectorIndexStatus
```

`start()` claims the one-job state before it schedules work.

It returns the running status without waiting for completion.

The job reads `MessagePersistence<T, V>.all()`.

It casts each recorded message to `Message<T, String>` before indexing.

Use one private `asText` function with `@Suppress("UNCHECKED_CAST")`.

This function matches the existing bridge in `MessagingServiceImpl`.

Every current composition binds `V` to `String`.

A composition with another value type must not set the recall selectors.

The job processes messages with `concatMap`.

The job writes one message at a time through `MessageVectorIndexer.add()`.

The existing indexer filters messages with `record == false`.

The reindex service counts attempted, successful, skipped, and failed messages.

One message failure does not stop the remaining messages.

The job records the last message failure summary.

A persistence stream failure stops the scan.

The service marks the final state after the stream terminates.

The final state stores the finished rebuild report.

The first implementation does not batch vector writes.

## Indexer Integration

Inject `VectorIndexState` into `VectorStoreMessageVectorIndexer`.

Keep the current add and remove signatures.

On an add failure, mark the index incomplete before the error continues.

The send operation still receives the original vector error.

The existing send failure semantics do not change.

Do not add retry, compensation, or message repair here.

Issue `CHAT-ruduojeu` owns those general fanout decisions.

## Actuator Endpoint

Add `VectorIndexEndpoint` in `chat-deploy`.

Use actuator identifier `vectorindex`.

The read operation returns `MessageReindexService.status()`.

The write operation calls `MessageReindexService.start()`.

The write returns while the rebuild runs.

A busy write returns `running=true` and starts no job.

Gate the endpoint with both existing property conditions.

Require `app.service.composite`.

Require both `app.service.core.vector` and `app.service.core.embedding`.

A deployment without active recall wiring exposes no endpoint bean.

`ActuatorWebSecurityConfiguration` protects the endpoint with the existing
`ACTUATOR` role.

Operators must expose `vectorindex` through the normal actuator exposure property.

This issue adds no deployment selector or exposure value.

## Bean Wiring

`VectorRecallServiceConfiguration` creates one `VectorIndexState` bean.

The configuration injects that state into recall, indexing, and reindex services.

The configuration receives `PersistenceServiceBeans<T, V>`.

It obtains message persistence through `persistenceBeans.messagePersistence()`.

The composite context does not expose `MessagePersistence<T, V>` as a bean.

The reindex service receives the returned `MessagePersistence<T, V>` instance.

The reindex service owns the unchecked text-message cast.

The existing vector and embedding selector conditions remain authoritative.

Do not create reindex beans when recall wiring is inactive.

## Failure Reporting

An individual vector failure increments the failed count.

The job continues with the next message.

Any failed count prevents the complete state.

A persistence failure prevents the complete state.

An unexpected job failure prevents the complete state.

The status stores a concise summary of the last failure.

The log records the complete exception and rebuild counts.

The actuator response never reports complete after a failed attempt.

## Verification

### State tests

- A fresh state reports incomplete and idle.
- One start claim changes the state to rebuilding.
- A second claim reports busy.
- Zero failures can mark the state complete.
- One failure keeps the state incomplete.
- A live invalidation prevents a concurrent rebuild from marking complete.

### Reindex service tests

- A rebuild reads every persisted message.
- A rebuild indexes every recorded message.
- A rebuild skips each unrecorded message.
- A message failure does not stop later messages.
- A failed message count prevents completion.
- A persistence failure prevents completion.
- A successful rebuild records its completion time and count.

### Recall tests

- Each recall method returns one `MessageRecallResult`.
- The result contains the existing hits.
- An empty result carries `indexComplete=false` before a rebuild.
- An empty result carries `indexComplete=true` after a successful rebuild.
- Existing filter, limit, threshold, and scheduler behavior remains unchanged.

### Controller tests

- REST returns one JSON object instead of NDJSON.
- RSocket returns one response instead of a stream.
- Both transports include the completeness flag when hits are empty.

### Actuator tests

- The read operation returns current status.
- The write operation starts one background job.
- A second write returns busy.
- The endpoint is absent without the reindex bean.
- Existing actuator authentication protects the endpoint.

### Lost-storage test

Use one persistence instance across two embedded vector-store lifecycles.

Persist recorded messages outside the first vector-store lifecycle.

Close the first vector store and collection.

Delete the embedded storage directory.

Create a second vector store on the same configured path.

Run the operator rebuild against the retained persistence instance.

Assert that recall finds the persisted messages.

Assert that the recall result reports `indexComplete=true`.

Do not delete an active memory-mapped directory.

## Documentation

Update the prior recall design with a short supersession note.

Remove the stale no-backfill claim from `MessageRecallServiceImpl`.

Update the forward register after implementation evidence exists.

Review drift bindings before any provenance refresh.

## Out Of Scope

- Automatic startup rebuilds.
- Lazy rebuilds.
- Topic rebuilds.
- Time-window rebuilds.
- Batch vector writes.
- A real embedding provider.
- Deployment selector changes.
- Deployment actuator exposure changes.
- Durable completeness state.
- Stale vector document removal.
- General fanout retry or compensation.

## Acceptance

A protected operator action starts one full message reindex job.

The job reads persistence and uses the existing mapper and indexer.

The job marks complete only after zero failures and no concurrent invalidation.

Recall returns hits and a completeness flag.

The flag distinguishes incomplete empty results from complete empty results.

The lost-storage test proves recall recovery after one successful rebuild.

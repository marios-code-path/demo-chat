# Vector Index Job Record

Status: proposed. The owner approved the durable invalidation separation. The
revised document waits for review.

Issue: `CHAT-fpwpfrfj`.

Prior design: `docs/superpowers/specs/2026-09-10-vector-reindex-design.md`.

## The Gap

`VectorIndexStatus.complete` currently reads `phase == COMPLETE`.

`claim()` moves `COMPLETE` to `REBUILDING`.

Therefore, a repair rebuild makes every recall report `indexComplete=false`.

Recall must report current coverage. It must not report the active job phase.

## Design Summary

Store each `IndexJob` in the existing key-value store.

Give each job one topic. Use the topic key as the job root key.

Publish `JobRecord` messages on that topic. The `record` Boolean carries no
handling policy for this path.

Keep the active claim in process. Durable job state never acts as a lock.

Keep the current invalidation target in the same in-process state. Never find
that target through a latest-job query.

A policy reads finished jobs and answers two independent questions:

1. What startup action must the process take?
2. What coverage value must recall report?

## Root Keys

`IndexJob<T>` and `JobRecord<T>` are top-level domain records. Each record must
implement `KeyBearer<T>` and hold its own `Key<T>` root key.

The key-value pair that stores an `IndexJob` must use `IndexJob.key`.

A read of a stored job must compare the two keys. The read fails when the
decoded root key differs from the key it was stored under. Only the read holds
both keys. A caller that accepts a wrong key acts on another job.

A `JobRecord` message must use `JobRecord.key.id` as its message id. The message
key also carries the worker and job relationships.

All relationship fields use `Key<T>`. They do not use an unwrapped `T` value.

## IndexJob

Add `IndexJob<T>` in `chat-core`.

The record holds these values:

- Root key.
- Node id. The value of `app.nodeid`.
- Key type. The value of `app.key.type`.
- Incarnation id. One random value per process start.
- `startedBy`. A root key that identifies the worker incarnation.
- Start time and optional end time.
- Outcome. One of `RUNNING`, `SUCCEEDED`, `FAILED`, or `RELEASED`.
- Attempted, indexed, skipped, and failed counts.
- Failure summary, or null.
- Invalidation count.
- Last invalidation time, or null.

The worker key identifies the entity that performs the job. One process
incarnation uses one worker key for all its jobs.

The job record is durable evidence. A `RUNNING` job from an earlier incarnation
does not block a new claim.

## JobRecord

Add `JobRecord<T>` in `chat-core`.

The record holds these values:

- Root key.
- Job key.
- Worker key.
- Timestamp.
- Message.
- Error key, or null.
- Optional progress counts.

Current deployments bind message data to `String`. Encode `JobRecord` as a
versioned JSON string for publication. A consumer decodes it from message data.

The `JobRecord` root key remains inside the payload. Its id also becomes the
message id.

## Job Topic And Discovery

Create one persisted topic before each rebuild. Use a reserved topic-name prefix.

The topic name includes the node id, key type, start time, and incarnation id.

The job service creates this topic with this sequence, in this order:

1. `TopicPersistence.add()`.
2. `TopicIndexService.add()`.
3. `PubSubService.open()`.

The job service never calls `TopicServiceImpl.addRoom()`.

`PubSubService.open()` is required, not optional. The memory backend keeps its
pub/sub state in instance maps. It answers `sendMessage()` on a topic it never
opened with `Object not Found`. `CHAT-qonhhtuq` records that defect.

`addRoom()` performs the same three writes for a user room. The job service
repeats the sequence rather than calling that method.

`addRoom()` rejects the reserved prefix without a system-topic exception.

Use the generated topic key as `IndexJob.key`. Store the job with
`KeyValuePair.create(job.key, job)`.

The topic store becomes the job directory. A new process performs these steps:

1. List topics.
2. Select names with the reserved prefix and matching node id and key type.
3. Read each key-value pair with `get(topic.key)`.
4. Decode its data with the `IndexJob` codec.
5. Select the applicable job by start time and root key.

This flow never calls `typedAll` on mixed key-value data. Every read uses a known
job key.

The codec accepts the backend data shapes. Memory can return an `IndexJob`.
Redis can return a map. Cassandra can return a JSON string.

The RSocket key-value client does not transmit the requested class for
`typedGet`. The design does not depend on that method.

System job topics must not appear in user room lists. User room creation must
reject the reserved prefix.

The first version reads all matching job topics. Automatic job retention remains
out of scope.

## Job Lifecycle

The service creates the topic and then writes the `RUNNING` job.

The active rebuild uses the existing in-process atomic claim.

The service updates the same key-value pair when counts or terminal state change.
All current key-value backends overwrite an existing value with the same key.

A new job starts with `invalidationCount=0` and no invalidation time.

A successful job can become the in-process invalidation target.

A failed or released job cannot become the invalidation target.

A process crash can leave a durable `RUNNING` job. A new process treats that job
as historical evidence and can mark it `RELEASED` on a best-effort basis.

Startup discovery performs this release sweep before it applies either trust
policy.

## Job Record Write Path

The job service creates a `Message` for each encoded `JobRecord`.

The job service uses one composed writer with this order:

1. `MessagePersistence.add()`.
2. `MessageIndexService.add()`.
3. `PubSubService.sendMessage()`.

A failed step stops later steps without rollback. Persistence can therefore hold
a record that the topic index cannot discover.

The composed writer never calls `MessagingServiceImpl.send()`.

That composite method also calls `MessageVectorIndexer.add()`. Job records must
not enter vector recall.

The writer stores every job record. It also sets `record=true` on each message.

The Boolean does not select writes and carries no advisory meaning here.

## Job Record Cadence

A run emits two events. It emits one when it creates its job, and one beside its
terminal write. The second names the outcome of the run.

Both events carry the job key, so both reach the job topic.

The record id comes from message persistence, like every other message id.
`PersistenceStore.key()` answers with a `Mono`, so the emitter reads it rather
than taking a synchronous supplier that no deployment could provide.

**A record is a report, and a failed record write never fails the run.** Losing
a report must not turn a healthy rebuild into a failed one. It must not stop the
scan, and it must not stop the terminal write.

Downstream clients still receive the unchanged Boolean.

The message index indexes each record by its job topic.

Readers query that index with `topicIdToQuery`. They resolve the returned keys
with `MessagePersistence.byIds()`.

Coverage never depends on job-message retention.

A future handling-policy mask can replace the Boolean. Possible flags include
`SAVE_VECTOR`, `SAVE_PERSIST`, and `FORWARD`. That change remains out of scope.

## Topic Listing And Scan Exclusion

The rebuild creates its job topic before it prepares the scan.

The rebuild then calls `TopicPersistence.all()` once. That result serves job
discovery and scan exclusion.

The exclusion set contains every reserved job topic from that result. It also
contains the current job topic explicitly.

A failed topic listing fails the run. The rebuild never scans without the full
exclusion set.

The message scan still reads the full result from `MessagePersistence.all()`.

The scan rejects messages whose destination is in the exclusion set.

The exclusion filter runs before all attempted, indexed, skipped, and failed
counters.

The scan does not use `Message.record` as an inclusion rule.

`TopicServiceImpl.listRooms()` filters reserved job topics from user results.

User room creation rejects the same reserved prefix.

## Durable Invalidation

A live vector add failure removes coverage after a successful job.

The process generation remains a process-local race token. It never participates
in durable coverage comparison.

This token detects a live add failure after the scan passes that message.

The in-process state holds the exact covering job key as its invalidation target.

`invalidate()` atomically increments the process generation and captures that
target key from the same state transition.

`invalidate()` updates only the captured job. It never queries for the latest
successful job.

`finish()` performs its generation check through the same atomic state.

If `invalidate()` wins, `finish()` observes the changed generation. The current
job does not become the target.

If `finish()` wins, it installs the current job as the target. The later
invalidation updates that job.

The job writer preserves transition order for one job. A terminal success write
must not overwrite a later non-zero invalidation.

The update increments `invalidationCount` and sets the last invalidation time.

The count acts as a flag for coverage. Its numeric value is advisory and is not
an audit total.

Concurrent updates can undercount. Any non-zero value still removes coverage.

`MessagingServiceImpl.send()` persists a user message before vector indexing.

Therefore, a later successful full rebuild can restore that message and start a
new job with zero invalidations.

The process can also publish an error `JobRecord` on that job topic. The emission
uses the composed job writer.

A failed job update never fails the original message send. The process stays
incomplete until a successful rebuild.

The process can stop after the generation increment but before the durable write.

A restart can then lose that invalidation. The `stored` trust policy accepts
this risk explicitly.

## Policy

Add two properties. Both properties have safe defaults.

`app.vector.index.trust` controls whether an earlier job counts as coverage.

- `none` is the default. Only a successful job from this incarnation counts.
- `stored` trusts a successful job from the same node id and key type.

`app.vector.index.startup` controls startup action.

- `report` is the default. The process reports state and takes no action.
- `rebuild` starts one rebuild after the application is ready.

The trust policy never starts a rebuild. The startup policy never changes the
reported coverage.

`stored` is an operator assertion. The vector and key-value stores must survive
the same restart.

`VectorStore` cannot count documents. The process cannot verify stored coverage.

The operator also accepts the failed-update window for durable invalidations.

`rebuild` remains off by default. Real embedding throughput remains unmeasured.

## Coverage Rule

Recall reports coverage, not job phase.

The policy selects the newest applicable successful job.

Two applicable jobs can hold one start instant. The policy then selects the
higher root key. The policy compares the key through `TypeUtil.compare`, in the
key type. A text compare puts "9" above "10", which is the wrong order.

`indexComplete` is true when that job has `invalidationCount == 0`.

The policy does not compare a stored value with the process generation.

Under `trust=none`, the covering job must have the current incarnation id.

Under `trust=stored`, the covering job can have an earlier incarnation id.

The selected job becomes the in-process invalidation target.

The policy never falls back to an older successful job after invalidating the
newest applicable successful job.

A new rebuild does not remove an existing covering job. Therefore, a repair
rebuild keeps `indexComplete=true` while it runs.

A failed repair also keeps prior coverage when no invalidation occurred.

A first rebuild reports incomplete until it succeeds.

## Status Changes

`VectorIndexStatus` keeps the phase, running flag, and in-process report.

`complete` no longer reads the phase. It reads the covering job.

The status gains the active job and covering job. Either value can be null.

Both new fields hold a key, so the status carries the key type.

### The active job lifecycle

The active job names the job of the run that holds the claim.

The reindex service sets it through `markActiveJob(jobKey)`.

The service calls that method after the job store creates the job.

The service calls that method before the first job record write.

`markActiveJob` writes the value only while the state reports running.

A call outside a running state changes nothing.

The claim generation cannot identify a run. An invalidation raises the
generation during the same run.

The running flag is the only guard. One process runs at most one rebuild.

Every finish path clears the active job. Every release path clears it too.

A state with no running rebuild reports a null active job.

The active job never decides coverage. The covering job alone decides coverage.

### The actuator contract

The actuator read operation returns the status and the recent jobs.

It returns jobs, not job records. `IndexJob` holds the outcome, the four counts,
the failure summary, the timestamps, and the invalidation count. A `JobRecord`
adds no operator value beside that, and the records stay readable on the job
topic.

The read returns at most 50 jobs. That bound is the recall limit cap, at
`RequestResponse.kt`.

It sorts by start instant, then by the root key, both descending. It compares
the key through `TypeUtil.compare`, in the key type, for the same reason the
coverage rule does.

It selects the jobs of this node and this key type. It filters the topic names
first, and it then validates the identity of each decoded record.

An operator must set two properties to reach the endpoint. The deployments
disable every actuator endpoint by default, so exposure alone is not enough.

- `management.endpoint.vectorindex.enabled=true`
- `management.endpoints.web.exposure.include=vectorindex`

No deployment in this repository sets either value.

The write operation returns the claim snapshot. An accepted first trigger
reports `running=true` and `activeJob=null`. **It never promises the job key.**
The run creates its job after the claim, and on another scheduler.

A client polls the read operation until the active job is not null, or until
running is false. An immediate second read does not close that race.

### Vector Write Mode

The document id of a message is derived from the message id. So a rebuild
always meets an id it wrote before.

Only the embedded provider refuses a repeat. It answers a second write of one
id with `Duplicate id`. That provider removes the document and then writes it.

The mock, simple, and redis providers overwrite. They write once. A removal
before the write would be wasted work, and `RedisVectorStore` logs an error
when a delete removes no document.

The remove and write pair is not atomic. Recall can miss the message between
the two calls.

**The index still reports complete during that window.** A repair keeps the
coverage of the older successful job, so a caller can read
`indexComplete=true` and miss one message. This is an accepted transient false
positive. The window is one store call wide.

A removal failure stops the write. It promises nothing about the old document,
because a provider can remove the document and then fail while it commits.
Three things hold. The write does not run. The caller receives the removal
error. The failure removes coverage.

## Failure Behavior

A topic-list failure at startup reports no covering job.

A malformed matching job record makes the stored-policy read fail closed.

An orphan job topic without a key-value record does not count as coverage.

A job write failure logs an error. The in-process state keeps the rebuild result.

The index remains usable. A record is evidence and never acts as a lock.

## Verification

- `IndexJob` has its own root key.
- `JobRecord` has its own root key.
- The job topic key equals the `IndexJob` root key.
- A read of a record whose root key differs from its storage key fails.
- A known topic key supports an `IndexJob` decode on each backend.
- The same decode works through the RSocket key-value client.
- A rebuild of a complete index keeps `indexComplete=true`.
- A failed repair keeps prior coverage when no invalidation occurs.
- A successful job with zero invalidations provides coverage.
- A later invalidation removes stored coverage.
- Coverage never compares stored and process-local generations.
- `invalidate()` updates the target held by the atomic in-process state.
- An invalidation that wins the finish race prevents the new job from covering.
- An invalidation after finish updates the new covering job.
- A terminal job write never overwrites a later invalidation.
- A stale `RUNNING` job never rejects a new claim.
- The status reports the active job while a rebuild runs.
- Every finish path and every release path clears the active job.
- A `markActiveJob` call outside a running state changes nothing.
- The actuator read returns at most 50 jobs of this node and key type.
- The actuator read sorts jobs by instant, then by the typed root key.
- The actuator read skips a job whose record disagrees with its topic name.
- The actuator write reports a null active job on an accepted trigger.
- The actuator read runs no store call before a subscriber arrives.
- The embedded provider removes a document before it writes that id.
- The mock, simple, and redis providers write one document once.
- A removal failure stops the write and removes coverage.
- The job writer calls persistence, the message index, and pub/sub in order.
- A failed job-record write stops later writes and keeps earlier writes.
- The job writer never calls `MessagingServiceImpl.send()`.
- A subscriber receives the unchanged `record` Boolean.
- Job records can be read with `topicIdToQuery` and `byIds()`.
- Job messages do not enter vector recall.
- One topic listing serves discovery and scan exclusion during a rebuild.
- The scan exclusion set contains the current job topic.
- A failed topic listing prevents the message scan.
- The topic filter runs before every rebuild counter.
- `listRooms()` excludes reserved job topics.
- The job service creates its topic through persistence, the topic index, and
  `PubSubService.open()`, in that order.
- A job record reaches a subscriber of the job topic, which proves the open call.
- A run emits one record when it starts and one when it finishes.
- A failed record write leaves the run successful and its job SUCCEEDED.
- The job service never calls `addRoom()`.
- `addRoom()` rejects the reserved prefix.
- A topic-list or job-read failure reports incomplete under `stored`.
- Two jobs of one instant select the higher root key, compared as a key.

## Out Of Scope

- A durable completeness claim that no read can verify.
- Automatic rebuild as the default.
- Stale vector document removal.
- Cross-node coverage.
- Automatic job-topic retention.
- A handling-policy mask that replaces `record`.

## Decisions

1. Store `IndexJob` in the existing key-value store.
2. Give each job a persisted topic.
3. Use the topic key as the `IndexJob` root key.
4. Give `IndexJob` and `JobRecord` independent root keys.
5. Use job topics to discover known keys after restart.
6. Store and index every job record through the composed job writer.
7. Exclude `MessagingServiceImpl.send()` from the job-record path.
8. Keep the process generation separate from durable coverage evidence.
9. Hold the durable invalidation target in the atomic in-process state.
10. Treat any non-zero invalidation count as incomplete.
11. Treat the invalidation count as advisory, not as an audit total.
12. Use one topic listing for rebuild discovery and scan exclusion.
13. Include the current job topic in the exclusion set.
14. Fail the run when its topic listing fails.
15. Filter job messages before updating rebuild counters.
16. Keep the full persistence scan.
17. Give `record` no handling-policy meaning in the job-record path.
18. Use `none` as the default trust policy.
19. Create job topics through persistence and the topic index.
20. Keep system-topic exceptions out of `addRoom()`.
21. Stop later job-record writes after failure without rolling back earlier writes.
22. Open the job topic for pub/sub as the third step of job topic creation.
23. Guard `markActiveJob` with the running flag, not with the claim generation.

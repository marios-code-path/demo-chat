# Vector Index Job Record

Status: proposed. This document waits for owner review.

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

Publish `JobRecord` messages on that topic. The `record` Boolean recommends
whether a consumer should store each message.

Keep the active claim in process. Durable job state never acts as a lock.

A policy reads finished jobs and answers two independent questions:

1. What startup action must the process take?
2. What coverage value must recall report?

## Root Keys

`IndexJob<T>` and `JobRecord<T>` are top-level domain records. Each record must
implement `KeyBearer<T>` and hold its own `Key<T>` root key.

The key-value pair that stores an `IndexJob` must use `IndexJob.key`.

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
- Current invalidation generation.
- Covered generation, or null.
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

A successful job sets `coveredGeneration` to its final generation.

A failed or released job leaves `coveredGeneration` null.

A process crash can leave a durable `RUNNING` job. A new process treats that job
as historical evidence and can mark it `RELEASED` on a best-effort basis.

## Job Message Policy

`Message.record` is a retention recommendation. It does not prove persistence.

- `true` recommends storage.
- `false` recommends ephemeral handling.

The job publishes every `JobRecord` through `PubSubService.sendMessage()`.

The producer can use pub/sub alone when it does not want local persistence.
Downstream consumers can inspect `record` and choose whether to store the message.

The first policy recommends storage for terminal events and errors. It recommends
ephemeral handling for routine progress.

Stored job messages must not enter message vector recall. Coverage never depends
on job-message retention.

A future handling-policy mask can replace the Boolean. Possible flags include
`SAVE_VECTOR`, `SAVE_PERSIST`, and `FORWARD`. That change remains out of scope.

## Durable Invalidation

A live vector add failure removes coverage after a successful job.

The process increments the in-process generation first. It then updates the
latest covering `IndexJob` with the generation and invalidation time.

The process can also publish an error `JobRecord` on that job topic. The emission
policy recommends storage for this error.

A failed job update never fails the original message send. The process stays
incomplete until a successful rebuild.

A restart can lose an invalidation whose job update failed. The `stored` trust
policy accepts this risk explicitly.

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

`indexComplete` is true when one trusted successful job covers the current
generation.

Coverage requires `coveredGeneration == currentGeneration`.

Under `trust=none`, the covering job must have the current incarnation id.

Under `trust=stored`, the covering job can have an earlier incarnation id.

A new rebuild does not remove an existing covering job. Therefore, a repair
rebuild keeps `indexComplete=true` while it runs.

A failed repair also keeps prior coverage when no invalidation occurred.

A first rebuild reports incomplete until it succeeds.

## Status Changes

`VectorIndexStatus` keeps the phase, running flag, and in-process report.

`complete` no longer reads the phase. It reads the covering job.

The status gains the active job and covering job. Either value can be null.

The actuator read operation returns the status and recent job records.

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
- A known topic key supports an `IndexJob` decode on each backend.
- The same decode works through the RSocket key-value client.
- A rebuild of a complete index keeps `indexComplete=true`.
- A successful job covers its final generation.
- A later invalidation removes stored coverage.
- A stale `RUNNING` job never rejects a new claim.
- A `record=false` job message can use pub/sub without persistence.
- A subscriber receives the unchanged `record` recommendation.
- Job messages do not enter vector recall.
- A topic-list or job-read failure reports incomplete under `stored`.

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
6. Keep `record` as an advisory Boolean.
7. Use `none` as the default trust policy.

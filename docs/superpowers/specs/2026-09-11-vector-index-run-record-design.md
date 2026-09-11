# Vector Index Run Record

Status: proposed. This document waits for owner approval.

Issue: `CHAT-oghjsnad`. This design corrects a gap found in review of `93091856`.

Prior design: `docs/superpowers/specs/2026-09-10-vector-reindex-design.md`.

## The Gap

`VectorIndexStatus.complete` reads `phase == COMPLETE`.

`claim()` moves `COMPLETE` to `REBUILDING`.

So a rebuild of a healthy index makes every recall report `indexComplete=false`.

The operator endpoint invites periodic repair. Periodic repair then degrades the
reported flag for the length of each run.

The flag is the feature. A report that drops for an operator action is wrong.

## Why The Earlier Decision Changes

The prior design put durable completeness state out of scope.

The reason was correct. A durable mark can claim complete while the storage
directory is gone. Spring AI `VectorStore` has no count operation, so nothing can
verify that claim.

This design does not store a completeness claim.

It stores an evidence journal for finished runs and live invalidations.

A record of a past run is evidence. A policy reads the evidence and decides.

The policy owns two separate questions:

1. What action must the process take against the index?
2. What must recall report?

Evidence and policy stay separate. That separation is the point of this change.

## Durable Evidence

Add `VectorIndexEvidence` in `chat-core`.

Two record types implement this contract:

- `VectorIndexRunRecord` describes one finished rebuild.
- `VectorIndexInvalidationRecord` describes one live indexing failure.

The store appends both record types. It never stores `REBUILDING` as durable
state. The in-process claim remains the only active-job state.

This rule prevents a process crash from leaving a durable busy state. A new
process can always claim a new rebuild.

Every evidence record holds these values:

- Node id. The value of `app.nodeid`.
- Key type. The value of `app.key.type`.
- Incarnation id. One random value per process start.
- Invalidation generation.
- Event time.

The run record also holds these values:

- Started at, and finished at.
- Outcome. One of `SUCCEEDED`, `FAILED`, or `RELEASED`.
- Attempted, indexed, skipped, and failed counts.
- Failure summary, or null.

The invalidation record also holds the failure summary.

Each record must carry the node id. The embedded vector store keeps one index per
instance. A record without a node id would describe another instance's index.

Each record must carry the incarnation id. A reader uses it to separate this
process from an earlier process.

## Invalidation Must Be Durable

A live add failure changes coverage after a successful run.

A record that holds only the run would then claim coverage that the live failure
removed.

So the reindex service writes the invalidation generation into every run record.

A later live failure appends one invalidation record. The write is asynchronous
and never blocks the send path.

A failed record write never fails a send and never fails a rebuild.

The in-process state remains incomplete after a failed invalidation write. A
restart can lose that evidence. The `stored` policy accepts this risk explicitly.

## Storage

Add a dedicated `VectorIndexEvidenceStore` boundary. Do not use
`PersistenceServiceBeans.keyValuePersistence()`.

The existing key-value store cannot safely hold this journal. Its key space is
shared with user values. Its `typedAll` implementations convert every value to
the requested type instead of filtering by type.

The dedicated store uses its own key space. It also uses opaque record ids that
do not use the application key type.

Partition records by node id and key type. Order records by generation, event
time, and record id.

Append one record for each finished run and each live invalidation. Prune to the
newest `app.vector.index.history` records in one partition. The default is
10.

Pruning can remove an older successful run after many later failed runs. This
case produces a safe incomplete report. The design accepts this false negative.

Use an append journal with pruning. Do not overwrite one record per node. The
overwrite needs a stable application key, which can collide with generated keys.

Each persistence backend owns its durable representation. Redis uses a separate
prefix and ordered index. Cassandra uses a separate table and partition. The
memory provider uses an in-process list for tests and ephemeral deployments.

Expose this store through the persistence transport. A split deployment writes
the evidence beside the message store. This change expands the transport scope.

The store provides three operations:

- Append one evidence record.
- Read the newest records for one node id and key type.
- Prune old records after an append succeeds.

## Alternatives Considered

The shared key-value append design needs a typed full scan. That scan cannot
filter mixed value types on the current backends.

The shared key-value overwrite design needs a stable application key. That key
can collide with a generated user key.

The dedicated journal adds backend and transport work. It gives this state a
separate key space and a defined query boundary.

## Policy

Add two properties. Both have a safe default.

`app.vector.index.trust` decides whether a record from an earlier incarnation
counts as coverage.

- `none` is the default. Only a successful run of this process counts.
- `stored` trusts a successful record from an earlier incarnation of the same node
  id and key type, when no invalidation followed it.

`app.vector.index.startup` decides the action at startup.

- `report` is the default. The process reports the state and takes no action.
- `rebuild` starts one rebuild after the application is ready.

The trust policy never starts a rebuild. The startup policy never changes the
reported coverage. These decisions remain independent.

`stored` is an operator assertion. It is only true when the vector storage path is
durable and survives the restart. Nothing in the process can check it, because
`VectorStore` cannot count documents. The property documentation must say this.

The assertion also trusts the evidence store. It accepts the crash window before
an asynchronous invalidation write completes.

Operators must use `stored` only when the vector and evidence stores survive the
same restart. They must also accept the asynchronous-write risk.

`rebuild` remains off by default. Real embedding throughput is still unmeasured.

## The Report Rule

Recall reports coverage, not phase.

`indexComplete` is true when a successful run covers the current index, and no
invalidation followed that run.

Under `trust=none`, a covering run is a successful run of this incarnation.

Under `trust=stored`, a covering run is a successful run of this node id and key
type, from any incarnation.

A successful run covers generation `G`. An invalidation with a generation larger
than `G` removes that coverage.

At startup, the state reads the largest stored generation. New invalidations
continue from that value. Write completion order cannot change this comparison.

A rebuild in progress does not change the answer. So a periodic repair of a
healthy index no longer flips the flag. That closes the gap.

A first rebuild on a fresh process still reports incomplete until it succeeds.

## Status Changes

`VectorIndexStatus` keeps `phase`, `running`, and the in-process report.

`complete` no longer reads `phase`. It reads the covering run.

The status gains the covering run, or null.

The actuator read operation returns the status and the newest evidence records
for this node.

## Failure Of The Record Store

A read failure at startup reports no covering run. The process reports incomplete.

A write failure after a run logs an error. The in-process state keeps the result.

The index stays usable in both cases. The record is evidence, never a gate.

## Verification

- A rebuild of a complete index keeps `indexComplete` true for the whole run.
- A successful run appends one record with its identity and generation.
- A live indexing failure appends one invalidation record.
- A fresh incarnation with `trust=none` reports incomplete beside a successful record.
- A fresh incarnation with `trust=stored` reports complete beside that record.
- An invalidation after a successful record removes coverage under `trust=stored`.
- Reordered evidence writes do not change the generation comparison.
- A failed record write leaves the in-process result unchanged.
- A record read failure at startup reports incomplete.
- The prune keeps the newest records for one node and removes older records.
- The evidence store never creates a durable busy state.

## Out Of Scope

- Automatic rebuild as a default.
- A durable completeness claim that no read can verify.
- Stale vector document removal.
- Cross node coverage. One record describes one node id.
- A record for a partial run. Only a finished run writes a record.

## Decisions

1. Use an append journal with pruning in a dedicated evidence store.
2. Use `none` as the default trust policy.

The default matches the current ephemeral storage. No current deployment mounts
a durable vector volume.

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

It stores a record of what one rebuild run did.

A record of a past run is evidence. A policy reads the evidence and decides.

The policy owns two separate questions:

1. What action must the process take against the index?
2. What must recall report?

Evidence and policy stay separate. That separation is the point of this change.

## The Run Record

Add `VectorIndexRun` in `chat-core`.

The record holds these values:

- Node id. The value of `app.nodeid`.
- Key type. The value of `app.key.type`.
- Incarnation id. One random value per process start.
- Started at, and finished at.
- Outcome. One of `SUCCEEDED`, `FAILED`, or `RELEASED`.
- Attempted, indexed, skipped, and failed counts.
- Failure summary, or null.
- Invalidation count at the end of the run.
- Last invalidation time, or null.

The record must carry the node id. The embedded vector store keeps one index per
instance. A record without a node id would describe another instance's index.

The record must carry the incarnation id. A reader uses it to separate a run of
this process from a run of an earlier process.

## Invalidation Must Be Durable

A live add failure changes coverage after a successful run.

A record that holds only the run would then claim coverage that the live failure
removed.

So the reindex service writes the invalidation count and the last invalidation
time into the record at the end of every run.

A later live failure must also update the durable record. The service writes a
short update on each invalidation. The write is asynchronous and never blocks the
send path.

A failed record write never fails a send and never fails a rebuild.

## Storage

Use the existing key value store. Reach it through
`PersistenceServiceBeans.keyValuePersistence()`.

The composite configuration already receives `PersistenceServiceBeans`, so this
adds no new backend module and no new selector.

Write one record per finished run. Read with `typedAll(VectorIndexRun::class.java)`.

Filter on node id and key type. Select the newest record by `finishedAt`.

Prune to the newest `app.vector.index.run.history` records for this node. The
default is 10.

:warning: **Load-bearing and unverified.** The alternative is one record per node,
overwritten through a stable `Key<T>`. `PersistenceStore.add` accepts a caller
supplied key, so an overwrite looks possible. Nobody has run it. A stable
`Key<T>` for a node id can also collide with a generated id on some backends. The
plan must prove one of the two shapes before it writes the service.

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

`stored` is an operator assertion. It is only true when the vector storage path is
durable and survives the restart. Nothing in the process can check it, because
`VectorStore` cannot count documents. The property documentation must say this.

`rebuild` remains off by default. Real embedding throughput is still unmeasured.

## The Report Rule

Recall reports coverage, not phase.

`indexComplete` is true when a successful run covers the current index, and no
invalidation followed that run.

Under `trust=none`, a covering run is a successful run of this incarnation.

Under `trust=stored`, a covering run is a successful run of this node id and key
type, from any incarnation.

A rebuild in progress does not change the answer. So a periodic repair of a
healthy index no longer flips the flag. That closes the gap.

A first rebuild on a fresh process still reports incomplete until it succeeds.

## Status Changes

`VectorIndexStatus` keeps `phase`, `running`, and the in-process report.

`complete` no longer reads `phase`. It reads the covering run.

The status gains the covering run, or null.

The actuator read operation returns the status and the newest record for this node.

## Failure Of The Record Store

A read failure at startup reports no covering run. The process reports incomplete.

A write failure after a run logs an error. The in-process state keeps the result.

The index stays usable in both cases. The record is evidence, never a gate.

## Verification

- A rebuild of a complete index keeps `indexComplete` true for the whole run.
- A successful run writes one record with the node id, key type, and incarnation id.
- A fresh incarnation with `trust=none` reports incomplete beside a successful record.
- A fresh incarnation with `trust=stored` reports complete beside that record.
- An invalidation after a successful record removes coverage under `trust=stored`.
- A failed record write leaves the in-process result unchanged.
- A record read failure at startup reports incomplete.
- The prune keeps the newest records for one node and removes older records.

## Out Of Scope

- Automatic rebuild as a default.
- A durable completeness claim that no read can verify.
- Stale vector document removal.
- Cross node coverage. One record describes one node id.
- A record for a partial run. Only a finished run writes a record.

## Open Decisions

1. **Record shape.** Append with prune, or one record per node with overwrite. The
   recommendation is append with prune, because the overwrite path is unproven.
2. **Default trust.** `none` is the recommendation. It matches the current default
   of ephemeral storage. `stored` becomes useful only when a deployment mounts a
   durable volume, and no deployment does today.

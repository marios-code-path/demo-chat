# Vector Index Status Contract

## Purpose

Define what the in-process `running` and `complete` fields promise during a
rebuild. Keep the current ordering that prevents a failed durable write from
leaving a run active forever. Change the production trigger response so a
reader can distinguish an accepted rebuild from a rejected trigger.

## Contract

`running` reports in-process work. `running=false` means that the state decided
the run outcome. It does not prove that the durable terminal `IndexJob` write
finished.

`complete` reports in-process coverage. `complete=true` means that the state
holds a covering job. It does not prove that the durable job has a terminal
outcome. A failed terminal write can leave `complete=true` beside a durable
`RUNNING` job. Under `app.vector.index.trust=stored`, a restart can then report
no coverage even though the previous process reported complete.

The durable job outcome is authoritative after a restart. Terminal outcomes
include `SUCCEEDED`, `FAILED`, and `RELEASED`.

`SUCCEEDED` proves that the rebuild did its work. `FAILED` and `RELEASED` end a
wait, but they do not prove successful indexing.

## Reader behavior

`MessageReindexService.start()` returns `Mono<VectorIndexTriggerResult<T>>`.
`VectorIndexTriggerResult` carries `accepted` and `status`. The actuator POST
returns this object. The GET response keeps its current status and jobs shape.

Readers must handle a rejected trigger as a busy or no-op result. They must not
wait for a job when the trigger was rejected.

For an accepted trigger, tests, the operator guide, and the launch gate must
set an outer bound for the complete rebuild. The outer bound belongs to the
reader because rebuild duration depends on corpus size. It must exist, and it
must cover the running interval.

After a reader first observes `running=false`, it must apply an inner bound to
the durable completion interval. The inner bound covers the terminal record
event and the durable job write. A rebuild can run longer than this inner bound.

Within the two bounds, readers must wait for both conditions:

1. The in-process state reports `running=false`.
2. The newest job with `startedAt` at or after the local trigger timestamp has
   a terminal outcome.

If the outer bound expires while `running=true`, readers report that the run
did not finish. If the inner bound expires after `running=false`, readers report
that the run ended without a durable record. They must not hang.

`RELEASED` ends the wait. It does not satisfy a successful rebuild assertion.
The launch gate requires a `SUCCEEDED` job, hits, and `indexComplete`.

The trigger does not promise a job key. Readers correlate a new job by taking a
local timestamp before the trigger and requiring `startedAt` at or after it.
The guide must state that this method requires compatible clocks. Readers must
check trigger acceptance before using this correlation method.

## Proof

Add a deterministic test with a delayed `finishJob` publisher. The test must
show `running=false` while the durable job remains `RUNNING`, then show the
terminal outcome after the publisher completes.

Add coverage for a failed terminal write. The reader must stop at its bound and
report the missing durable record.

Add coverage for `RELEASED` as a terminal outcome.

Add KDoc on `VectorIndexStatus.running` and `VectorIndexStatus.complete` that
states both values describe in-process state, not durable job completion.

Add a wire test for the trigger result. It must expose `accepted` beside the
status and keep the GET response free of trigger-only fields.

Update the trigger example in `docs/VECTOR-RECALL-API.md` to show the new POST
response and both wait bounds.

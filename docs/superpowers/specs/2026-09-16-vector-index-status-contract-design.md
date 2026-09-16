# Vector Index Status Contract

## Purpose

Define what the in-process `running` and `complete` fields promise during a
rebuild. Keep the current ordering that prevents a failed durable write from
leaving a run active forever.

## Contract

`running` reports in-process work. `running=false` means that the state decided
the run outcome. It does not prove that the durable terminal `IndexJob` write
finished.

`complete` reports in-process coverage. `complete=true` means that the state
holds a covering job. It does not prove that the durable job has a terminal
outcome. A failed terminal write can leave `complete=true` beside a durable
`RUNNING` job. A restart can then report no coverage.

The durable job outcome is authoritative after a restart. Terminal outcomes
include `SUCCEEDED`, `FAILED`, and `RELEASED`.

## Reader behavior

Tests, the operator guide, and the launch gate must wait for both conditions:

1. The in-process state reports `running=false`.
2. A newly created job has a terminal outcome.

Readers must bound this wait. If the state ends without a terminal outcome,
they must report that the run ended without a durable record. They must not hang.

The trigger does not promise a job key. Readers correlate a new job by taking a
local timestamp before the trigger and requiring `startedAt` at or after it.
The guide must state that this method requires compatible clocks.

## Proof

Add a deterministic test with a delayed `finishJob` publisher. The test must
show `running=false` while the durable job remains `RUNNING`, then show the
terminal outcome after the publisher completes.

Add coverage for a failed terminal write. The reader must stop at its bound and
report the missing durable record.

Add coverage for `RELEASED` as a terminal outcome.


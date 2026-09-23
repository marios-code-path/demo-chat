# The clock that orders grants

`CHAT-zhjltbky` needs a total order over the grants of one principal, because
a subtractive grant removes every permission before it. This is the clock that
supplies it.

Written on 2026-09-23. The owner asked for a stable clock with an atomic
`tick`, and then for a vector clock.

## Why `key.id` is the wrong order

`AuthSummarizer` sorts by a comparator that reads `key.id`. Long keys come
from `SnowflakeGenerator`, which builds an id from a wall clock, node bits and
a sequence. So the order is a wall clock order.

Three faults follow.

1. **A wall clock moves.** Two nodes disagree by their skew, and one node
   disagrees with itself after a correction.
2. **A uuid key carries no order at all.** This repository runs both key
   types, so half of the compositions have no order to read.
3. **Identity carries ordering.** A key answers which row this is. Nothing
   says it must also answer when the row arrived.

## What this adds

Three types in `chat-core`, under `com.demo.chat.domain.clock`. **Nothing
stores a stamp yet.** `AuthMetadata` is unchanged.

| Type | What it does |
|---|---|
| `VectorClock` | Counts per node id. `tick`, `merge` and `relate`. |
| `ClockStamp` | One reading with the node that took it, and the order rule. |
| `NodeClock` | The clock of one process. `tick` is atomic. |

The index is `app.nodeid`. That value is validated in 0..1023, it has no
default, and the store side lease keeps it unique across deployments that
write to one store. **So the index of the clock is already unique**, and no
new identity is needed.

`NodeClock` never reads a wall clock.

## A vector clock answers causality, and not order

This is the property to hold on to.

`relate` answers `BEFORE`, `AFTER`, `EQUAL` or `CONCURRENT`. **Two grants
written at once on two nodes are concurrent**, and a vector clock reports that
rather than choosing between them. That is correct, and it is not enough for
the subtractive rule, which needs the word "before" to be defined for every
pair.

So `ClockStamp.ORDER` adds one rule. Causality decides first. **Two concurrent
stamps are ordered by the node that wrote them.** A node id is unique, so the
answer is the same for every reader.

**The origin rule is a choice, not a law.** It lives in one place so that a
different choice is one edit.

## What a reader must do

1. Read a stamp from the store.
2. Call `NodeClock.observe` with its clock.
3. Tick when it writes.

Without step 2 a later write does not follow what this process has read, and
two rows that should be ordered report as concurrent.

## What this does not do

- **No grant carries a stamp.** `AuthMetadata` holds `key`, `principal`,
  `target`, `permission`, `mute` and `expires`. A stamp field is a wire change
  and it waits for the policy decision.
- **`AuthSummarizer` still sorts by `key.id`**, and it still groups by
  permission rather than reading one order.
- **Nothing subtracts.** The `-` rule needs this order and it also needs the
  permission check to change.

## Two costs, recorded

1. **A stamp is a map.** It holds one entry per node that has ticked. The
   entry count grows with the number of nodes that ever wrote, and every row
   carries one.
2. **A stamp must serialize.** It reaches the same wire that
   `DomainWireShapeTests` pins for the other domain types.

## One alternative, recorded and not chosen

A hybrid logical clock answers a total order from one value, which is a
timestamp, a counter and a node id. It is cheaper to store than a map, and it
stays close to physical time, which helps an operator read a record.

It gives a weaker causality statement than a vector clock. It cannot report
that two writes were concurrent, because it orders them.

The owner asked for a vector clock, and this document records the alternative
rather than substituting it.

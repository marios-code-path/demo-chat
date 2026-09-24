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

So `ClockStamp.ORDER` adds a rule that makes the order total.

### The first rule was wrong

**Do not compare causality pairwise and then break a tie by the origin.** That
rule is not transitive, so it is not an order at all.

Measured on 2026-09-23, from the reviewer:

| Stamp | Clock | Origin |
|---|---|---|
| a | `{1:1}` | 2 |
| b | `{2:1}` | 1 |
| c | `{1:2}` | 0 |

`b` and `a` are concurrent, so the origin puts `b` first. `a` comes before `c`
by causality. `c` and `b` are concurrent, so the origin puts `c` first. That
gives `b < a < c < b`, a cycle.

A sort can then answer differently for one input, and TimSort refuses the
comparator with `Comparison method violates its general contract!`. The suite
reproduces all three failures against that rule.

### The rule that holds

**`ClockStamp.ORDER` reads `VectorClock.total` first**, which is the sum of
every count.

That sum rises with causality. If one clock comes before another, then every
count is lower or equal and one count is lower, so the sum is lower. **A cause
therefore always sorts before its effect.** The sum is a linear extension of
the partial order, and it is transitive because it compares numbers.

Two stamps of one sum are ordered by the node that wrote them, and then by the
counts themselves. The last step keeps the order strict, so two stamps compare
equal only when they are equal.

**Reading the sum is a choice, not a law.** It orders two concurrent stamps by
how much each node had seen. One place decides it.

### What the order does not do

It does not group a causal chain together. A linear extension may place an
unrelated stamp between two stamps of one chain. **Nothing in the subtractive
rule needs that grouping**, because that rule asks only whether one grant comes
before another.

A deterministic topological sort over the whole grant set would keep chains
together. It is not a comparator, it reads every row at once, and it needs its
own tie rule for the concurrent frontier.

## The tests that hold this

Twenty seven. Four exist because of the ordering defect, six guard the
boundary, five hold immutability and normalization, and two state the restart
requirement.

- The three stamps of the counterexample, over **every one of the six
  orders**. The first version of that test read one order, and a cycle hides
  from one order, so it passed against the rule it was written to catch.
- Transitivity over 60 random stamps, every triple.
- A cause never sorts after its effect, over 200 random pairs.
- A sort of 500 random stamps answers one order and does not refuse the
  comparator.

## The value cannot change after it is built

**`VectorClock` copies the map it is given, and the copy refuses a write.**
It is not a data class, so there is no `copy` that could carry an unchecked
map past the constructor.

This matters because `total` is computed once. Measured on 2026-09-23, before
the copy existed: a caller kept the map it had passed and raised a count.
`total` stayed as it was, `relate` read the new count, and the order then
answered that a clock came before its own cause. **The cached sum turned a
mutable input into a wrong order.**

**A count of zero is not stored.** It reads the same as an absent count, so
`{}` and `{1:0}` compared equal through the order and were unequal objects.
A reader that puts stamps in a sorted set would have seen one and a reader
that puts them in a hash set would have seen two.

## The boundary, and why it is checked

**A stamp reaches this code from a store.** `CHAT-ojbgbznh` will read one
back, so the constructor is a boundary and not only a convenience.

`VectorClock` refuses three inputs.

| Input | Why |
|---|---|
| A count that would carry the sum past `Long.MAX_VALUE` | A sum that wrapped reads as lower, and a cause would sort after its effect |
| A negative count | A count never falls |
| An index outside 0..1023 | The index is `app.nodeid`, which is validated to that range |

`tick` refuses to wrap for the same reason. Both use `Math.addExact` and
answer `IllegalArgumentException` with a message that states the cause.

`total` is computed once in the constructor. So the check runs at the
boundary, and a sort reads the value without adding the counts again for
every comparison.

**The failure is loud and early.** A wrapped clock would not throw. It would
answer a wrong order, quietly, inside a sort.

Six tests hold this. Against unchecked arithmetic, two of them fail.

## What a reader must do

1. Read a stamp from the store.
2. Call `NodeClock.observe` with its clock.
3. Tick when it writes.

Without step 2 a later write does not follow what this process has read, and
two rows that should be ordered report as concurrent.

## A restart repeats stamps, and that is an integration requirement

**A fresh `NodeClock` starts at zero.** So the first stamp after a restart
equals the first stamp before it, and two different grants can carry one
stamp. A new grant can then sort before an older one.

The node id lease does not close this. It stops two processes owning one id
at one time, and it keeps no counter.

**`CHAT-ojbgbznh` must close it before a stamp is stored.** Two candidates.

1. **Recover the counter from the store.** Read the highest count for this
   node before the first write, and `observe` it. One test shows that this
   works, and the cost is a read of the stored stamps at startup.
2. **Give each process lifetime its own identity.** An epoch beside the node
   id. That enlarges the identity space and it meets the claim lease, which
   holds one id per key type per store.

Two tests state the position. One shows that two fresh clocks repeat a stamp.
One shows that a clock which observes the stored counter does not.

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

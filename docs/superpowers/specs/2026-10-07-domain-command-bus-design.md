# Message command capture and backend completion

Date: 2026-10-07.

Issue: `CHAT-uxlstrdy`.

Related issue: `CHAT-paanfjhw`, for operators that filter, compare, and route by root identity.

Status: Stage 1 decisions accepted by the owner on 2026-10-07. Stage 2 decisions remain open.

Implementation has not started. This spec does not authorize code changes or deployment.

Scope: the owner accepted a staged delivery on 2026-10-07. Stage 1 is the `memory` command bus in one process. Stage 2 is the `kafka` command bus. See `Delivery stages`.

## Problem and first boundary

`MessagingServiceImpl.send()` directly sequences Persistence, Index, optional Vector, and Pubsub writes.
Each failure prevents later writes. Backend composition therefore controls both execution order and caller completion.

The first boundary replaces that message write sequence with captured commands and independent backend handlers.
The caller awaits a declared set of backend completions. Completion requirements do not define processing dependencies.

The first command records a normal chat message. User creation, room creation, membership, and authorization writes remain outside this boundary.
Claim-check payload storage is deferred. Commands carry their message content.
This design captures commands. It does not establish domain events as the authoritative history for all application state.

## Delivery stages

The owner accepted this staged delivery on 2026-10-07.

### Stage 1: the `memory` command bus in one process

- Stage 1 delivers the `memory` command bus provider in one process.
- Command TTL stays disabled.
- Request identity mappings last for the process lifetime.
- Stage 1 and Stage 2 use the same command, handler, receipt, and completion contracts.
- Startup refuses an unsupported replica model for a process-local provider.
- Stage 1 gives no crash-recovery guarantee. A restart loses the mappings, the retained status, and unresolved commands.

Stage 1 must still test concurrent duplicate requests, early completions, uncertain backend outcomes, and the history and live handoff.
The Stage 1 decisions must be settled before the Stage 1 implementation plan is written.

### Stage 2: the `kafka` command bus

- Stage 2 delivers the `kafka` provider. It gets its own spec decisions and its own implementation plan.
- Distributed admission, crash recovery, status routing across instances, and replica delivery are deferred to Stage 2.
- The Stage 2 decisions stay open. Stage 1 does not resolve them.

Sections that describe Kafka behavior state the Stage 2 design. They do not describe Stage 1 work.

## Agreed architecture

- A launch selector chooses the command bus provider: `memory` or `kafka`. See `Command bus providers`.
- With `kafka`, Kafka provides the shared command and completion transport.
- With `kafka`, Kafka Streams owns request admission and its durable request identity mapping.
- Admission assigns the message key without a key registry write. `P` registers that key. See `Message key assignment and registration`.
- Each logical backend has one consumer group within its data scope.
- Replicas of that backend share the group and divide partition processing. This holds only for shared backend state. See `Backend state placement and query routing`.
- Two Cassandra persistence workers write to one shared Cassandra cluster.
- Persistence, Index, Vector, and Pubsub remain separate logical backends.
- Backend identity names a processing obligation, rather than a deployed worker.
- Root identity selects supported domains. Domain-name comparisons do not route commands.
- The entity ordering key and the domain root have separate purposes.

`P`, `I`, `V`, and `U` denote Persistence, Index, Vector, and Pubsub in examples.

## Core surface

These contracts belong in `chat-core`. Kafka types remain in the transport adapter.
The signatures below describe the surface. They are not implementation code.

| Contract | Operations | Responsibility |
|---|---|---|
| `DomainCommandBus` | `submit(submission)` | Admit a new request or recover its existing receipt |
| `CommandCompletionService` | `status(commandId)`, `observe(commandId)`, `await(commandId, requirement)` | Read retained results and evaluate caller completion |
| `DomainCommandHandler` | `descriptor`, `handle(command)` | Declare supported roots, operations, and the safe-repeat contract and version, then perform backend work |
| `BackendExecutionPolicy` | Policy declarations | Define retry, uncertain-outcome recovery, and escalation behavior |

`submit()` returns a receipt after admission commits the identity mapping and accepted command publication.
It does not return a canonical receipt merely because Kafka accepted the incoming request record.
If admission remains uncertain, the caller recovers through its original request identity.
Delivery of the admission result to the submitting caller is an open decision. See the decision list.

The adapter runtime manages subscriptions, offsets, completion publication, and handler lifecycle.
Handlers perform backend work. They do not manage Kafka offsets themselves.

## Request identity and command data

The client creates one `requestId` for each intentional send before its first network attempt.
Automatic retries and manual retries preserve that ID. A new intentional send uses a new ID, even with identical content.

Admission scopes identity to `(authenticated owner, requestId)` within the configured application data scope.
The authenticated owner comes from the trusted service boundary. Caller-supplied ownership does not establish identity.
Authentication or authorization refusal occurs before capture. Generic command submission remains an internal service contract.

The admission store records:

```text
(owner, requestId) -> {
    payloadFingerprint,
    commandId,
    messageKey,
    messageTimestamp,
    admittedAt,
    admissionOutcome
}
```

The fingerprint covers the operation, sender, destination, and message content under a versioned canonical encoding.
It excludes retry metadata. It detects changed intent, rather than identifying duplicate messages by content alone.

| Input | Admission action |
|---|---|
| New identity | Create the mapping and emit one accepted command |
| Same identity and same intent | Return the original receipt without emitting another accepted command |
| Same identity and different intent | Refuse this submission as a conflict without changing the original command |

With `kafka`, concurrent submissions for one identity reach the same Streams task through consistent partitioning.
With `kafka`, the mapping update, accepted command publication, admission result, and consumed offset commit in one Kafka transaction.
Streams exactly-once processing protects that Kafka boundary. Explicit request deduplication still handles separate duplicate input records.

The accepted command contains:

```text
commandId
owner
requestId
rootId
operation = RecordMessage
orderingKey
schemaVersion
message = { key, sender, destination, timestamp, content }
processingObligations
executionPolicyVersion
expiresAt = absent by default
```

Admission assigns the canonical message key and timestamp once for the accepted command.
Every retry and backend attempt uses those values. Assignment follows the existing key-type, root, and node-identity rules.
Admission does not write the key to the key registry. See `Message key assignment and registration`.
The first message command routes through the message root. Its destination identifies the proposed ordering boundary.

## Message key assignment and registration

The existing `key()` operation of a message store generates an ID and writes a key registry row.
For example, `KeyServiceCassandra.key()` inserts a `CSKeyRow`.
An admission that called it would write outside the Kafka transaction. An aborted admission would then leave an orphan registry row.

The proposed design separates key assignment from key registration:

1. Admission assigns the stable message ID and the message root. It writes nothing to the key registry.
2. `P` registers that exact key through the selected key provider.
3. `P` then stores the message.
4. `P` reports success only after registration and message storage both succeed.

Registration must tolerate repeats. A repeat with the same ID and root succeeds and writes no second row.
Registration must reject a conflicting root. A registered ID with a different root fails registration, and `P` does not report success.
A crash between registration and storage repeats both steps. The repeated registration succeeds as a repeat.

This requires a new idempotent registration contract on each key provider.
ID assignment must still use the node identity and key-type rules of the selected key type.

With `None`, the receipt confirms acceptance. It does not guarantee immediate message lookup.
Message lookup verifies the key against the registry. A lookup before `P` registers the key can answer not found.
Index, Vector, and Pubsub do not wait for registration. A reader can receive a message key before `P` registers it.

Processing obligations record the applicable logical backends at admission.
Configuration changes do not silently change the obligations of an existing command.
An absent Vector handler creates no Vector obligation. A required but unsupported backend causes admission refusal.

## Command bus providers

A launch selector chooses one command bus provider. The proposed values are `memory` and `kafka`.
Both providers implement the same service contracts: `DomainCommandBus`, `CommandCompletionService`, and the handler runtime.
A shared classpath does not require every deployment to use Kafka.

| Provider | Intended use | Durability |
|---|---|---|
| `memory` | Local deployments and tests | Deduplication and completion state last for the process lifetime. A restart loses that state. |
| `kafka` | Distributed execution | Admission, commands, and completions support durable recovery through Kafka. |

The `memory` provider gives no crash durability.
Stage 1 delivers `memory`. Stage 2 delivers `kafka`.
Startup must reject an unsupported provider combination.
The list of unsupported combinations is an open decision.

## Kafka processing topology

This section applies to the `kafka` provider. It is Stage 2 work.

```text
Authenticated send request
    -> requests topic, keyed by request identity
    -> Streams admission processor and request identity store
        -> admission results
        -> accepted commands, keyed by ordering identity
            -> persistence group -> Cassandra workers -> shared cluster
            -> index group
            -> vector group, when selected
            -> pubsub group
        <- backend completion records
    -> retained command status
    -> caller status queries and completion waits
```

Request partitioning serializes duplicate admission decisions. Command partitioning supplies the backend ordering boundary.
These are separate partitioning decisions. The transport adapter performs the necessary repartitioning.

Backend consumers read committed accepted commands.
Each applicable backend group receives the command. Workers within one group divide the work.
One group per worker would repeat processing across all workers and does not satisfy the agreed topology.

The runtime publishes backend success only after the handler's operation reports success.
It commits the command offset only after the completion record is durably published.
Crashes can repeat backend execution or completion publication. Both must tolerate repeats.
Parallel handler execution must preserve the configured ordering boundary and commit only a fully handled offset prefix.

Kafka transactions do not make Cassandra writes or client notifications atomic with Kafka completion records.
Each backend must define its own safe-repeat and recovery contract.

## Backend state placement and query routing

In Stage 1, every backend runs in one process with the `memory` command bus. Multi-replica placement is Stage 2 design.

Delivery scope applies to every backend, not only to Pubsub.
A shared group divides commands among replicas. That model is correct only when the replicas share one backend state.

Some providers keep process-local state. Each process then holds its own copy.
For example, `LuceneIndexBeans` creates the Lucene message index in each process.
If replicas divide index updates, each local index holds part of the messages. No replica can then serve a complete local read.

Known process-local providers include the Lucene indexes, memory persistence, memory Pubsub, and the embedded vector store.
Cassandra persistence writes to one shared cluster.

The spec must define these values for every backend provider:

- State placement: shared store, or process-local state.
- Replica model: a shared group divides work, one replica serves each data scope, or each replica receives every command.
- Query routing: which replica answers a read.
- Completion evidence: which completion proves that the answering replica holds the write.

A deployment that runs a process-local provider with an unsupported replica model must fail startup.

## Pubsub delivery scope

Multi-replica Pubsub delivery is Stage 2 work. Stage 1 runs Pubsub in one process.

Pubsub delivery scope is specific to each provider.
The shared group model applies to `U` only when the provider delivers each message to every replica that hosts a listener.

Memory Pubsub keeps listener sinks and membership maps in one process.
It remains single-process. A `U` group with more than one replica is not supported for this provider.
Multi-replica memory delivery requires a new mechanism that delivers each message to every listener-hosting replica.

Kafka Pubsub also keeps local sinks, record consumers, and membership maps in each process. See `KafkaTopicPubSubService.open()`.
The Kafka broker alone does not prove that every listener-hosting replica receives each message.
Kafka Pubsub replica delivery requires its own reviewed contract and test.

This spec makes no replica delivery claim for a provider until that provider has a reviewed delivery contract.

## Completion and retained status

A completion record identifies `commandId`, logical backend, outcome, and attempt metadata.
Duplicate completions do not count twice. A stale retry result must not replace an established success.

Backend status distinguishes pending processing, success, definitive failure, expired unstarted work, and uncertain execution.
Uncertain execution remains pending from the caller's perspective. It contributes no success and is not a terminal failure.

`observe()` first exposes retained status, then subsequent changes without losing changes between those steps.
`await()` evaluates retained status before waiting for later completions.
The originating service instance is not the sole owner of results. Another instance can recover the same command status.

| Requirement | Caller completion |
|---|---|
| `[P]` | Persistence reports success |
| `[P, I]` | Persistence and Index report success |
| `[P, I, V, U]` | Every named backend reports success |
| `None` | Admission commits, without waiting for any backend |

`None` does not bypass admission refusal or publication errors.
Backend completions may arrive in any order.
Completion requirements are selected through a declared service policy. They do not select which handlers execute.

The receipt contains `commandId` and `messageKey`.
The proposed send result contains that receipt and one caller outcome:

| Outcome | Meaning |
|---|---|
| `Accepted` | Admission committed, and the caller required no backend completion |
| `Pending` | The wait ended before its requirement resolved |
| `Completed` | Every backend in the caller's requirement succeeded |
| `Incomplete` | A required backend definitively failed or expired before execution, with backend results available |

`Completed` applies to the caller's requirement. It does not imply that every processing obligation resolved.
A non-required backend failure does not reverse the caller's successful result.
Status access requires authorization against command ownership. A command ID alone grants no access.

## Failure, retry, and expiry

A caller timeout ends its wait. It does not cancel backend processing or establish that a write failed.
A transport disconnect can prevent any receipt from reaching the caller. The client must retain its request ID independently.

The backend declares retry conditions, retry limits, delays, and uncertain-outcome recovery.
Exhausting automatic retries does not prove that the write did not occur.
An uncertain result remains pending for reconciliation or operator action.
In Stage 1, the recovery cycle of decision 12 reconciles an uncertain result. Stage 1 has no operator action.
A definitive failure records its reason and existing backend effects. It does not imply rollback of other backends.

Retries recover the original command and message key. They do not create new intent.
Persistence, Index, Vector, and Pubsub each require a safe-repeat contract before activation.
Pubsub recovery must address repeated delivery to recipients, rather than deduplicating completion records alone.

## Configurable defaults

Property names below are proposed names for review.

| Property | Default | Meaning |
|---|---|---|
| `app.command.ttl` | `disabled` | Accepted commands have no automatic execution expiry |
| `app.command.deduplication.retention` | `30d` | Proposed configurable minimum retention after all processing obligations resolve. The first version deletes no mapping. |
| `app.command.recovery.interval` | `30s` | Interval between recovery attempts for an uncertain result. A starting value, not a production measurement. |

The duration syntax must be documented and validated by the implementation.
Invalid or unsupported values fail startup. Caller wait timeout is a separate setting.

Unresolved commands retain their identity mapping regardless of age.
Retention starts after all processing obligations resolve, rather than when one caller's completion requirement succeeds.
Retrying the same request does not reset its identity or create another command.

Stage 1 retains every identity mapping for the process lifetime. A restart loses the mappings.
Stage 2 retains every identity mapping indefinitely until a deletion policy exists.
Automatic mapping deletion remains disabled in both stages.
The `30d` value is the proposed minimum for a later deletion policy. It does not imply automatic deletion.
An enforceable expired-request and delayed-replay policy must exist before deletion is enabled.
Compaction alone does not define that policy.

Stage 1 keeps command TTL disabled. Stage 1 startup rejects a finite TTL value.
The finite TTL rules below are Stage 2 design.

For finite TTL, admission records an execution deadline from its acceptance time.
A handler must not start a new effect after that deadline.
Work that demonstrably never started records an expired outcome.
Work started before the deadline can complete. An uncertain prior effect remains pending for reconciliation.
Expiry does not prove that an earlier attempt failed. It does not cancel effects already in progress.
Expiry must not erase request identity or revive expired work through a retry.
Deduplication must extend beyond the execution deadline when finite TTL is enabled.

Message retention, Kafka record retention, command execution expiry, and request deduplication retention are separate policies.
Kafka topic retention must preserve the backlog needed for supported recovery. It does not replace application identity retention.

## Sites of usage

`ChatMessageService.send()` currently returns `Mono<Key<T>>`.
Its request contains message content, sender, and destination, without a client request ID.
The proposed contract adds request identity and returns a receipt with the caller outcome.
This is a coordinated transport contract change. The existing key-only contract cannot express pending execution.

Conceptual service flow:

```text
send(request)
    -> authorize the caller
    -> validate sender and destination
    -> submit authenticated intent with requestId
    -> receive the canonical command receipt
    -> await the declared completion requirement
    -> return receipt and caller outcome
```

| Site | Change |
|---|---|
| `chat-core` | Add command, receipt, completion, handler, and policy contracts |
| Command bus providers | Add `memory` and `kafka` implementations of the same contracts, behind one launch selector |
| Key providers | Add ID assignment with no registry write, and an idempotent registration operation |
| `MessagingServiceImpl.send()` | Replace direct backend writes with submission and completion waiting |
| Composite bean configuration | Inject command services and declared completion policy |
| Message persistence handler | Register the assigned key, then adapt `MessagePersistence.add()` |
| Message index handler | Adapt `MessageIndexService.add()` |
| Message vector handler | Adapt `MessageVectorIndexer.add()` |
| Message pubsub handler | Adapt `TopicPubSubService.sendMessage()` |
| REST send facade | Carry request identity and expose receipt outcomes |
| RSocket mapping and typed client | Carry request identity and expose send and status operations |
| Shell send commands | Preserve request identity and show pending or completed status |
| Deployment configuration | Select the bus provider. Declare handlers, groups, data scope, and execution policies. |

Existing read interfaces remain the read surface. Their observed consistency changes with the selected completion requirement.
After `[P]` succeeds, reads through Index can still lag. Live Pubsub delivery can also remain pending.
The command bus is separate from chat pubsub. Chat pubsub is backend `U`.

## History and live handoff

`listenTopic()` reads the index history first. It then subscribes to the live Pubsub stream.
A message can reach the room between those two steps. That message then appears in neither result.
The current sequential send already has this gap. Independent `I` and `U` handlers make it wider, because `U` can publish before `I` indexes.

A `U` handler that waits for `I` does not close the gap.
The history read can still finish before the index write, and the subscription can still start after the publish.

The implementation must define an explicit history and live handoff.
Each message that reaches the room after the listen request must reach the listener through history or the live stream.

A subscription that starts before the history read does not close every gap.
Suppose `U` publishes before the listener subscribes, and `I` has not yet indexed the message.
The live stream does not hold the message, and the history read does not find it.

The handoff therefore needs a recovery mechanism. The proposed choices are:

- Retained replay: the listener receives retained messages from a defined boundary.
- Catch-up boundary: the history read waits until `I` has indexed every message before a defined boundary.

The selection and the boundary definition are open decisions.
The listener must remove each message whose message ID it already emitted.

REST and composite authorization checks remain before admission.
Background handlers execute trusted admitted commands. They cannot depend on the original request's live security context.
The implementation must define trusted producer access and ownership metadata without embedding bearer tokens in commands.

## Verification requirements

### Stage 1 cases

The Stage 1 implementation plan must include these observable cases:

1. `[P]` completes while other backends remain pending.
2. `[P, I]` waits for both successes in either arrival order.
3. `None` completes after admission and exposes admission refusal.
4. A completion before subscription remains observable.
5. Concurrent duplicate requests emit one accepted command and recover one receipt.
6. Changed content under the same request identity produces a conflict.
7. Admission itself writes no key registry row. An admission that aborts before it publishes a command leaves no partial mapping, no orphan committed command, and an unchanged registry. After admission commits, `P` can register the key before the caller receives its receipt. That registration is not an admission write.
8. Duplicate and stale completions cannot reverse success or satisfy the same obligation twice.
9. An uncertain backend result remains pending. It contributes no success and is not a terminal failure. A requirement that excludes that backend can still complete.
10. A caller timeout leaves execution and status recovery available in the same process.
11. An unauthorized submit or status query reaches no protected operation.
12. An identity mapping remains for the process lifetime. Its retention does not begin at `[P]` completion alone.
13. Disabled TTL permits delayed accepted work. Stage 1 startup rejects a finite TTL value.
14. Repeated Pubsub execution follows its reviewed recipient deduplication contract.
15. A message that `U` publishes before `I` indexes reaches a listener that starts between those two steps. The selected recovery mechanism delivers it.
16. A message in both the history read and the live stream reaches the listener once.
17. A refused submission leaves the key registry unchanged.
18. A repeated registration of the same key succeeds and writes no second row.
19. Registration of a registered ID with a different root fails, and `P` does not report success.
20. `P` reports success only after registration and message storage both succeed.
21. A `None` receipt does not imply lookup. A lookup before `P` registers the key can answer not found.
22. The `memory` provider passes the shared contract tests.
23. Startup rejects an unknown bus provider and each unsupported provider combination.
24. Startup rejects an unsupported replica model for a process-local provider.
25. An uncertain result enters recovery, reuses the same command ID, message key, timestamp, and content, and creates no new submission.
26. Recovery success or a definitive refusal releases the room. Another uncertain result schedules another attempt.
27. Startup rejects an active handler with a missing or unsupported safe-repeat contract before admission accepts a command. An inactive provider does not prevent startup.
28. A legacy RSocket send with a sender that differs from the authenticated user is rejected, and nothing is admitted.
29. A failure between staging and the admission commit leaves no mapping, no dispatch entry, and no handler call.
30. A notification failure after the admission commit still answers the receipt. The dispatcher still dispatches the command.
31. After `EmitResult.OK`, the publication record and the repeat marker commit together. A bookkeeping failure records an uncertain result, and recovery delivers one copy per listener.
32. A listener created from the callback of another listener receives the message under publication once, and no task deadlocks.

Mutation checks must prove admission deduplication, completion aggregation, and uncertain-outcome handling.
Focused module tests provide feedback. Full CI provides Stage 1 integration evidence.
Stage 1 tests make no crash-recovery claim.

### Stage 2 cases

These cases belong to the Stage 2 plan. Stage 1 does not verify them.

1. The `kafka` provider passes the same contract tests as the `memory` provider.
2. A crash during Kafka admission exposes no partial mapping and no orphan committed command.
3. A crash after a Cassandra write permits recovery without another logical message.
4. Two Cassandra workers divide work while targeting the shared cluster.
5. Worker reassignment preserves identity and the declared ordering boundary.
6. Another service instance recovers command status and the admission result.
7. Retention does not delete unresolved identities after a restart.
8. Finite TTL expires unstarted work without misclassifying uncertain effects.
9. Each multi-replica Pubsub provider follows its reviewed replica delivery contract.

A launched Kafka-to-Cassandra scenario provides Stage 2 integration evidence.

## Decisions required before an implementation plan

The main architecture is defined. Each stage has its own open decisions.

### Stage 1 decisions

The owner settled these decisions on 2026-10-07. The Stage 1 implementation plan builds on them.
See `Stage 1 decision recommendations` for a proposed default for each decision.

1. Specify ID assignment during admission without bypassing node identity and root contracts. Accepted 2026-10-07.
2. Define the idempotent registration contract of each key provider, including the root conflict result. Accepted 2026-10-07.
3. Define the bus selector property name and its behavior when unset. Capability decision 2 states that an unset or unknown selector fails startup. Accepted 2026-10-07.
4. Define the unsupported provider combinations that startup rejects. Accepted 2026-10-07.
5. Define how startup detects and rejects an unsupported replica model for a process-local provider. Accepted 2026-10-07.
6. Define the canonical payload encoding and fingerprint algorithm. Accepted 2026-10-07.
7. Define admission-result delivery to the submitting caller in one process. Include a result that arrives before subscription. Accepted 2026-10-07.
8. Define the history and live handoff mechanism and its message-ID deduplication. Accepted 2026-10-07.
9. Select retained replay or a catch-up boundary for the handoff, and define that boundary. Accepted 2026-10-07.
10. Define exact REST and RSocket contracts, including admission-pending responses and migration of existing clients. Accepted 2026-10-07.
11. Select the initial message completion requirement and caller wait timeout. Accepted 2026-10-07.
12. Define per-backend retry policies, ordering guarantees, and safe-repeat behavior. Accepted 2026-10-07.
13. Define Pubsub recipient deduplication or an explicit delivery guarantee. Accepted 2026-10-07.
14. Define obligation resolution when a backend is removed or an operator abandons recovery. Accepted 2026-10-07.

### Stage 2 decisions

These decisions are deferred to Stage 2. They are not resolved.

- Select the adapter module and compatible Kafka Streams dependency version from the current build.
- Define the node identity of each Kafka admission process.
- Define status storage, query routing, and observation recovery across service instances.
- Define admission-result recovery through another service instance.
- Define state placement, replica model, query routing, and completion evidence for every backend provider that runs with more than one replica.
- Define the replica delivery contract of each Pubsub provider that runs with more than one replica.
- Define admission crash recovery and its guarantee.
- Define external backend crash recovery and its guarantee for each backend. This guarantee is separate from the admission guarantee.
- Define backend removal and operator abandon for obligations. Stage 1 keeps an uncertain obligation pending until the process ends.
- Define durable attempt evidence and reconciliation for the finite TTL contract.

Expired-request refusal and automatic deduplication deletion are deferred beyond both stages.
Replay that deliberately rebuilds a projection must remain distinct from submitting a new message or repeating live notification delivery.

## Stage 1 decision recommendations

Status: the owner reviewed these recommendations on 2026-10-07.
All 14 Stage 1 decisions are accepted. The owner settled the last three, 7, 9, and 13, on 2026-10-07.
The numbers match the Stage 1 decision list. Connected decisions share one group.
Each recommendation gives a proposed default, the alternatives, the affected interfaces, and the acceptance evidence.
The code facts below were read on 2026-10-07.

### Decisions 1 and 2: key assignment and registration

#### Decision 1: ID assignment during admission

Status: accepted by the owner on 2026-10-07.

Proposed default:

- Add a `KeyAllocator<T>` contract to `chat-core`. Its `allocate(domain)` operation returns a `Key<T>` and performs no I/O.
- The allocator uses the `IKeyGenerator<T>` of the selected key type. `LongKeyGenerator` and `UUIDKeyGenerator` both take the node id.
- The root comes from `RootKeys.of(ChatDomain.MESSAGE)`.
- In Stage 1, admission runs in the composite process. Its node identity is `app.nodeid`.
- The store-side claim lease protects `app.nodeid` when a core selector names `redis` or `cassandra`.
- Admission sets the message timestamp once, when it creates the mapping.

Alternatives:

- Call the existing `key()` before admission. Rejected: it writes a registry row, so a refused or repeated request leaves an orphan row.
- Derive the ID from a hash of `(owner, requestId)`. Rejected: a `Long` hash loses time order and can collide.
- A Cassandra `TIMEUUID` column also accepts only a time-based UUID, so a hashed UUID fails there.

Affected interfaces: a new `KeyAllocator<T>`, the key generator beans, and the composite bean configuration.

Acceptance evidence:

- A test with a key service spy proves that admission calls no store-writing `key()` operation.
- Stage 1 cases 7 and 17.
- A `uuid` composition stores an assigned message ID in a Cassandra `TIMEUUID` column.

#### Decision 2: idempotent registration

Status: accepted by the owner on 2026-10-07.

Owner qualification: registration must be protected on every transport that exposes it.

- RSocket: the `key.register` route joins the `ROLE_SERVICE` or `ROLE_ADMIN` rule.
- REST: `CoreRestControllers.requireAbsent` refuses the core key controller on a REST launch. Any later REST exposure needs the same role rule.
- A transport test must prove the refusal on each transport that exposes registration.

Proposed default:

- Add `register(key)` to `IKeyService<T>`. It answers `Mono<Void>`.
- For an absent ID, the provider writes the row.
- For the same ID and the same root, the call succeeds and writes nothing.
- For the same ID and a different root, the call fails with a new `KeyRootConflictException`. The exception names the ID and both roots.
- `P` records a root conflict as a definitive failure.
- For a root key ID, the call fails. `rem` already refuses a root key.
- `P` calls `register`, then `MessagePersistence.add()`.

| Provider | Proposed conditional write |
|---|---|
| Memory | `ConcurrentHashMap.putIfAbsent`, then compare the stored root |
| Redis | `HSETNX` on `chat:keys:<keyType>`. When the field exists, `HGET` it and compare the root. |
| Cassandra | `INSERT ... IF NOT EXISTS`. When the insert does not apply, compare the root in the returned row. |
| RSocket `KeyClient` | A new `key.register` route |

The new route must join the `ROLE_SERVICE` or `ROLE_ADMIN` rule in `RSocketSecurityConfiguration`.
That rule ends with `anyExchange().permitAll()`, so a new route is open unless the rule names it.
The cost of one Cassandra lightweight transaction for each message is not measured.

Alternatives:

- A separate `KeyRegistration<T>` interface. It keeps `IKeyService` unchanged, but each provider then supplies a second bean.
- A plain upsert. Rejected: it cannot detect a conflicting root.

Affected interfaces: `IKeyService`, `KeyServiceInMemory`, `KeyServiceRedis`, `KeyServiceCassandra`, `DummyKeyService`, `KeyClient`, `KeyServiceMapping`, `KeyServiceController`, the REST key mapping in `chat-webflux`, and `RSocketSecurityConfiguration`.

Acceptance evidence:

- Stage 1 cases 18, 19, and 20 for each key provider. Redis and Cassandra run against containers.
- A mutation that replaces the conditional write with a plain write fails the conflict test.
- A transport test proves that an anonymous caller and a plain user cannot call `key.register`.

### Decisions 3 to 5: provider selection and startup validation

#### Decision 3: the selector property

Status: accepted by the owner on 2026-10-07.

Proposed default:

- The property is `app.command.bus`. It shares the `app.command` prefix with the TTL and retention properties.
- Stage 1 accepts `memory` only. Startup rejects `kafka` with a message that names Stage 2.
- Startup rejects an unset or unknown value. The message names the property. This follows capability decision 2.
- `chat-build` emits `app.command.bus=memory` on each launch that builds the composite message service.

Alternatives:

- Default to `memory` through `matchIfMissing`. The memory key generator and the Lucene index endpoint use that pattern today.
- That default saves configuration. A later change of the default would then act in silence.
- Use the `app.service.core` prefix. That prefix names store selectors, and the bus is not a store.

Affected interfaces: `chat-build`, `shell-scripts/test-flags.sh`, and each deployment configuration and test context that builds `MessagingServiceImpl`.

Acceptance evidence:

- Stage 1 case 23.
- The `test-flags.sh` golden cases include the new argument.
- A boot test of each deployment composition starts with the selector set.

#### Decision 4: rejected combinations

Status: accepted by the owner on 2026-10-07.

Proposed default: Stage 1 startup rejects each of these combinations.

1. `app.command.bus` is unset, unknown, or `kafka`.
2. `app.command.ttl` holds a finite value.
3. The completion requirement names a backend with no handler. For example, it names `V` and the vector selectors are unset.
4. The declared topology is not one process. See decision 5.
5. An active handler declares no safe-repeat contract, or an unsupported contract or version. See decision 12.

A `BeanFactoryPostProcessor` runs the check, as `VectorSelectorValidation` does.
A `SmartInitializingSingleton` check runs after the singletons exist, which is too late. The register records that trap.

Alternatives:

- Check at the first send. Rejected: the failure then appears far from its cause.

Affected interfaces: a new command bus validation in `chat-core`.

Acceptance evidence:

- One startup test for each rejected combination.
- Each test asserts that the message names the property that caused the refusal.

#### Decision 5: declared and detected topology

Status: accepted by the owner on 2026-10-07.

A process can detect its own configuration and its own beans. It cannot detect another process without shared infrastructure.

Proposed default:

- Add a declared property `app.command.replicas`. Its default is `1`.
- Stage 1 startup rejects any other value while `app.command.bus=memory`.
- The process checks its own selectors and handler beans. That check is real detection.
- The process does not detect a second process. Two processes, each with its own `memory` bus, are unsupported and undetected.
- The operator documentation must state that limit.

Alternatives:

- A store-side bus lease, like the node id claim lease. It detects a second process on a shared Redis or Cassandra store only.
- That lease cannot cover the memory store. Review it in Stage 2, not Stage 1.
- No declared property. Then the process has nothing to refuse, and the owner rule requires a refusal.

Affected interfaces: the decision 4 validation, `docs/BUILD.md`, and the `chat-build` help text.

Acceptance evidence:

- Stage 1 case 24, with `app.command.replicas=2`.
- The operator documentation states that the check reads the declaration and does not detect other processes.

### Decision 6: payload encoding and fingerprint

Status: accepted by the owner on 2026-10-07.

Proposed default:

- The fingerprint carries version `1`. The mapping stores the version beside the fingerprint.
- The fields, in fixed order, are: the operation name, the sender ID, the destination ID, and the message content.
- IDs encode through the `TypeUtil` text form of the key type. Content encodes as UTF-8 text.
- The encoding writes the version first. Each field then writes a 4-byte big-endian length, followed by its bytes.
- SHA-256 over those bytes gives the fingerprint.
- The `requestId`, the owner, timestamps, and retry metadata stay out of the fingerprint. The owner already scopes the mapping.
- Stage 1 encodes a `String` message value only. Every composition binds the message value to `String` today.

Alternatives:

- Canonical JSON, such as RFC 8785. It needs a canonicalization library, and it must not depend on Jackson 2 or Jackson 3 behavior.
- Jackson serialization of the request. Rejected: field order and serializer settings can change the bytes.

Affected interfaces: a new command fingerprint type in `chat-core`.

Acceptance evidence:

- Golden vectors for `Long` and `UUID` IDs. A fixed input gives a fixed hex value.
- A change in each field changes the fingerprint. Stage 1 case 6.
- A change in retry metadata leaves the fingerprint unchanged.

### Decision 7: admission-result delivery in one process

Status: accepted by the owner on 2026-10-07.

The first draft used one `ConcurrentHashMap.compute` step. That step cannot roll back a separate queue insertion.
An owner probe produced zero mappings and one queued command after an exception. So that draft is withdrawn.
The second draft used two commit writes under a commit lock. One explicit commit marker replaces it.

Proposed default:

1. A per-identity lock serializes admission for one `(owner, requestId)`. That lock is the admission critical section.
2. Inside the critical section, admission reads the mapping. A same-intent repeat answers the original receipt. A changed intent answers a conflict.
3. For a new identity, admission creates one commit marker in the uncommitted state.
4. Admission stages the mapping and the dispatch entry. Both refer to that one marker. Both stay hidden while the marker is uncommitted.
5. A hidden mapping answers no status query. The dispatcher dispatches no hidden entry.
6. Admission commits by one atomic write to the marker. That write exposes the mapping and the dispatch entry together.
7. `submit()` answers the receipt after the commit.
8. After the commit, admission notifies the dispatcher.

Ordering: the dispatch log keeps the staging order. For each room, the dispatcher stops at a hidden entry.
It continues when that entry commits or when rollback removes it. So the room order is the admission order.

Pre-commit failure: an exception before the commit removes the staged mapping and the staged dispatch entry. No reader observed them, because the marker was uncommitted.

Post-commit notification failure: the admission is committed. `submit()` still answers the receipt, and it does not report a rejected admission.
The dispatcher must find a committed entry without that notification. The plan must define how, for example by a rescan on the next notification and at an interval.

A duplicate submission waits on the per-identity lock. It then reads the committed mapping, or it stages a new one after a rollback.

Status observation:

- Each command has a status record. Its version rises on each change.
- `observe()` subscribes to changes first, then reads the record. It emits the record, then each change with a higher version.
- So a completion that arrives before the subscription is not lost.

Alternatives:

- One global admission lock. It is simpler, but it serializes every admission in the process.
- One atomic map step that also inserts into the queue. Rejected: the owner probe showed a queued command with no mapping.
- Two commit writes under a commit lock. Rejected: the owner asked for one explicit commit marker.
- Read the status record first, then subscribe. Rejected: a change between the two steps is lost.

Affected interfaces: the `memory` implementations of `DomainCommandBus` and `CommandCompletionService`, and the dispatcher of the handler runtime.

Acceptance evidence:

- Stage 1 cases 4, 5, 7, 29, and 30.
- A test holds the commit open with a latch. No handler and no status query observes the staged command during that time.
- A mutation that exposes the dispatch entry before the marker commits fails the latch test.
- A test completes a backend between `submit()` and `await()`. The `await()` call reports that completion.

### Decisions 8, 9, and 13: handoff and recipient delivery

Status: decisions 8, 9, and 13 are accepted by the owner on 2026-10-07.

The first draft replayed every accepted command. That replay bypassed a failed or pending `U` obligation. So that draft is withdrawn.

#### The room coordinator

A room coordinator runs every publication and every subscription boundary of one room. It runs one task at a time.

- A publication is one coordinator task. A subscription boundary is one coordinator task.
- A lock alone does not establish the guarantee. A subscriber callback can run on the publishing thread and request a new boundary.
- A reentrant lock would let that callback reenter the boundary during the publication. A plain lock would deadlock that callback.
- So the live stream delivers to subscribers off the coordinator, for example through `publishOn`.
- A subscriber callback never runs inside a coordinator task.
- A boundary request from a callback becomes a new coordinator task. It runs after the current publication task completes, including its bookkeeping.

#### Decision 13: publication success

Status: accepted by the owner on 2026-10-07.

Proposed default:

- `EmitResult.OK` means that the provider accepted the emission. It does not mean that recipients acknowledged delivery.
- `U` reports success only after an emit result of `EmitResult.OK` and a committed publication record.
- The current `MemoryTopicPubSubService.sendMessage()` discards the `tryEmitNext` result. Stage 1 must change it to read that result.
- Each memory room sink is `onBackpressureBuffer(256, false)` with one internal subscriber. The owner chose this on 2026-10-08.
- The internal subscriber requests without a limit and does no work. It lives until the room closes or the application stops.
- So the sink survives when every listener leaves, and an empty room never fills the buffer. The old sink stayed cancelled after its last listener left.
- A slow external listener can still fill the buffer. That overflow stays retryable.
- The plan must classify each other emit result as transient or definitive.

A publication task follows these steps:

1. Read the repeat marker of the command. If the marker exists, report success and emit nothing.
2. Prepare the publication record before the emission. The record holds the message, the command ID, and the next room sequence.
3. Emit with `tryEmitNext`.
4. On `EmitResult.OK`, commit the publication record and the repeat marker together, in the same task.
5. On another result, discard the prepared record. Nothing becomes visible.

The repeat marker is a repeat filter only. It does not prove that a listener received a message once. Decision 8 gives that proof.

Bookkeeping failure after `EmitResult.OK`: the message reached the live stream, but the record and the marker are absent.
`U` then records an uncertain result. Recovery repeats the publication. The repeat emits again, and the listener ID set of decision 8 removes the second copy.

#### Decision 9: retained replay of published messages

Status: accepted by the owner on 2026-10-07.

Proposed default:

- The room publication log holds each committed publication record, for the process lifetime.
- A failed or pending `U` obligation adds nothing to the log.
- A listener follows this sequence:
  1. A boundary task on the room coordinator subscribes to the live stream and records the log position. That position is the replay boundary.
  2. Buffer the live messages.
  3. Read the index history.
  4. Merge the history with the log entries up to the boundary.
  5. Emit the merged messages in timestamp order. Then emit the buffered and later live messages.
- Each publication task runs before or after each boundary task. So each committed publication is in the replay or on the live subscription.

Delivery coverage: a listener receives a room message when `I` indexed it before the history read, or `U` published it.
A message with a failed or pending `U` and a lagging `I` does not reach that listener. A later history read returns it after `I` indexes it.

The publication log uses memory for the process lifetime. That cost is not measured.

#### Decision 8: message-ID deduplication

Status: accepted by the owner on 2026-10-07.

Proposed default:

- The listener keeps the set of message IDs that it emitted, for the lifetime of the subscription.
- The set covers the history, the replay, and the live stream.
- The listener drops each message whose ID is already in the set.
- Stated guarantee: within one subscription, the listener emits each message ID at most once.
- The memory sink can buffer an emission until its first subscriber arrives. The ID set also removes that repeat.
- The ID set uses memory for each subscription lifetime. That cost is not measured.

Alternatives:

- Replay every accepted command. Rejected: it bypasses a failed or pending `U` obligation.
- Catch-up boundary: wait until `I` resolves each command before the boundary. An uncertain or failed `I` either blocks the listener or loses the message.
- Remove live repeats by a publication sequence number. It needs less memory. It does not remove an overlap between the history and the live stream.
- A per-room lock in place of the coordinator. Rejected: it does not stop a callback from reentering the boundary.

Affected interfaces: `MemoryTopicPubSubService.sendMessage()`, `MessagingServiceImpl.listenTopic()`, the `U` handler, a new room publication log, and a new room coordinator.

Acceptance evidence:

- Stage 1 cases 14, 15, 16, 31, and 32.
- A failed emit leaves the publication log and the repeat marker unchanged, and `U` reports no success.
- A pending `U` obligation adds nothing to a replay.
- A mutation that commits the record before the emit result is read fails the failed-emit test.
- A mutation that removes the replay step fails case 15.
- A mutation that removes the listener ID set fails case 16.
- A mutation that runs subscriber callbacks inside the publication task fails case 32.

### Decisions 10 and 11: caller results and completion waiting

#### Decision 11: completion requirement and wait timeout

Status: accepted by the owner on 2026-10-07.

Proposed default:

- The default requirement is `[P, I]`. After it succeeds, `messageById` finds the message, and the room history includes it.
- `V` is not required. Recall already reports coverage through `indexComplete`.
- `U` is not required. Live delivery reaches listeners through the handoff.
- The wait timeout is `5s`. It is a configurable initial default.
- Tests do not establish that `5s` suits production.
- The proposed properties are `app.command.completion.requirement` and `app.command.completion.timeout`.

Today `send()` returns after all four writes. With `[P, I]`, a send can return before live delivery. That is a behavior change.

Alternatives:

- `[P]`. It returns sooner, but the room history can lag.
- `[P, I, V, U]`. It matches the current behavior. A slow embedding call then delays each send.
- `None`. It returns soonest, but a message lookup can fail.

#### Decision 10: REST and RSocket contracts

Status: accepted by the owner on 2026-10-07. See `Sender binding` for the sender rule.

Proposed default:

- Add new operations. Keep the existing `send` operations as adapters.
- A new `MessageSubmitRequest` carries `requestId`, the message, and the destination. The server binds the sender to the authenticated user.
- `MessageSendRequest` does not change.
- A new `MessageSendResult` carries the receipt, `commandId` and `messageKey`, and one caller outcome.
- RSocket gains `message-submit`, which answers `MessageSendResult`. It also gains `message-command-status`, which answers the status record.
- REST gains `POST /message/submit/{id}`. The request ID travels in an `Idempotency-Key` header, because the REST body holds the message text today.
- REST gains `GET /message/command/{commandId}` for status.

| Result | REST status |
|---|---|
| `Completed` | 201 |
| `Accepted` or `Pending` | 202 |
| Same request ID with changed intent | 409 |
| `Incomplete` | 424 |

The response body always carries the outcome. The body is the authority, not the status code.

A status query by a caller that does not own the command answers 404. So a command ID reveals nothing.

The existing `send` routes keep their `Key` result:

- The server creates a `requestId` for each call. So these routes give no retry safety.
- The route awaits the default requirement and answers the key on `Completed`.
- On `Pending`, the route answers a typed error that carries the command ID. REST answers 504.
- On `Incomplete`, the route answers a typed error that names the failed backend.
- The shell moves to the new operations in Stage 1.
- The API documentation must state that the legacy send adapters provide no retry safety.

Each new facade method needs its own `@PreAuthorize` check. `CHAT-znprrzhn` measured that a facade method crosses no proxy.

Alternatives:

- Add `requestId` to `MessageSendRequest` and change `send` in place. That keeps one contract, but it breaks every client at once.
- Carry the REST request ID in the body. That changes the body shape of the REST send.

Affected interfaces for decisions 10 and 11: `ChatMessageService`, `MessagingServiceImpl`, `MessagingServiceAccess`, `MessageServiceAccess`, `ChatMessageServiceRestMapping`, `MessageServiceControllerMapping`, `MessagingClient`, `PubSubCommands`, `KeyRefusalAdvice`, and `docs/ANONYMOUS-AUTHORIZATION.md`.

Acceptance evidence:

- Stage 1 cases 1, 2, 3, 10, 11, and 12.
- One REST test for each status code in the table.
- The existing send tests pass on `Completed` without change.
- An unauthorized status query answers 404 and reads no command data.

### Decisions 12 and 14: backend recovery and obligation resolution

#### Decision 12: retry, ordering, and safe repeat

Status: accepted by the owner on 2026-10-07.

Proposed default:

- Each backend handler classifies an error as definitive or transient.
- Definitive errors include a domain refusal, a root conflict, and a missing room. The handler records a definitive failure at once.
- Transient errors include a timeout and a connection error. The handler retries them with exponential backoff from 100 ms.
- The handler makes at most five attempts. The count includes the initial attempt. So a transient error gets at most four retries.
- In Reactor, `Retry.backoff(n, …)` counts retries only. Five attempts are `Retry.backoff(4, …)`.
- `KeyServiceCassandra` uses `Retry.backoff(5, …)`. That gives five retries, and six attempts in total.
- The five-attempt limit applies to the initial execution cycle only. It does not limit the lifetime of the command.
- Retry exhaustion records an uncertain result. A timeout during a write also records an uncertain result. Both stay pending.

Strict room ordering:

- Each backend handles the commands of one room in admission order.
- A retrying or uncertain command holds the later commands of that room for that backend.
- Only a success or a definitive failure releases them.
- If an uncertain command released them, an earlier effect could finish after a later effect.
- Other rooms continue. Other backends continue for the same room.

Recovery cycle, chosen by the owner on 2026-10-07:

- An uncertain result enters a recovery cycle. The command stays pending and holds the later commands of its room for that backend.
- After the initial cycle, the runtime schedules one recovery attempt per interval. The interval is configurable. The proposed initial value is `30s`.
- A recovery attempt reuses the same command ID, message key, timestamp, and content. It creates no new submission.
- A success records success and releases the room.
- A definitive refusal records a definitive failure and releases the room.
- Another uncertain result schedules the next recovery attempt.
- Other rooms continue. Other backends continue for the same room.
- Status reports the held room and the time of the next recovery attempt.

Safe-repeat contract, chosen by the owner on 2026-10-07:

- Every active handler declares its safe-repeat contract and the contract version in its descriptor.
- Startup rejects a missing or unsupported contract or version. The check runs before admission accepts any command.
- Startup validates the declaration only. It cannot establish that tests passed.
- CI verifies each supported handler against its declared contract.
- The CI tests cover repeated execution, uncertain outcomes, and recovery with unchanged command identity.
- A provider that is not active does not prevent startup.
- So every active handler enters the recovery cycle. No active handler runs without recovery.

The repeat table below records behavior read from the code. It does not prove that a repeat is safe.

The five attempts and the `30s` interval are starting values. They are not production measurements.

| Backend | Repeat behavior read from the current code |
|---|---|
| `P` registration | New. Decision 2 makes it safe to repeat. |
| `P` storage | Memory replaces the map entry. Redis and Cassandra write under the message key. |
| `I` | Lucene `updateDocument` replaces by the exact key. The Cassandra index tables write by full primary key. |
| `V` | `VectorWriteMode` sets the repeat behavior, from `CHAT-muuaovqn`. A repeat calls the embedding model again. |
| `U` | Memory Pubsub emits again. Decision 13 adds a repeat filter. |

Alternatives:

- No retry in Stage 1. Each transient error then becomes an uncertain result at once.
- No room ordering for `P` and `I`, because the timestamp already orders the history. A slow command then holds no other command.
- No recovery cycle. An uncertain command then holds its room for that backend until the process ends. The owner rejected this on 2026-10-07.
- An operator abandon operation. Stage 2 holds it.

Affected interfaces: `BackendExecutionPolicy`, the `DomainCommandHandler` descriptor, the four message handlers, the decision 4 startup validation, and the handler runtime.

Acceptance evidence:

- Stage 1 cases 8 and 9.
- A transient error followed by success completes the obligation once.
- A transient error that persists makes exactly five attempts.
- A definitive error causes no retry.
- Retry exhaustion records an uncertain result.
- A recovery attempt reuses the command ID, message key, timestamp, and content, and it creates no new submission.
- A recovery success releases the room. A recovery definitive refusal records a failure and releases the room.
- A recovery uncertain result schedules another attempt after the configured interval.
- Startup rejects an active handler with a missing or unsupported contract or version.
- An inactive provider with no contract does not prevent startup.
- For each supported handler, CI runs repeated execution, an uncertain outcome, and recovery with unchanged command identity. Each test proves one logical result.
- An uncertain command holds a later command of the same room for that backend.
- The same uncertain command does not hold another room or another backend.

#### Decision 14: obligation resolution

Status: accepted by the owner on 2026-10-07.

Proposed default:

- Admission fixes the obligations. Startup fixes the handler set.
- In Stage 1, no backend can leave during a process lifetime. A restart loses every obligation. So Stage 1 needs no backend removal rule.
- Stage 1 has automatic recovery. Decision 12 defines the recovery cycle.
- Stage 1 has no operator abandon operation.
- An uncertain obligation stays pending while recovery continues. Status reports it as uncertain.
- Startup rejects an active handler without a supported safe-repeat contract. So every active handler enters recovery.
- Backend removal and operator abandon moved to the Stage 2 decision list.

Alternatives:

- An actuator operation that marks an uncertain obligation as abandoned. It records the operator identity.
- A requirement that names an abandoned backend then answers `Incomplete`. This adds an operator write path to Stage 1.

Affected interfaces: status reporting only.

Acceptance evidence:

- A test proves that an uncertain obligation stays pending during recovery and that status reports it as uncertain.
- A test proves that no operation abandons an obligation in Stage 1.

### Sender binding

The owner decided on 2026-10-07: an ordinary submission binds its sender to the authenticated user.

- `MessageSubmitRequest` carries no sender. The server takes the sender from the authenticated user.
- The legacy RSocket `message-send` adapter rejects a `from` value that differs from the authenticated user. It does not replace the value in silence.
- This is a behavior change on the legacy route. The API documentation and the release notes must state it.
- The REST send already takes the sender from the authenticated principal.
- Privileged impersonation needs a separate contract. It stays outside Stage 1.
- The fingerprint covers the bound sender.

Before this decision, the RSocket `message-send` route took `req.from` from the caller. Its access check read `SEND` on the destination only.

## Sources and code basis

The code sites were inspected during the design conversation on 2026-10-07.
The spec describes proposed behavior, rather than current implementation guarantees.

- `chat-core/src/main/kotlin/com/demo/chat/service/composite/ChatMessageService.kt`
- `chat-core/src/main/kotlin/com/demo/chat/domain/RequestResponse.kt`
- `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessagingServiceImpl.kt`
- `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/CompositeServiceBeansConfiguration.kt`
- `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/access/MessagingServiceAccess.kt`
- `chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/composite/mapping/ChatMessageServiceRestMapping.kt`
- `chat-service-controller/src/main/kotlin/com/demo/chat/controller/composite/mapping/MessageServiceControllerMapping.kt`
- `chat-client-rsocket/src/main/kotlin/com/demo/chat/client/rsocket/clients/composite/MessagingClient.kt`
- `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommands.kt`
- `chat-messaging-kafka/src/main/kotlin/com/demo/chat/pubsub/kafka/impl/KafkaTopicPubSubService.kt`

Kafka Streams state recovery uses changelog topics. See [Streams architecture](https://kafka.apache.org/41/streams/architecture/).
Kafka processing guarantees cover Kafka state and output transactions. See [Streams processing concepts](https://kafka.apache.org/41/streams/core-concepts/).
The build manages `kafka-clients` 4.1.2, so both Kafka links name the 4.1 documentation.Delayed retries require an explicit identity contract. See [AWS retry guidance](https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/).

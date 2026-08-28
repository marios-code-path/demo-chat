# Node id claim lease, stage 2 of CHAT-koufkrsl

Issue: `CHAT-wyssrokr`. Depends on `CHAT-koufkrsl`, which is done.

## Goal

Make a duplicate `app.nodeid` impossible to start.

A Redis or Cassandra deployment must not start when a live `app.nodeid` claim
for the same key space is already held in the same shared store.

## Why a registry check cannot do this

One store can be reached by deployments that do not share a registry.

A Consul check sees only its own registry. Two deployments with separate
registries can hold the same node id, write to one store, and detect nothing.
The collision appears later as two entities that share a key.

The claim must therefore live in the store that the deployments share.

## Scope

In scope:

- A `NodeIdClaimStore` contract in `chat-core`.
- A startup guard that claims, renews, and releases a lease.
- A Redis implementation with atomic owner checks in Lua.
- A Cassandra implementation with a claim table and lightweight transactions.
- A claim table in `keyspace-long.cql` and `keyspace-uuid.cql`.
- Tests for denial, release, expiry, boot failure, and memory absence.

Out of scope:

- Any claim for the memory backend. A memory store is per process.
- Any change to `NodeId` or `NodeIdConfiguration` from stage 1.
- Any change to the capability composition design.

## Decisions

### D1. The claim seam is the key selector or the persistence selector

A process must claim a lease in every distinct shared backend that
`app.service.core.key` or `app.service.core.persistence` names.

The table below is exhaustive. The row is `app.service.core.key`. The
column is `app.service.core.persistence`. The cell is the set of backends
that the process claims.

| key \ persistence | `memory` | `redis` | `cassandra` |
|---|---|---|---|
| `memory` | none | redis | cassandra |
| `redis` | redis | redis | redis and cassandra |
| `cassandra` | cassandra | cassandra and redis | cassandra |

An unset selector counts as `memory` in this table. `app.service.core.key`
is unset for the client launches, and the memory `KeyGenConfiguration`
carries `matchIfMissing = true`. An unset `app.service.core.persistence`
names no shared persistence backend.

So an unset key with an unset persistence claims nothing. A client keeps
its current startup behaviour.

Reason: generated ids reach both the key store and the persistence store.
A seam on one selector alone leaves a real pair unprotected. Current
configuration permits every pair in the table.

Spring cannot express this OR with `@ConditionalOnProperty`. A condition
class `ConditionalOnSharedBackend` in `chat-core` reads both properties.

### D2. Renewal failure closes the application context

`Denied` means one thing only. Another owner holds a live claim.

| Event | Action |
|---|---|
| `claim` returns `Denied` | Fail startup with the duplicate node message |
| `claim` throws | Fail startup with the backend error |
| `renew` returns `Denied` | Log ERROR. Close the context at once |
| `renew` returns `Lost` | Log ERROR. Close the context at once |
| `renew` throws | Log WARN. Retry at the next interval |
| Close deadline reached | Log ERROR. Close the context |

The close deadline is `ttl - safety-margin` after the last successful claim
or renew. Every success reschedules a single shot deadline timer.

Reason: a lost claim is proven loss of ownership. The process closes before
another owner can safely continue.

`Lost` does not prove that another owner exists. `Denied` does prove it,
because the store named the other holder.

A store error proves neither. A short store outage must not stop a healthy
process. The margin makes the process close before the lease can expire.

### D3. The Cassandra claim table comes from the schema files

`node_claim` is declared in `keyspace-long.cql` and `keyspace-uuid.cql`.

The application runs no DDL. A missing table fails startup with a message
that names the table and the schema file. The documentation carries a
standalone `CREATE TABLE` statement for a keyspace that already exists.

`node_claim` is not added to `truncate-long.cql` or `truncate-uuid.cql`.
A live lease must expire. A test cleanup script must not delete it.

Reason: schema authority stays outside the application, as it is today.

### D4. The guard claims during context refresh

Each backend module contributes a claim store. Each such configuration
imports `NodeIdClaimGuardConfiguration`. The guard bean claims in
`afterPropertiesSet`.

A duplicate throws during refresh. Startup then fails before the
application is ready and before the process serves normal traffic.

The stronger statement is that the web server port never binds. Bean
instantiation runs before `WebServerStartStopLifecycle`, so the port should
still be closed. That order is not asserted anywhere today. The spec does
not rely on it. A lifecycle proof test measures it, and the result is
recorded as an observation, not as a requirement.

Reason: a listener on `ApplicationReadyEvent` runs after the web server
starts. A duplicate deployment would serve traffic before it closes.

### D5. Uniqueness is per key type per store

Cassandra separates key types by keyspace. `chat_long` and `chat_uuid` hold
separate `node_claim` tables.

Redis has no such split. The claim key carries the key type.

A `long` deployment and a `uuid` deployment on one Redis can both hold node
id 7. This is safe. `UUIDKeyGenerator` hashes the Snowflake `Long`, so the
two value spaces do not intersect.

### D6. Memory contributes no claim store

There is no no-operation implementation.

A no-operation store that returns `Granted` is false safety. Memory
contributes nothing, so the guard bean does not exist.

Memory still requires `app.nodeid`. Stage 1 requires it wherever a key
generator activates. This design adds no requirement to memory.

## Activation invariant

This invariant protects the stage 1 rule that `app.nodeid` stays scoped.

1. A claim store configuration imports `NodeIdClaimGuardConfiguration`.
2. A memory only context contributes no `NodeIdClaimStore`.
3. No claim store means no guard bean.
4. No guard bean means this design requests no `NodeId` bean.

A test pins each statement.

## Contract

Package `com.demo.chat.domain`, module `chat-core`.

```kotlin
sealed class ClaimResult {
    object Granted : ClaimResult()
    data class Denied(val holder: String) : ClaimResult()
    object Lost : ClaimResult()
}

interface NodeIdClaimStore {
    val backendName: String
    val scope: String
    fun claim(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult>
    fun renew(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult>
    fun release(nodeId: NodeId, owner: String): Mono<Void>
}
```

`claim` never returns `Lost`. `Lost` reports that a renew found no live
claim.

`backendName` orders the stores. It is `redis` or `cassandra`.

`scope` is the full phrase that names the space the claim covers. Redis
supplies `redis store for key type long`. Cassandra supplies
`cassandra keyspace chat_long`.

`scope` carries the whole phrase so that one message template serves both
backends. A template that joined a backend name to a short scope would need
a branch per backend.

The stores compose on the reactive infrastructure. The guard is the only
place that blocks. The guard applies `.timeout(operationTimeout).block()`.

## Guard

`NodeIdClaimGuard` implements `InitializingBean` and `DisposableBean`.

It receives `List<NodeIdClaimStore>`, the `NodeId`, a `RuntimeOwnerId`,
`NodeIdClaimProperties`, and the `ConfigurableApplicationContext`.

### Startup

1. Sort the stores by `backendName`. This is a stable total order.
2. Claim each store in that order. Record each granted store.
3. On a denial or an error, release the recorded stores in reverse order.
4. Release is best effort. A release failure logs at DEBUG.
5. Fail startup with `NodeIdClaimException`.

### While the process runs

The guard renews every store at `renew-interval`.

A success reschedules the deadline timer to now plus `ttl - safety-margin`.

The deadline timer closes the context when it fires.

The guard owns one single thread scheduler. It creates the scheduler and it
shuts the scheduler down. The guard does not use a shared task scheduler,
because a shared pool can delay a renew behind unrelated work.

A test supplies a virtual clock and a controlled scheduler. The unit tests
therefore need no real waiting.

### Shutdown

A renew task can decide to close the context. That task runs on the guard
scheduler. `destroy` then runs inside `close`, on that same thread.

A `destroy` that waits for the scheduler to finish would wait for itself.
That is a deadlock.

Two rules prevent it.

1. The guard never closes the context from the scheduler thread. It
   dispatches `context.close()` to a separate daemon thread named
   `nodeid-claim-close`. The scheduler task returns at once.
2. `destroy` calls `shutdownNow` on the scheduler. It never calls
   `awaitTermination` from a scheduler thread. It checks the current thread
   before any wait.

Rule 1 alone is enough. Rule 2 stays as a second barrier, because a future
caller may close the context from an unexpected thread.

`destroy` cancels the scheduler first. It then releases every granted store
under the operation timeout. A failure logs at DEBUG and never blocks
shutdown.

Release is an optimisation. It makes a clean restart immediate. Expiry is
what makes the design correct.

## Runtime owner id

`RuntimeOwnerId` is generated once at process start.

Format: `$appName@$host:$pid#$random8hex`.

The value is unique per process. A restart never reuses it. The value is
readable in the failure message.

## Properties

Prefix `app.nodeid.claim`.

| Property | Default | Rule |
|---|---|---|
| `ttl` | `30s` | At least 1s. Whole seconds only |
| `renew-interval` | `10s` | `renew-interval <= ttl / 3` |
| `safety-margin` | `5s` | Greater than zero. Less than `ttl` |
| `operation-timeout` | `5s` | `operation-timeout < renew-interval` |

One more rule: `ttl - safety-margin` must be greater than `renew-interval`.

`ttl` uses whole seconds because Cassandra TTL uses whole seconds.

`operation-timeout` is strictly less than `renew-interval`. A blocked call
must not consume the next renewal slot.

A rule failure fails startup and states the rule.

The default set gives a close deadline of 25s. A renew error at 10s and at
20s keeps the process running. The deadline timer closes it at 25s.

## Redis implementation

Module `chat-persistence-redis`. Bean on `ReactiveStringRedisTemplate`.

Key: `chat:nodeclaim:<keyType>:<nodeId>`.

No braces. Redis Cluster hash tags are not intended.

The key type comes from `app.key.type`. The store reports
`scope = "key type <keyType>"`.

### Claim

```
SET chat:nodeclaim:long:7 <owner> NX PX 30000
```

A nil reply means another owner holds the claim. The store then reads the
key for diagnostics only.

A holder that disappears between the two calls is retried once. A second
failure to name the holder throws a backend error. The store never invents
a holder.

### Renew

```lua
local v = redis.call('GET', KEYS[1])
if v == false then return 'lost' end
if v == ARGV[1] then
    redis.call('PEXPIRE', KEYS[1], ARGV[2])
    return 'granted'
end
return 'denied:' .. v
```

The reply is one string. `lost` is `Lost`. `granted` is `Granted`.
`denied:<owner>` is `Denied`.

A single string keeps one serializer in play. A Lua array that mixes a
number and a string needs a mixed result type, and
`ReactiveStringRedisTemplate` carries a string serializer.

The script returns the state and the holder in one round trip. No read
follows a failed script. The value can change between two calls.

### Release

```lua
local v = redis.call('GET', KEYS[1])
if v == false then return 'lost' end
if v == ARGV[1] then
    redis.call('DEL', KEYS[1])
    return 'granted'
end
return 'denied:' .. v
```

`PX` makes the Redis server the clock. No application clock enters the
decision.

## Cassandra implementation

Module `chat-persistence-cassandra`. Bean on the reactive session.

```sql
CREATE TABLE chat_long.node_claim(
    node_id  int,
    owner_id text,
    PRIMARY KEY(node_id)
);
```

The same table is declared in the `chat_uuid` keyspace.

The keyspace comes from the configured session. The store reports
`scope = "keyspace <keyspace>"`.

| Operation | Statement |
|---|---|
| Claim | `INSERT INTO node_claim (node_id, owner_id) VALUES (?, ?) IF NOT EXISTS USING TTL 30` |
| Renew | `UPDATE node_claim USING TTL 30 SET owner_id = ? WHERE node_id = ? IF owner_id = ?` |
| Release | `DELETE FROM node_claim WHERE node_id = ? IF owner_id = ?` |

A claim that is not applied returns the current `owner_id` in the result
row. That value becomes `Denied(holder)`.

A renew that is not applied returns the current row. An absent row is
`Lost`. A different owner is `Denied`.

Lightweight transactions run at `SERIAL`. The coordinator applies the TTL.
No application clock enters the decision.

A missing table raises `InvalidQueryException`. The store converts it into
a startup failure that names `node_claim` and `keyspace-<type>.cql`.

Reference for the statement order and TTL behaviour:
https://cassandra.apache.org/doc/latest/cassandra/developing/cql/dml.html

## Failure message

`NodeIdClaimException` carries `nodeId`, `scope`, `holder`, and `ttl`. It
builds the text in one place in `chat-core`.

The exception carries no `backendName`. The scope phrase already names the
backend. `backendName` stays on the store, where it orders the claims.

`scope` is the full phrase that names the space the claim covers. The store
supplies it.

The message must state the scope, because uniqueness is per key type per
store. A message that says "one store" is false for a Redis `long`
deployment beside a Redis `uuid` deployment. See D5.

The template is one string:

```
app.nodeid=<id> is already claimed in the <scope>.
Holder: <holder>
Two deployments that write to the <scope> must not use the same app.nodeid.
Set a different app.nodeid, or stop the other deployment and wait <ttl>
for its lease to expire.
```

Redis:

```
app.nodeid=7 is already claimed in the redis store for key type long.
Holder: core-service@host-a:4711#a3f19c2b
Two deployments that write to the redis store for key type long must not
use the same app.nodeid.
Set a different app.nodeid, or stop the other deployment and wait 30s
for its lease to expire.
```

Cassandra:

```
app.nodeid=7 is already claimed in the cassandra keyspace chat_long.
Holder: core-service@host-a:4711#a3f19c2b
Two deployments that write to the cassandra keyspace chat_long must not
use the same app.nodeid.
Set a different app.nodeid, or stop the other deployment and wait 30s
for its lease to expire.
```

One template builds both. Only the scope and the wait time differ.

## Cassandra UUID note

The Cassandra UUID path uses `CassandraUUIDKeyGenerator`. That generator
ignores the node id.

A Cassandra UUID deployment still claims a lease.

This is policy uniformity, not collision prevention. One contract covers
every backend and every key type. An operator does not have to remember an
exception.

## Testing

### Unit tests in chat-core

These use fake stores. They need no container.

| Test | Assertion |
|---|---|
| Grace window holds | A renew error at 10s and at 20s keeps the process running |
| Grace window closes | The deadline timer at 25s closes the context |
| Success resets | A successful renew reschedules the deadline timer |
| Lost closes at once | A `Lost` renew closes the context with no grace |
| Denied closes at once | A `Denied` renew closes the context with no grace |
| Ordered claim | Two stores are claimed in `backendName` order |
| Ordered release | A denial at store two releases store one first |
| Property rules | Each rule in the properties table fails startup and states the rule |
| Message text | The text names the property, the backend, the scope, the holder, and the wait |
| Close is off the scheduler | A `Denied` renew closes the context on the `nodeid-claim-close` thread, not on a scheduler thread |
| Shutdown does not deadlock | `destroy` completes inside that close, within a bounded time |

### Store integration tests

These reuse `RedisTestContainer` and `CassandraTestContainerConfiguration`.
This design adds no container base class.

| Test | Backend |
|---|---|
| A second owner is denied | redis, cassandra |
| A release allows a takeover | redis, cassandra |
| An expiry allows a takeover | redis, cassandra |
| A `long` claim and a `uuid` claim on node 7 both hold | redis |
| A missing table names the table and the schema file | cassandra |

These tests call the store directly. They pass the TTL as a method
argument. The guard property rules do not apply to them.

The expiry tests use a short lease. Redis uses `ttl=1s`. Cassandra uses
`ttl=3s`, because Cassandra TTL uses whole seconds.

Write the Cassandra expiry test first. It is the probe for the unverified
claim below. A failure there changes the schema before any other code
depends on it.

### Boot tests

`chat-deploy-redis` and `chat-deploy-cassandra` each gain one test.

The test claims the node id in the container under a foreign owner. It then
boots the deploy context. It asserts a `NodeIdClaimException` and the
actionable text.

The same test records the lifecycle observation from D4. It asserts that
`ApplicationReadyEvent` is never published. It then reports whether the
server port accepted a connection at any point. The port result is
recorded in the spec as an observation. A port that binds does not fail
the test, because the requirement is failure before ready and before
normal traffic.

### Memory test

`chat-deploy-memory` asserts that no `NodeIdClaimStore` bean and no
`NodeIdClaimGuard` bean exist.

The test states the limit in a comment. Memory still requires `app.nodeid`.
This design adds no requirement and offers no false safety.

### Build gate

`./shell-scripts/build-health.sh` reports no new failures.

`./shell-scripts/build-health.sh --integration` reports no new failures.

`KNOWN_FAILING_INTEGRATION` stays empty. A container backed regression here
is signal. It is not a line to add to the list.

## Risks

### R1. Shared container and shared node id

Every test in the repository uses `app.nodeid=1`.

Spring caches contexts. Two container backed test classes in one module can
hold two open contexts against one store. The second boot is then denied.

The denial is correct behaviour, so it appears as a real test failure.

Mitigation: every container backed test class that activates a claim store
gets its own `app.nodeid`. The implementation plan allocates the values.
This allocation is not optional.

### R2. Truncate scripts omit the claim table

`truncate-long.cql` and `truncate-uuid.cql` do not truncate `node_claim`.

A test that truncates and reboots therefore meets its own earlier claim.

Mitigation: the same distinct node id allocation from R1.

### R3. The startup guard blocks

The guard blocks the refresh thread on a store call.

Mitigation: `operation-timeout` bounds every call. The default is 5s.

### R4. The guard can close the context from its own scheduler thread

A renew task decides to close. `destroy` then runs on the scheduler thread
and can wait for that same thread.

Mitigation: the guard dispatches `context.close()` to the
`nodeid-claim-close` daemon thread. `destroy` calls `shutdownNow` and never
waits from a scheduler thread. Two unit tests pin both rules.

## Claims that are load bearing and unverified

1. ~~Cassandra treats a TTL expired row as absent for `IF NOT EXISTS`.~~
   **Verified on 2026-08-28.** `NodeClaimTableProbeTests` ran against
   `cassandra:4.1.3` in Testcontainers. Three tests passed. A row whose
   `owner_id` expired accepted a takeover by a second owner. A live row
   refused one. A deleted row accepted one.
   The `expires_at` fallback is not needed. The design keeps the
   coordinator applied TTL, and no application clock enters the decision.

2. ~~The reactive Redis and Cassandra beans are present at every claim
   seam.~~ **Verified on 2026-08-28.** `RedisClaimBootTests` boots
   `key=memory` with `persistence=redis` and finds exactly one redis claim
   store. `CassandraClaimBootTests` boots `key=memory` with
   `persistence=cassandra` and finds exactly one cassandra claim store.
   The cassandra case needed `chat-persistence-memory` on the test
   classpath of `chat-deploy-cassandra`, for the memory `KeyServiceBeans`.
   It is declared in test scope only.

3. Whether the web server port binds before the claim fails is not
   measured. The boot tests assert the requirement, which is that
   `ApplicationReadyEvent` is never published. The stronger statement in D4
   is still an expectation, not a result. Do not quote it as verified.

## Consequences

This is a breaking change for an operator who runs two deployments with one
node id against one store. That deployment fails to start. This is the
intent of the issue.

An existing Cassandra keyspace needs the `node_claim` table before an
upgrade. The documentation carries the statement.

A deployment that adds `app.service.core.key=redis` or
`app.service.core.persistence=redis` now needs the store to be reachable at
startup. The store was already needed for service. The guard moves the
failure earlier and makes the message clearer.

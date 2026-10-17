# Node id claim lease

`app.nodeid` identifies one host in the Snowflake key generator. Two
deployments that write to one shared store must not use the same value.

**Since 2026-09-21 a kafka deployment reads `app.nodeid` for a second
purpose.** `KafkaDeployConfiguration` derives the consumer group
`chat-kafka-<nodeId>` from it, so that every instance reads every record
rather than competing for one partition. See CHAT-xblitvkl.

That widens the cost of a duplicate value. **A kafka deployment does not
always claim.** It claims only under the rule below, so two kafka instances
on memory stores can both state node id 1 with nothing to stop them. They
then share one consumer group, and one of them receives no messages. The
operator assigns distinct values.

A registry check cannot enforce this. One store can be reached by
deployments that do not share a registry. The claim therefore lives in the
store.

## When a process claims

A process claims when `app.service.core.key` or
`app.service.core.persistence` names `redis` or `cassandra`.

| key \ persistence | `memory` | `redis` | `cassandra` |
|---|---|---|---|
| `memory` | none | redis | cassandra |
| `redis` | redis | redis | redis and cassandra |
| `cassandra` | cassandra | cassandra and redis | cassandra |

An unset selector counts as `memory`. A client that names neither selector
claims nothing.

Generated ids reach the key store and the persistence store. That is why
one selector alone does not decide the claim.

## Scope of the claim

Uniqueness is per key type per store.

Cassandra separates key types by keyspace. Redis carries the key type in the
claim key, `chat:nodeclaim:<keyType>:<nodeId>`.

A `long` deployment and a `uuid` deployment on one redis may both hold node
id 7. Their value spaces do not intersect, because `UUIDKeyGenerator` hashes
the Snowflake `Long`.

A Cassandra UUID deployment still claims a lease. `CassandraUUIDKeyGenerator`
ignores the node id, so this is policy uniformity, not collision
prevention. One contract covers every backend, and an operator does not have
to remember an exception.

## Properties

| Property | Default |
|---|---|
| `app.nodeid.claim.ttl` | `30s` |
| `app.nodeid.claim.renew-interval` | `10s` |
| `app.nodeid.claim.safety-margin` | `5s` |
| `app.nodeid.claim.operation-timeout` | `5s` |

Rules: `ttl` is at least 1s and uses whole seconds. `renew-interval` is at
most `ttl / 3`. `safety-margin` is above zero and below `ttl`.
`operation-timeout` is below `renew-interval`. `ttl` minus `safety-margin`
is above `renew-interval`.

A rule failure fails startup and states the rule.

## What the process does while it runs

The process renews every `renew-interval`.

A renew that finds another owner closes the application context at once. A
renew that finds no live claim does the same.

A renew that fails on a store error logs a warning and retries. The process
closes when `ttl` minus `safety-margin` passes with no successful renew.
With the defaults that is 25 seconds.

A clean shutdown releases the lease, so a restart is immediate. A crash
leaves the lease to expire.

**A clean close releases the lease on Redis and on Cassandra, since
`CHAT-lswjobhz`.** The guard is a `SmartLifecycle` at phase
`Int.MAX_VALUE - 6144`. It releases in `stop`, before
`LettuceConnectionFactory` stops at phase 0. Before that change, the guard
released at bean destruction, after the connection factory stopped, and a Redis
restart with the same `app.nodeid` failed until the lease expired.
`RedisServiceLeaseBootTests` pins the immediate restart. `CHAT-ocpojbyy` held
that defect. A release that fails logs a warning, and the lease expires on its
own.

The guard claims before `RootKeyStartup`, so a refused claim mints no key.
`RedisClaimBootTests` pins it on an empty store. The renew loop and the close
deadline are the ones of the store service lease. See `docs/SERVICE-LEASE.md`.

## Cassandra upgrade

A fresh keyspace gets `node_claim` from `keyspace-long.cql` or
`keyspace-uuid.cql`. An existing keyspace needs the statement below. Run it
once per keyspace, before the upgrade.

```sql
CREATE TABLE IF NOT EXISTS chat_long.node_claim(
    node_id  int,
    owner_id text,
    PRIMARY KEY(node_id)
);
```

Use `chat_uuid` for a uuid keyspace.

`node_claim` is deliberately absent from `truncate-long.cql` and
`truncate-uuid.cql`. A live lease must expire. A cleanup script must not
delete it.

## Reading a startup failure

```
app.nodeid=7 is already claimed in the redis store for key type long.
Holder: core-service@host-a:4711#a3f19c2b
Two deployments that write to the redis store for key type long must not
use the same app.nodeid.
Set a different app.nodeid, or stop the other deployment and wait 30s
for its lease to expire.
```

The holder names the application, the host, the process id, and a random
suffix. A restart never reuses the suffix.

Set a different `app.nodeid`, or stop the named deployment. A stopped
deployment releases its lease on a clean shutdown. A crashed deployment
releases it when the lease expires.

A missing Cassandra table reports a different message. It names
`node_claim` and the schema file to apply.

## Testing note

Every container-backed test that activates a claim store uses its own
`app.nodeid`. Spring caches contexts, so two open contexts against one store
would collide. That collision is correct behaviour, and it appears as a test
failure.

**The store service lease adds a second rule.** Two open cores on one store
collide on their service leases, even with distinct node ids. A Spring test
class that keeps a store-backed core in the context cache closes it after the
class, with `@DirtiesContext(classMode = AFTER_CLASS)`. `RedisDeployBootTests`
and `CassandraDeployTest` do. See `docs/SERVICE-LEASE.md`.

The test classes of one Cassandra test module share one container, so they
share one keyspace. A node id that two classes claim collides in that keyspace.
Give each class its own ids. `CHAT-znodyvcc`.

| Module and test | `app.nodeid` |
|---|---|
| `chat-persistence-redis` claim store tests | 100 to 109 |
| `chat-persistence-cassandra` claim store tests | 200 to 209. `CassandraServiceLeaseStoreTests` holds 203 |
| `chat-persistence-cassandra` `NodeClaimTableProbeTests` | 210 to 212 |
| `chat-deploy-redis` `RedisDeployBootTests` | 1 |
| `chat-deploy-redis` `RedisClaimBootTests` | 11 and 12 |
| `chat-deploy-redis` `RedisGrantRestartTests` | 13 to 17, 30 and 31 |
| `chat-deploy-redis` `RedisLuceneFilesRestartTests` | 18 and 19 |
| `chat-deploy-redis` `RedisKeyAndPersistenceSafeRepeatTests` | 40 |
| `chat-deploy-redis` `RedisPubSubSafeRepeatTests` | 41 |
| `chat-deploy-redis` `RedisSharedStoreIndexTests` | 50 and 51, for the refusal of a second core |
| `chat-deploy-redis` `RedisServiceLeaseBootTests` | 52 to 58 |
| `chat-deploy-cassandra` `CassandraDeployTest` | 1 |
| `chat-deploy-cassandra` `CassandraClaimBootTests` | 21 and 22 |
| `chat-deploy-cassandra` `CassandraGrantRestartTests` | 23 |
| `chat-deploy-cassandra` `CassandraAuthorizationMatrixTests` | 24 |
| `chat-deploy-cassandra` `CassandraMessageSendTests` | 25 to 28 |
| `chat-deploy-cassandra` `CassandraMessagePointerTests` | 29 |
| `chat-deploy-cassandra` `CassandraEmbeddedVectorBootTests` | 30 |
| `chat-deploy-cassandra` `CassandraKeyAndPersistenceSafeRepeatTests` | 43 |
| `chat-deploy-cassandra` `CassandraIndexSafeRepeatTests` | 44 |
| `chat-deploy-cassandra` `CassandraServiceLeaseBootTests` | 45 to 47 |
| `chat-deploy-memory`, `chat-deploy-kafka` | 1, and they claim nothing |

`chat-deploy-kafka` claims nothing, and it still reads `app.nodeid` for its
consumer group. `KafkaDeploymentTests` reads `chat-kafka-1` from that value.

The vector recall tests (`chat-vector-redis`, `chat-deploy-redis`
`RedisVectorRecallBootTests`) activate no claim store: key and persistence
are memory. Only the vector store and the pubsub touch Redis. They claim
nothing and hold no row in the table above.

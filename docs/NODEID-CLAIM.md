# Node id claim lease

`app.nodeid` identifies one host in the Snowflake key generator. Two
deployments that write to one shared store must not use the same value.

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

| Module and test | `app.nodeid` |
|---|---|
| `chat-persistence-redis` claim store tests | 100 to 109 |
| `chat-persistence-cassandra` claim store tests | 200 to 209 |
| `chat-deploy-redis` `RedisDeployBootTests` | 1 |
| `chat-deploy-redis` `RedisClaimBootTests` | 11 and 12 |
| `chat-deploy-cassandra` `CassandraDeployTest` | 1 |
| `chat-deploy-cassandra` `CassandraClaimBootTests` | 21 and 22 |
| `chat-deploy-memory`, `chat-deploy-kafka` | 1, and they claim nothing |

The vector recall tests (`chat-vector-redis`, `chat-deploy-redis`
`RedisVectorRecallBootTests`) activate no claim store: key and persistence
are memory. Only the vector store and the pubsub touch Redis. They claim
nothing and hold no row in the table above.

# Stable roots across a restart: proof plan

FP: `CHAT-bafkgkko`, under `CHAT-avduuqwp`.

**Goal.** Prove that a grant written at run time on a domain root still applies
after a context restart, on Cassandra and on Redis. Prove key-type isolation
and root deletion protection on both persistent backends.

**Scope limits.**

- The memory backend has no restart test. Its roots do not survive a process
  restart, and the design states this.
- This work does not close `CHAT-znprrzhn`. Deployed route enforcement stays
  separate.

## Measured facts at master `dbd6b019`

1. **A grant read queries the auth index first.**
   `CoreAuthorizationService.getAuthorizationsAgainst` calls `authIndex.findBy`,
   then reads each row from persistence.
2. **A Cassandra deployment uses the Cassandra index.** That index is
   persistent.
3. **A Redis deployment uses the Lucene index.** `chat-build:319` sets
   `app.service.core.index=lucene`, and `RedisDeployBootTests:68` does too.
   The Lucene index lives in process memory.
4. **No main code reloads a Lucene index.** `LoadablePersistedIndex` in
   `chat-core` can fill an index from persistence, but no main code builds it.
   So after a restart the Redis auth index is empty. The grant row stays in
   Redis, but no read finds it, even when the roots are stable.
5. **All three key services refuse root deletion.** See
   `KeyServiceCassandra:35`, `KeyServiceRedis:42` and `KeyServiceInMemory:28`.
   Only the Cassandra `KeyServiceTests` tests it. That test does not check that
   the root stays readable after the refusal.
6. **Key-type isolation has a Redis test only.** `RootKeyStoreRedisTests` has
   "two key types on one redis keep separate roots". Cassandra separates key
   types by keyspace, and no test proves it.
7. **A clean shutdown releases the node id lease.** See
   `docs/NODEID-CLAIM.md`. So a restarted context can use the same node id.

## Decision D1: the Redis auth index

Fact 4 blocks task 2. The owner decides.

- **A. Reload the Lucene auth index from Redis persistence at start.** Build
  the auth index as a `LoadablePersistedIndex`. Load it before the process
  serves a request. The load runs after the root load, in the same startup
  sequence.
- **B. Record that Redis gives no grant continuity.** The Redis test then
  proves stable roots only. `CHAT-bafkgkko` closes with a stated gap.

**Recommendation: A, for the auth index only.** The user, topic, message and
membership Lucene indexes have the same defect. File that as a separate issue.
Task 2 measures one consequence and records it: whether `InitialUsersService`
finds `Admin` through the user index after a Redis restart.

## Task 1: Cassandra runtime grant survives a context restart

**File:** `chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraGrantRestartTests.kt`.

The test uses `SpringApplicationBuilder(ChatApp)` with command line arguments,
as `CassandraClaimBootTests` does. The settings are
`app.service.core.key=cassandra`, `app.service.core.persistence=cassandra`,
`app.service.core.index=cassandra`, `app.service.composite.auth`,
`app.users.create=false` and `app.nodeid=23`.

1. Start context A against the test container keyspace.
2. In A, create a user through the user service.
3. In A, read the `AccessBroker` bean. It is the production broker from
   `CoreAuthBeans.accessBroker`.
4. Assert that the broker denies `NEW` and `DEL` for that user on the
   `MESSAGE_TOPIC` root. `docs/ANONYMOUS-AUTHORIZATION.md` measures `NEW` as
   denied. `DEL` is not measured, so this step measures it.
5. Write the grant `{user, MESSAGE_TOPIC root, NEW}` through
   `AuthorizationService.authorize`.
6. Assert that the broker now allows it.
7. Close A.
8. Start context B with the same settings against the same keyspace.
9. Assert that B holds the same `MESSAGE_TOPIC` root as A.
10. Assert that the broker in B allows `NEW` for the user on that root.
11. Assert that the broker in B still denies `DEL` for the same pair. A broker
    that allows every request fails here.

## Task 2: Redis runtime grant survives a context restart

**Blocked by D1.**

**File:** `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisGrantRestartTests.kt`.

The steps are the same as task 1. The settings are
`app.service.core.key=redis`, `app.service.core.persistence=redis`,
`app.service.core.index=lucene`, `app.service.composite.auth`,
`app.users.create=false` and `app.nodeid=13`.

If D1 is A, this task also builds the reload.

1. Make the Lucene auth index a `LoadablePersistedIndex` over the auth
   persistence.
2. Load it in the startup listener after the roots load, and before
   `RootKeyInitializationReadyEvent`.
3. Add a unit test. A grant stored before the load is found after it.

If D1 is B, steps 10 and 11 of task 1 change. The test asserts the root at
step 9 only. The register records the gap.

## Task 3: key-type isolation and root deletion protection

1. **Cassandra key-type isolation.** Add a test to
   `RootKeyStoreCassandraTests`. A `long` loader on `chat_long` and a `uuid`
   loader on `chat_uuid` on one cluster keep separate roots. A second load of
   each reads its own roots.
2. **Redis key-type isolation.** The test exists. No change.
3. **Redis root deletion.** Add a test for `KeyServiceRedis`. `rem` of a
   domain root fails with `RootKeyDeletionException`. After the failure,
   `rootOf` of that root still returns the root.
4. **Cassandra root deletion.** Extend `rem refuses a root key` with the same
   check after the refusal.

## Task 4: mutation proofs and focused gates

Each mutation is applied, measured, and restored by absolute path. `git status`
proves each restore.

| Id | Mutation | Expected failure |
|---|---|---|
| M1 | `loadRootKeysFromStore` mints fresh roots on each start, in place of `RootKeyLoader.load()` | Task 1 at step 9 or 10. Task 2 the same. |
| M2 | Remove the auth index load, if D1 is A | Task 2 at step 10 |
| M3 | Remove the root guard in `KeyServiceRedis.rem` | Task 3 item 3 |
| M4 | Remove the root guard in `KeyServiceCassandra.rem` | Task 3 item 4 |
| M5 | Give the Cassandra `uuid` loader the `chat_long` keyspace | Task 3 item 1 |

**Focused gates.** Each runs with its output in a log file.

1. The new and changed test classes, with `-Pintegration` and the full
   upstream reactor, `-am`.
2. `shell-scripts/build-health.sh --integration`.

The full `--ci` gate is not required. No wire format and no image content
changes. If D1 is A, the Redis startup changes, and `--ci` runs as well.

## Documents

- `docs/NODEID-CLAIM.md`: add node ids 23 and 13 to the allocation table.
- `forward-register.md`: record the result, and the Redis index finding.
- `docs/ARCHITECTURE.md` section 3: add the auth index load, if D1 is A.

## Closure

`CHAT-bafkgkko` closes when tasks 1 to 4 pass. Its closing comment names each
test, each mutation result, and each gate result. If D1 is B, the comment
states the Redis gap.

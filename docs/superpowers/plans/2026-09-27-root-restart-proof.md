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

**Decided by the owner on 2026-09-27: A.** Reload the Lucene auth index from
persistence at start. The owner set these conditions:

1. Load all persisted auth metadata after the roots load.
2. Complete the load before `RootKeyInitializationReadyEvent`.
3. Fail the start on any load error.
4. Do not serve with a partial or empty auth index. See D2.
5. Test the production `AccessBroker` across a Redis context restart.
6. Keep the reload of the other Redis indexes in a separate issue.
7. Run `--ci`, because the Redis startup changes.

Option B, no grant continuity on Redis, is rejected.

## Decision D2: the serving window

**Open. The owner decides before task 2 step 4.**

Read from the Spring Boot 4.0.8 bytecode. Not measured.

- `SpringApplication.run` calls `refreshContext` before the `started`
  listeners.
- Both servers start inside the refresh. `WebServerStartStopLifecycle` is a
  `SmartLifecycle` at phase 2147481599. `RSocketServerBootstrap` is a
  `SmartLifecycle` at the default phase.
- `loadRootKeysFromStore` listens for `ApplicationStartedEvent`.

So both servers accept connections before the roots load. The same window
exists at master today, for every read of a root. A load in the same listener
completes before `RootKeyInitializationReadyEvent`, and a failed load stops the
process. It cannot prevent a request that arrives before the load.

- **A. Accept the window and record it.** File an issue that moves the whole
  root and index load before the servers start.
- **B. Move the load before the servers in this issue.** Run the shape checks,
  the root load and the index load in a `SmartLifecycle` with a phase below
  both servers. This changes the startup that T2 of `CHAT-avduuqwp` approved.

**Recommendation: B.** It is the only option that meets condition 4. Tasks 1
and 3 do not depend on D2, so they start first.

## Task 1: Cassandra runtime grant survives a context restart

**File:** `chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraGrantRestartTests.kt`.

The test uses `SpringApplicationBuilder(ChatApp)` with command line arguments,
as `CassandraClaimBootTests` does. The settings are
`app.service.core.key=cassandra`, `app.service.core.persistence=cassandra`,
`app.service.core.index=cassandra`, `app.service.composite.auth`,
`app.users.create=true` and `app.nodeid=23`.

**Two measured changes, 2026-09-27.**

- `app.users.create` is `true`. A grant read puts the `Anon` identity in its
  actor set, and that identity loads with the initial users. With `false`,
  every read failed with "The Anon identity is not loaded", and
  `hasAccessByKeyId` answered false.
- The target is the `KEY_VALUE_PAIR` root, not the `MESSAGE_TOPIC` root. The
  shipped grants name the `Admin`, `User`, `Message` and `MessageTopic` roots.
  The Cassandra auth index keeps one row per target, so a shipped row and the
  runtime grant on one root replace each other. `CHAT-rmxxtwtu` holds that
  defect. The steps below read `MESSAGE_TOPIC` as `KEY_VALUE_PAIR`.

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

This task also builds the reload.

1. Add `fun interface StartupIndexLoad { fun load(): Mono<Void> }` in
   `chat-core`, beside `StoreShapeCheck`.
2. Register one in `chat-deploy` when `app.service.core.index` is `lucene`. It
   reads `authMetaPersistence().all()` and adds each row to
   `authMetadataIndex()`. Any error ends the load with that error.
3. Run every registered load after the roots load and before
   `RootKeyInitializationReadyEvent`, in the `STORE`, `KV` and `HTTP` paths.
   Block on each load. A `NONE` process loads no roots, so it runs no load.
4. Apply D2.
5. **Startup failure test, listener level.** In `RootKeyStartupTests`, a load
   emits one row and then fails. The listener throws that error. No
   `RootKeyInitializationReadyEvent` is published.
6. **Startup failure test, Redis context level.** Before context B starts,
   write one auth metadata row that cannot decode into the Redis auth store.
   Context B fails to start, and the failure names the load.
7. **Load test.** A grant stored before the load is found through the index
   after it.

The owner decides whether a Lucene index of another domain reloads in a
separate issue. This task changes no other index.

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
| M2 | Remove the auth index load | Task 2 at step 10 |
| M6 | The load ignores an error and continues | Task 2 steps 5 and 6 |
| M3 | Remove the root guard in `KeyServiceRedis.rem` | Task 3 item 3 |
| M4 | Remove the root guard in `KeyServiceCassandra.rem` | Task 3 item 4 |
| M5 | Give the Cassandra `uuid` loader the `chat_long` keyspace | Task 3 item 1 |

**Focused gates.** Each runs with its output in a log file.

1. The new and changed test classes, with `-Pintegration` and the full
   upstream reactor, `-am`.
2. `shell-scripts/build-health.sh --integration`.

3. `shell-scripts/build-health.sh --ci`, because the Redis startup changes.

## Documents

- `docs/NODEID-CLAIM.md`: add node ids 23 and 13 to the allocation table.
- `forward-register.md`: record the result, and the Redis index finding.
- `docs/ARCHITECTURE.md` section 3: add the auth index load, and the D2
  result.

## Closure

`CHAT-bafkgkko` closes when tasks 1 to 4 pass. Its closing comment names each
test, each mutation result, and each gate result.

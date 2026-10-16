# Store service lease implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task by task, inline in one session. `AGENTS.md` forbids subagent-driven development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each store service on a shared backend exactly one live instance, through a store-side TTL lease, and release every lease on a clean close and on a failed start.

**Architecture:** A new `LeaseKeeper` in `chat-core` holds the renew and deadline loop that `NodeIdClaimGuard` holds today. `NodeIdClaimGuard` and a new `ServiceLeaseGuard` both become `SmartLifecycle` beans that use it. Redis and Cassandra each get a `ServiceLeaseStore`. The memory command runtime stops in a lifecycle phase above the guards, so no background writer writes after a release.

**Tech Stack:** Kotlin 2.4, Spring Boot 4, Spring Framework `SmartLifecycle`, Reactor, Lettuce, Cassandra driver 4, JUnit 5, AssertJ, StepVerifier, Testcontainers, Maven.

**Spec:** `docs/superpowers/specs/2026-10-09-store-service-lease-design.md`

**Issue:** `CHAT-lswjobhz`. Parent: `CHAT-aqpcacwv`. Branch: `chat-lswjobhz-shared-store-index`, in `.worktrees/chat-lswjobhz-shared-store-index`. `CHAT-ocpojbyy` closes with this work. `CHAT-jgegpcoi` closes the register period, and Task 9 needs its merge for register space.

## Global Constraints

- Lease names are `<kind>:<domain>`. The kind is `persistence` or `index`. The domains are `authmeta`, `keyvalue`, `membership`, `message`, `topic` and `user`.
- A lease lives in the persistence backend. A process claims when `app.service.core.persistence` names `redis` or `cassandra`. A lease never lives in the key backend.
- Redis lease key: `chat:lease:<keyType>:<name>`. Cassandra table: `service_lease(name text, owner_id text, PRIMARY KEY(name))` in both `keyspace-long.cql` and `keyspace-uuid.cql`.
- The truncate scripts do not list `service_lease`. A live lease must expire.
- Phases: `NodeIdClaimGuard` `Int.MAX_VALUE - 6144`. `ServiceLeaseGuard` `Int.MAX_VALUE - 5120`. The command runtime stop `Int.MAX_VALUE - 4608`. `RootKeyStartup` stays `Int.MAX_VALUE - 4096`.
- The guards renew with the node id properties: `app.nodeid.claim.ttl`, `renew-interval`, `safety-margin` and `operation-timeout`.
- The guarantee is the narrow one of the spec. No task adds fencing.
- Split deployments stay unsupported. No task adds a split path or a split refusal.
- The node id lease keeps its Redis key format and its `node_claim` table.
- The no-migration rule of 2026-09-25 stands. A Cassandra store without `service_lease` fails its start, and the operator recreates it.
- New container node ids: Redis 52 to 57, Cassandra 45 to 47. Record them in `docs/NODEID-CLAIM.md`.
- Run `mvn -o -pl chat-core,<module> test`, never `-pl <module>` alone. A Cassandra module needs `shared-resources-cassandra` in `-pl`. A deploy module uses `-pl <module> -am` with `-Dtest` and `-Dsurefire.failIfNoSpecifiedTests=false`.
- Container tests need `-Pintegration` and a running Docker. Read `docker events --filter event=oom --since 1h` before you trust a red Cassandra result.
- One Maven build at a time in this worktree. Write Maven output to a log file, and read the exit code and the summary lines.
- Commit the implementation before any mutation. Restore a mutation with `git checkout -- <absolute path>`, and prove the restore with `git status --short`.
- `git add` names exact files.
- Each commit message starts with `CHAT-lswjobhz:` and ends with the co-author trailer of recent `master` commits.
- Push and pull request steps need the owner's approval at that time.
- Agent-authored prose follows ASD-STE100 strict mode from `AGENTS.md`.

## Review Focus

1. **A request in flight when the servers stop.** Spring stops the servers before the guards, but a reactive chain that a request started can still write after the server stops. Expect the write to finish before the release, or to be a recorded limit. No task tests it. Task 5 records it in `docs/SERVICE-LEASE.md`.
2. **A Redis lease with a key type that the operator changes between runs.** The key carries the key type, so a long run and a uuid run hold separate leases on one Redis. Expect both to start. Task 3 pins it.
3. **A lease release that fails on close.** Expect a warning log line, and a lease that expires after `ttl`. Task 1 changes the release failure log from debug to warn, and pins it.
4. **A second start right after a crash.** Expect the second start to fail with the holder named, until `ttl` passes. Task 6 pins it with a seeded lease.
5. **A deployment that names `app.service.core.key=redis` with `app.service.core.persistence=memory`.** Expect a node id claim and no service lease. Task 3 pins it.

---

## File Map

| File | Task | Change |
|---|---|---|
| `chat-core/src/main/kotlin/com/demo/chat/domain/LeaseKeeper.kt` | 1 | New: `HeldLease`, `LeaseKeeper`, `LeasePhases` |
| `chat-core/src/main/kotlin/com/demo/chat/domain/ClaimScheduler.kt` | 1 | Thread name parameter |
| `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimGuard.kt` | 1 | `SmartLifecycle`, uses `LeaseKeeper` |
| `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimGuardConfiguration.kt` | 1 | Registers the guard as a dependent of each store |
| `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardStartupTests.kt` | 1 | `afterPropertiesSet` to `start` |
| `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardRenewalTests.kt` | 1 | `afterPropertiesSet` to `start` |
| `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardLifecycleTests.kt` | 1 | New |
| `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisClaimBootTests.kt` | 1, 3 | The first key mint, and the key-only case |
| `chat-core/src/main/kotlin/com/demo/chat/domain/ServiceLease.kt` | 2 | New: `ServiceLeaseStore`, `ServiceLeaseNames`, `ServiceLeaseException` |
| `chat-core/src/main/kotlin/com/demo/chat/domain/ServiceLeaseGuard.kt` | 2 | New |
| `chat-core/src/test/kotlin/com/demo/chat/test/domain/ServiceLeaseGuardTests.kt` | 2 | New |
| `chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisLeaseScripts.kt` | 3 | New: the shared Lua scripts |
| `chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisNodeIdClaimStore.kt` | 3 | Uses `RedisLeaseScripts` |
| `chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisServiceLeaseStore.kt` | 3 | New |
| `chat-persistence-redis/src/main/kotlin/com/demo/chat/config/persistence/redis/ServiceLeaseConfiguration.kt` | 3 | New |
| `chat-persistence-redis/src/test/kotlin/com/demo/chat/test/persistence/redis/RedisServiceLeaseStoreTests.kt` | 3 | New |
| `shared-resources-cassandra/src/main/resources/keyspace-long.cql`, `keyspace-uuid.cql` | 4 | `service_lease` table |
| `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraServiceLeaseStore.kt` | 4 | New |
| `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/config/persistence/cassandra/ServiceLeaseConfiguration.kt` | 4 | New |
| `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraStoreShapeCheck.kt` | 4 | `service_lease` in `PERSISTENCE_TABLES` |
| `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/CassandraServiceLeaseStoreTests.kt` | 4 | New |
| `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/MemoryCommandRuntime.kt` | 5 | Idempotent `close` |
| `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/command/MessageCommandConfiguration.kt` | 5 | `CommandRuntimeLifecycle` bean |
| `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/CommandRuntimeLifecycle.kt` | 5 | New |
| `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/CommandRuntimeLifecycleTests.kt` | 5 | New |
| `chat-deploy/src/test/kotlin/com/demo/chat/test/deploy/LeasePhaseOrderTests.kt` | 5 | New |
| `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisLuceneFilesRestartTests.kt` | 6 | `start` takes extra properties; the retry covers a held service lease |
| `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisSharedStoreIndexTests.kt` | 6 | Measurement becomes refusal |
| `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisServiceLeaseBootTests.kt` | 6 | New |
| `chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraServiceLeaseBootTests.kt` | 7 | New |
| Store-backed deploy test classes named by the `--ci` run | 8 | One context at a time |
| `docs/SERVICE-LEASE.md` | 9 | New |
| `docs/NODEID-CLAIM.md`, `docs/BUILD-HEALTH.md`, `IndexStartSequence.kt` KDoc, `drift.lock`, `forward-register.md` | 9 | Records |

---

### Task 1: One renew loop, and the node id guard as a lifecycle

**Files:** see the File Map rows for Task 1.

**Interfaces:**
- Produces: `interface HeldLease { val label: String; fun renew(): ClaimResult?; fun release(); fun deniedMessage(holder: String): String; fun lostMessage(): String }`. `class LeaseKeeper(subject: String, props: NodeIdClaimProperties, context: ConfigurableApplicationContext, scheduler: ClaimScheduler)` with `fun hold(leases: List<HeldLease>)`, `fun releaseAll()` and `val holding: Boolean`. `object LeasePhases { NODE_ID_GUARD, SERVICE_GUARD, WRITERS }`. `class ExecutorClaimScheduler(threadName: String = "nodeid-claim-renew")`.
- `NodeIdClaimGuard` implements `SmartLifecycle` and `DisposableBean`. `start()` replaces `afterPropertiesSet()`.

- [ ] **Step 1: Write the failing lifecycle tests**

Create `NodeIdClaimGuardLifecycleTests.kt`. Reuse `FakeClaimStore` and `ManualClaimScheduler` from `ClaimTestDoubles.kt`. Build the guard the way `NodeIdClaimGuardStartupTests` builds it, with a `GenericApplicationContext` that the test refreshes.

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.LeasePhases
import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimGuard
import com.demo.chat.domain.NodeIdClaimProperties
import com.demo.chat.domain.RuntimeOwnerId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext

/** The node id guard as a `SmartLifecycle`. See `CHAT-lswjobhz` and `CHAT-ocpojbyy`. */
class NodeIdClaimGuardLifecycleTests {

    private val redis = FakeClaimStore("redis")
    private val scheduler = ManualClaimScheduler()
    private val context = GenericApplicationContext().apply { refresh() }

    private fun guard() = NodeIdClaimGuard(
        listOf(redis), NodeId(7), RuntimeOwnerId.generate("test"),
        NodeIdClaimProperties(null, null, null, null), context, scheduler,
    )

    @Test
    fun `stop releases the lease`() {
        val guard = guard()
        guard.start()
        guard.stop()
        assertThat(redis.calls).containsExactly("claim:redis", "release:redis")
        assertThat(guard.isRunning).isFalse()
    }

    @Test
    fun `destroy releases a lease that stop did not release`() {
        val guard = guard()
        guard.start()
        guard.destroy()
        assertThat(redis.calls).containsExactly("claim:redis", "release:redis")
    }

    @Test
    fun `destroy after stop does not release twice`() {
        val guard = guard()
        guard.start()
        guard.stop()
        guard.destroy()
        assertThat(redis.calls.count { it.startsWith("release") }).isEqualTo(1)
    }

    @Test
    fun `the guard is not running before start and after a refused claim`() {
        val guard = guard()
        assertThat(guard.isRunning).isFalse()
        redis.claimAnswer = { com.demo.chat.domain.ClaimResult.Denied("other") }
        runCatching { guard.start() }
        assertThat(guard.isRunning).isFalse()
    }

    @Test
    fun `the guard starts in its own phase`() {
        assertThat(guard().phase).isEqualTo(LeasePhases.NODE_ID_GUARD).isEqualTo(Int.MAX_VALUE - 6144)
    }
}
```

`NodeIdClaimProperties(null, null, null, null)` takes the defaults. Read its constructor before you run. If its parameter list differs, pass `null` for each parameter.

- [ ] **Step 2: Run them and see them fail**

```bash
mvn -o -pl chat-core test -Dtest=NodeIdClaimGuardLifecycleTests > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:|e: file' $LOG | head
```

Expected: a compile failure on `LeasePhases`, `start` and `stop`.

- [ ] **Step 3: Write `LeaseKeeper.kt`**

```kotlin
package com.demo.chat.domain

import org.slf4j.LoggerFactory
import org.springframework.context.ConfigurableApplicationContext

/**
 * The lifecycle phases of the lease guards. See `CHAT-lswjobhz`.
 *
 * Spring starts phases in ascending order and stops them in descending order.
 * The connection factories start at phase 0, and `RootKeyStartup` at
 * `Int.MAX_VALUE - 4096`. So a guard claims after its backend starts and
 * before any start scan. It releases after the servers and the writers stop.
 */
object LeasePhases {
    const val NODE_ID_GUARD = Int.MAX_VALUE - 6144
    const val SERVICE_GUARD = Int.MAX_VALUE - 5120

    /** A background writer stops here, before the service guard releases. */
    const val WRITERS = Int.MAX_VALUE - 4608
}

/** One lease that a guard holds and renews. */
interface HeldLease {
    /** Names the lease in a log line. */
    val label: String

    /** Extends the lease. A thrown error is retried at the next interval. */
    fun renew(): ClaimResult?

    /** Drops the lease when this owner holds it. */
    fun release()

    fun deniedMessage(holder: String): String

    fun lostMessage(): String
}

/**
 * Renews the leases of one guard, and closes the context when it cannot.
 * `NodeIdClaimGuard` and `ServiceLeaseGuard` share it.
 *
 * The context closes when no renewal succeeds within
 * `ttl - safety-margin`, on this process's clock. A pause longer than the
 * safety margin defeats this. See the guarantee in
 * `docs/superpowers/specs/2026-10-09-store-service-lease-design.md`.
 */
class LeaseKeeper(
    private val subject: String,
    private val props: NodeIdClaimProperties,
    private val context: ConfigurableApplicationContext,
    private val scheduler: ClaimScheduler,
) {
    private val log = LoggerFactory.getLogger(LeaseKeeper::class.java)

    @Volatile
    private var held: List<HeldLease> = emptyList()
    private var renewHandle: AutoCloseable? = null
    private var deadlineHandle: AutoCloseable? = null

    val holding: Boolean get() = held.isNotEmpty()

    @Synchronized
    fun hold(leases: List<HeldLease>) {
        held = leases
        armDeadline()
        renewHandle = scheduler.schedulePeriodic(props.renewInterval) { renewOnce() }
    }

    /** Releases in reverse order. A second call releases nothing. */
    @Synchronized
    fun releaseAll() {
        renewHandle?.close()
        renewHandle = null
        deadlineHandle?.close()
        deadlineHandle = null
        val leases = held
        held = emptyList()
        leases.reversed().forEach { lease ->
            try {
                lease.release()
            } catch (error: Throwable) {
                log.warn("{} release failed. The lease expires on its own. Cause: {}", lease.label, error.message)
            }
        }
    }

    private fun armDeadline() {
        deadlineHandle?.close()
        deadlineHandle = scheduler.scheduleOnce(props.closeDeadline) {
            closeContext(
                "$subject was not renewed within ${props.closeDeadline.seconds}s. " +
                    "The lease may have expired. Closing the application context."
            )
        }
    }

    private fun renewOnce() {
        held.forEach { lease ->
            val result = try {
                lease.renew()
            } catch (error: Throwable) {
                log.warn("{} renew failed. Retrying at the next interval. Cause: {}", lease.label, error.message)
                return
            }
            when (result) {
                is ClaimResult.Granted -> Unit
                is ClaimResult.Denied -> {
                    closeContext(lease.deniedMessage(result.holder))
                    return
                }
                is ClaimResult.Lost -> {
                    closeContext(lease.lostMessage())
                    return
                }
                null -> {
                    log.warn("{} renew returned no result. Retrying at the next interval.", lease.label)
                    return
                }
            }
        }
        synchronized(this) { if (held.isNotEmpty()) armDeadline() }
    }

    // The close runs off the scheduler. A close started on a scheduler thread
    // would run destroy on that same thread, and destroy would wait for it.
    private fun closeContext(reason: String) {
        log.error(reason)
        scheduler.runDetached("lease-close") { context.close() }
    }
}
```

- [ ] **Step 4: Give `ExecutorClaimScheduler` a thread name parameter**

In `ClaimScheduler.kt`, change the class header and remove the `threadName` property line:

```kotlin
class ExecutorClaimScheduler(private val threadName: String = "nodeid-claim-renew") : ClaimScheduler {
```

Two guards each own a scheduler. `isSchedulerThread` compares the thread name, so the names must differ.

- [ ] **Step 5: Rewrite `NodeIdClaimGuard.kt`**

```kotlin
package com.demo.chat.domain

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.SmartLifecycle

/**
 * Holds the store-side lease on `app.nodeid` for the life of the process.
 *
 * The guard claims when its lifecycle phase starts, after the connection
 * factories and before `RootKeyStartup`. A duplicate therefore fails startup
 * before the application is ready and before the process serves normal
 * traffic.
 *
 * It releases in [stop], while the connection factories still run. It also
 * releases in [destroy], because a failed refresh may not run [stop]. See
 * `CHAT-ocpojbyy` and `CHAT-lswjobhz`.
 *
 * The guard is the only place that blocks. The stores stay reactive.
 */
class NodeIdClaimGuard(
    private val stores: List<NodeIdClaimStore>,
    private val nodeId: NodeId,
    private val owner: RuntimeOwnerId,
    private val props: NodeIdClaimProperties,
    context: ConfigurableApplicationContext,
    private val scheduler: ClaimScheduler
) : SmartLifecycle, DisposableBean {

    private val log = LoggerFactory.getLogger(NodeIdClaimGuard::class.java)
    private val keeper = LeaseKeeper("app.nodeid=${nodeId.value}", props, context, scheduler)

    @Volatile
    private var running = false

    override fun start() {
        if (stores.isEmpty()) {
            running = true
            return
        }

        val ordered = stores.sortedBy { it.backendName }
        val taken = mutableListOf<NodeIdClaimStore>()

        try {
            ordered.forEach { store ->
                when (val result = store.claim(nodeId, owner.value, props.ttl)
                    .timeout(props.operationTimeout).block()) {
                    is ClaimResult.Granted -> taken.add(store)
                    is ClaimResult.Denied ->
                        throw NodeIdClaimException(nodeId, store.scope, result.holder, props.ttl)
                    is ClaimResult.Lost ->
                        throw IllegalStateException(
                            "The ${store.backendName} store returned Lost from a claim. " +
                                "A claim returns Granted or Denied."
                        )
                    null ->
                        throw IllegalStateException(
                            "The ${store.backendName} store returned no result from a claim."
                        )
                }
            }
        } catch (failure: Throwable) {
            taken.reversed().forEach { lease(it).releaseQuietly() }
            throw failure
        }

        log.info("app.nodeid={} claimed in {}", nodeId.value, taken.joinToString { it.scope })
        keeper.hold(taken.map(::lease))
        running = true
    }

    override fun stop() {
        keeper.releaseAll()
        running = false
    }

    override fun isRunning(): Boolean = running

    override fun getPhase(): Int = LeasePhases.NODE_ID_GUARD

    override fun destroy() {
        keeper.releaseAll()
        scheduler.shutdownNow()
    }

    private fun lease(store: NodeIdClaimStore) = object : HeldLease {
        override val label = "app.nodeid=${nodeId.value} on ${store.backendName}"

        override fun renew(): ClaimResult? =
            store.renew(nodeId, owner.value, props.ttl).timeout(props.operationTimeout).block()

        override fun release() {
            store.release(nodeId, owner.value).timeout(props.operationTimeout).block()
        }

        override fun deniedMessage(holder: String): String =
            NodeIdClaimException(nodeId, store.scope, holder, props.ttl).message!!

        override fun lostMessage(): String =
            "app.nodeid=${nodeId.value} is no longer held in the ${store.scope}. " +
                "This process lost its lease. Closing the application context."
    }

    private fun HeldLease.releaseQuietly() {
        try {
            release()
        } catch (error: Throwable) {
            log.warn("{} release failed. The lease expires on its own. Cause: {}", label, error.message)
        }
    }
}
```

- [ ] **Step 6: Register the guard as a dependent of each store**

In `NodeIdClaimGuardConfiguration.nodeIdClaimGuard`, add a `beanFactory: ConfigurableListableBeanFactory` parameter. Before the `return`, register each store bean as a dependency, so Spring destroys the guard first:

```kotlin
    @Bean
    open fun nodeIdClaimGuard(
        stores: ObjectProvider<NodeIdClaimStore>,
        nodeId: NodeId,
        owner: RuntimeOwnerId,
        properties: NodeIdClaimProperties,
        context: ConfigurableApplicationContext,
        scheduler: ClaimScheduler,
        beanFactory: ConfigurableListableBeanFactory,
    ): NodeIdClaimGuard {
        // An ObjectProvider stream registers no dependency. Without one, Spring
        // can destroy a store's connection before the guard releases on a failed
        // start. See CHAT-lswjobhz.
        beanFactory.getBeanNamesForType(NodeIdClaimStore::class.java)
            .forEach { beanFactory.registerDependentBean(it, "nodeIdClaimGuard") }
        return NodeIdClaimGuard(
            stores.orderedStream().toList(), nodeId, owner, properties, context, scheduler
        )
    }
```

Import `org.springframework.beans.factory.config.ConfigurableListableBeanFactory`.

- [ ] **Step 7: Move the existing guard tests to `start`**

In `NodeIdClaimGuardStartupTests.kt` and `NodeIdClaimGuardRenewalTests.kt`, replace each `afterPropertiesSet()` call with `start()`. Leave each `destroy()` call. Measured on 2026-10-16: 13 such calls in the two files.

```bash
sed -i '' 's/\.afterPropertiesSet()/.start()/g' chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardStartupTests.kt chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardRenewalTests.kt
```

A renewal test that asserts a log level of a release failure now meets `warn`, not `debug`. Change the assertion if one exists, and record it in the commit message.

- [ ] **Step 8: Run the guard tests and the backend condition test**

```bash
mvn -o -pl chat-core test -Dtest='NodeIdClaimGuard*Tests,SharedBackendConditionTests' > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*NodeIdClaim|Tests run:.*SharedBackend|FAIL' $LOG
```

Expected: every class passes, with 5 tests in `NodeIdClaimGuardLifecycleTests`.

- [ ] **Step 9: Run the claim boot tests**

The claim moved from bean creation to a lifecycle phase. These tests prove that the failure still comes before ready.

```bash
mvn -o -Pintegration -pl chat-deploy-redis -am test -Dtest='RedisClaimBootTests,RedisGrantRestartTests' -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*(Claim|Grant)' $LOG
mvn -o -pl chat-deploy-memory -am test -Dtest=MemoryClaimAbsenceTests -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*Memory' $LOG
```

Expected: both exit 0.

- [ ] **Step 10: Measure the first key mint of a start**

The spec requires that the node id claim comes before any key mint. `RootKeyStartup` mints the root keys on an empty store. So a refused claim on an empty store must leave that store empty. Add this test to `RedisClaimBootTests`. It uses Redis database 7, which no other test uses.

```kotlin
    @Test
    fun `a refused claim on an empty store mints no key`() {
        val db7 = ReactiveStringRedisTemplate(
            LettuceConnectionFactory(
                RedisStandaloneConfiguration(container.containerIpAddress, container.getMappedPort(6379)).apply { database = 7 }
            ).apply { afterPropertiesSet() }
        )
        db7.execute { it.serverCommands().flushDb() }.blockLast()
        db7.opsForValue().setIfAbsent("chat:nodeclaim:long:11", "foreign-owner@host-z:1#deadbeef", Duration.ofSeconds(120)).block()

        Assertions.assertThrows(Exception::class.java) {
            boot(
                "app.nodeid=11",
                "app.service.core.key=redis",
                "app.service.core.persistence=redis",
                "spring.data.redis.database=7",
                ready = AtomicBoolean(false)
            ).run().close()
        }

        Assertions.assertEquals(listOf("chat:nodeclaim:long:11"), db7.keys("*").collectList().block())
    }
```

First prove that the deployment honors `spring.data.redis.database`. Boot once with `app.nodeid=12` and that property, close the context, and read `db7.keys("*")`. Expected: root keys are present. If no key is present, the deployment ignores the property. Then find the property that selects the database of the persistence template, and record it in the ledger.

```bash
mvn -o -Pintegration -pl chat-deploy-redis -am test -Dtest=RedisClaimBootTests -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*RedisClaimBoot' $LOG
```

Expected: the class passes.

- [ ] **Step 11: Commit, then measure two mutations, each alone**

Commit the task. Restore each mutation by absolute path.

| Mutation | Expected |
|---|---|
| Remove the `keeper.releaseAll()` line from `NodeIdClaimGuard.stop()` | `stop releases the lease` fails alone |
| Set `LeasePhases.NODE_ID_GUARD` to `Int.MAX_VALUE - 3000`, after `RootKeyStartup` | `a refused claim on an empty store mints no key` fails |

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/LeaseKeeper.kt chat-core/src/main/kotlin/com/demo/chat/domain/ClaimScheduler.kt chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimGuard.kt chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimGuardConfiguration.kt chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardLifecycleTests.kt chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardStartupTests.kt chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardRenewalTests.kt chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisClaimBootTests.kt
git commit -m "CHAT-lswjobhz: Run the node id guard as a lifecycle with one renew loop"
fp comment CHAT-lswjobhz "Task 1 done: LeaseKeeper, LeasePhases, and the node id guard as a SmartLifecycle."
```

---

### Task 2: The service lease guard

**Files:** see the File Map rows for Task 2.

**Interfaces:**
- Consumes: `HeldLease`, `LeaseKeeper`, `LeasePhases` from Task 1.
- Produces: `interface ServiceLeaseStore { val backendName: String; val scope: String; fun claim(name: String, owner: String, ttl: Duration): Mono<ClaimResult>; fun renew(name: String, owner: String, ttl: Duration): Mono<ClaimResult>; fun release(name: String, owner: String): Mono<Void> }`. `object ServiceLeaseNames { val DOMAINS: List<String>; fun of(persistence: Boolean, index: Boolean): List<String> }`. `class ServiceLeaseException(name, scope, holder, ttl)`. `class ServiceLeaseGuard(store, names: () -> List<String>, nodeId, owner, props, context, scheduler) : SmartLifecycle, DisposableBean`. The holder text is `node <id> <runtime owner id>`.

- [ ] **Step 1: Write the failing guard tests**

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import reactor.core.publisher.Mono
import java.time.Duration

/** The store service lease guard. See `CHAT-lswjobhz`. */
class ServiceLeaseGuardTests {

    /** A store whose answers the test sets, per lease name. */
    private class FakeLeaseStore : ServiceLeaseStore {
        override val backendName = "redis"
        override val scope = "redis store for key type long"
        val calls = mutableListOf<String>()
        val denied = mutableMapOf<String, String>()
        var renewAnswer: () -> ClaimResult = { ClaimResult.Granted }

        override fun claim(name: String, owner: String, ttl: Duration): Mono<ClaimResult> = Mono.fromCallable {
            calls.add("claim:$name")
            denied[name]?.let { ClaimResult.Denied(it) } ?: ClaimResult.Granted
        }

        override fun renew(name: String, owner: String, ttl: Duration): Mono<ClaimResult> =
            Mono.fromCallable { calls.add("renew:$name"); renewAnswer() }

        override fun release(name: String, owner: String): Mono<Void> =
            Mono.fromRunnable { calls.add("release:$name") }
    }

    private val store = FakeLeaseStore()
    private val scheduler = ManualClaimScheduler()
    private val context = GenericApplicationContext().apply { refresh() }
    private val owner = RuntimeOwnerId.generate("test")

    private fun guard(names: List<String> = ServiceLeaseNames.of(persistence = true, index = true)) = ServiceLeaseGuard(
        store, { names }, NodeId(7), owner, NodeIdClaimProperties(null, null, null, null), context, scheduler,
    )

    @Test
    fun `the names cover six domains for each kind present`() {
        assertThat(ServiceLeaseNames.of(persistence = true, index = false))
            .containsExactly("persistence:authmeta", "persistence:keyvalue", "persistence:membership",
                "persistence:message", "persistence:topic", "persistence:user")
        assertThat(ServiceLeaseNames.of(persistence = true, index = true)).hasSize(12)
        assertThat(ServiceLeaseNames.of(persistence = false, index = false)).isEmpty()
    }

    @Test
    fun `the guard claims every name in sorted order`() {
        guard(listOf("persistence:user", "index:user")).start()
        assertThat(store.calls).containsExactly("claim:index:user", "claim:persistence:user")
    }

    @Test
    fun `a refused claim releases the leases already taken and names the service, scope and holder`() {
        store.denied["index:user"] = "node 9 other@host:1#abc"
        val guard = guard(listOf("index:topic", "index:user", "persistence:user"))

        assertThatThrownBy { guard.start() }
            .isInstanceOf(ServiceLeaseException::class.java)
            .hasMessageContaining("index:user")
            .hasMessageContaining("redis store for key type long")
            .hasMessageContaining("node 9 other@host:1#abc")
        assertThat(store.calls).containsExactly("claim:index:topic", "claim:index:user", "release:index:topic")
        assertThat(guard.isRunning).isFalse()
    }

    @Test
    fun `the holder names the node id and the runtime owner`() {
        val owners = mutableListOf<String>()
        val recording = object : ServiceLeaseStore by store {
            override fun claim(name: String, owner: String, ttl: Duration): Mono<ClaimResult> {
                owners.add(owner)
                return store.claim(name, owner, ttl)
            }
        }
        ServiceLeaseGuard(recording, { listOf("index:user") }, NodeId(7), owner,
            NodeIdClaimProperties(null, null, null, null), context, scheduler).start()
        assertThat(owners.single()).isEqualTo("node 7 ${owner.value}")
    }

    @Test
    fun `stop releases in reverse order, and destroy after stop releases nothing`() {
        val guard = guard(listOf("index:user", "persistence:user"))
        guard.start()
        guard.stop()
        guard.destroy()
        assertThat(store.calls.filter { it.startsWith("release") })
            .containsExactly("release:persistence:user", "release:index:user")
    }

    @Test
    fun `destroy releases when stop did not run`() {
        val guard = guard(listOf("index:user"))
        guard.start()
        guard.destroy()
        assertThat(store.calls).containsExactly("claim:index:user", "release:index:user")
    }

    @Test
    fun `a lost lease closes the context`() {
        guard(listOf("index:user")).start()
        store.renewAnswer = { ClaimResult.Lost }
        scheduler.firePeriodic()
        assertThat(scheduler.detached).containsExactly("lease-close")
        assertThat(context.isActive).isFalse()
    }

    @Test
    fun `a missed deadline closes the context`() {
        guard(listOf("index:user")).start()
        assertThat(scheduler.onceDelay).isEqualTo(Duration.ofSeconds(25))
        scheduler.fireOnce()
        assertThat(context.isActive).isFalse()
    }

    @Test
    fun `no names means no claim and a running guard`() {
        val guard = guard(emptyList())
        guard.start()
        assertThat(store.calls).isEmpty()
        assertThat(guard.isRunning).isTrue()
        assertThat(guard.phase).isEqualTo(LeasePhases.SERVICE_GUARD)
    }
}
```

- [ ] **Step 2: Run them and see them fail**

```bash
mvn -o -pl chat-core test -Dtest=ServiceLeaseGuardTests > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:|e: file' $LOG | head
```

Expected: a compile failure on `ServiceLeaseStore`.

- [ ] **Step 3: Write `ServiceLease.kt`**

```kotlin
package com.demo.chat.domain

import reactor.core.publisher.Mono
import java.time.Duration

/**
 * A store-side lease on one store service, such as `index:user`. See
 * `CHAT-lswjobhz` and `docs/SERVICE-LEASE.md`.
 *
 * The lease lives in the persistence backend. Implementations return a
 * [Mono]. The guard is the only place that blocks.
 */
interface ServiceLeaseStore {
    /** `redis` or `cassandra`. */
    val backendName: String

    /** The full phrase that names the space this lease covers. */
    val scope: String

    /** Returns [ClaimResult.Granted] or [ClaimResult.Denied]. */
    fun claim(name: String, owner: String, ttl: Duration): Mono<ClaimResult>

    fun renew(name: String, owner: String, ttl: Duration): Mono<ClaimResult>

    /** Drops the lease when this owner holds it. */
    fun release(name: String, owner: String): Mono<Void>
}

/** The lease names of the store services. */
object ServiceLeaseNames {
    /** The domains of `PersistenceServiceBeans` and `IndexServiceBeans`. */
    val DOMAINS = listOf("authmeta", "keyvalue", "membership", "message", "topic", "user")

    /** The names for the service kinds that a process creates, in sorted order. */
    fun of(persistence: Boolean, index: Boolean): List<String> =
        (if (index) DOMAINS.map { "index:$it" } else emptyList()) +
            (if (persistence) DOMAINS.map { "persistence:$it" } else emptyList())
}

/** Reports a store service that another process holds. */
class ServiceLeaseException(
    val name: String,
    val scope: String,
    val holder: String,
    val ttl: Duration,
) : RuntimeException(message(name, scope, holder, ttl)) {
    companion object {
        fun message(name: String, scope: String, holder: String, ttl: Duration): String =
            "The store service $name is already held in the $scope.\n" +
                "Holder: $holder\n" +
                "A store service has one live instance on a shared backend. See docs/SERVICE-LEASE.md.\n" +
                "Stop the other deployment, and wait ${ttl.seconds}s for its lease to expire."
    }
}
```

- [ ] **Step 4: Write `ServiceLeaseGuard.kt`**

```kotlin
package com.demo.chat.domain

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.SmartLifecycle

/**
 * Holds one lease for each store service that this process creates. See
 * `CHAT-lswjobhz`.
 *
 * It claims after the node id guard and before `RootKeyStartup`, so it holds
 * the leases before any start scan reads the store. It releases in [stop] and,
 * after a failed refresh, in [destroy].
 *
 * [names] is read at start, when every service bean exists.
 */
class ServiceLeaseGuard(
    private val store: ServiceLeaseStore,
    private val names: () -> List<String>,
    private val nodeId: NodeId,
    owner: RuntimeOwnerId,
    private val props: NodeIdClaimProperties,
    context: ConfigurableApplicationContext,
    private val scheduler: ClaimScheduler,
) : SmartLifecycle, DisposableBean {

    private val log = LoggerFactory.getLogger(ServiceLeaseGuard::class.java)
    private val holder = "node ${nodeId.value} ${owner.value}"
    private val keeper = LeaseKeeper("The store service leases of app.nodeid=${nodeId.value}", props, context, scheduler)

    @Volatile
    private var running = false

    override fun start() {
        val taken = mutableListOf<String>()
        try {
            names().sorted().forEach { name ->
                when (val result = store.claim(name, holder, props.ttl).timeout(props.operationTimeout).block()) {
                    is ClaimResult.Granted -> taken.add(name)
                    is ClaimResult.Denied -> throw ServiceLeaseException(name, store.scope, result.holder, props.ttl)
                    is ClaimResult.Lost, null -> throw IllegalStateException(
                        "The ${store.backendName} store returned $result from a claim of $name. " +
                            "A claim returns Granted or Denied."
                    )
                }
            }
        } catch (failure: Throwable) {
            taken.reversed().forEach { name ->
                try {
                    lease(name).release()
                } catch (error: Throwable) {
                    log.warn("{} release failed. The lease expires on its own. Cause: {}", name, error.message)
                }
            }
            throw failure
        }
        if (taken.isNotEmpty()) {
            log.info("Store service leases {} claimed in the {}", taken, store.scope)
            keeper.hold(taken.map(::lease))
        }
        running = true
    }

    override fun stop() {
        keeper.releaseAll()
        running = false
    }

    override fun isRunning(): Boolean = running

    override fun getPhase(): Int = LeasePhases.SERVICE_GUARD

    override fun destroy() {
        keeper.releaseAll()
        scheduler.shutdownNow()
    }

    private fun lease(name: String) = object : HeldLease {
        override val label = "The store service $name"

        override fun renew(): ClaimResult? =
            store.renew(name, holder, props.ttl).timeout(props.operationTimeout).block()

        override fun release() {
            store.release(name, holder).timeout(props.operationTimeout).block()
        }

        override fun deniedMessage(holder: String): String =
            ServiceLeaseException(name, store.scope, holder, props.ttl).message!!

        override fun lostMessage(): String =
            "The store service $name is no longer held in the ${store.scope}. " +
                "This process lost its lease. Closing the application context."
    }
}
```

- [ ] **Step 5: Run the tests**

```bash
mvn -o -pl chat-core test -Dtest='ServiceLeaseGuardTests,NodeIdClaimGuard*Tests' > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*(ServiceLease|NodeIdClaim)' $LOG
```

Expected: `ServiceLeaseGuardTests` 9 of 9, and every node id guard class passes.

- [ ] **Step 6: Commit, then measure one mutation**

Commit. Then delete the `taken.reversed().forEach` release block in `start`, and run `ServiceLeaseGuardTests`. Expected: only the refused claim case fails. Restore by absolute path.

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/ServiceLease.kt chat-core/src/main/kotlin/com/demo/chat/domain/ServiceLeaseGuard.kt chat-core/src/test/kotlin/com/demo/chat/test/domain/ServiceLeaseGuardTests.kt
git commit -m "CHAT-lswjobhz: Add the store service lease guard"
fp comment CHAT-lswjobhz "Task 2 done: ServiceLeaseStore, ServiceLeaseNames and ServiceLeaseGuard."
```

---

### Task 3: The Redis lease store

**Files:** see the File Map rows for Task 3.

**Interfaces:**
- Consumes: `ServiceLeaseStore`, `ServiceLeaseGuard`, `ServiceLeaseNames` from Task 2. `NodeIdClaimGuardConfiguration` supplies `NodeId`, `RuntimeOwnerId` and `NodeIdClaimProperties`.
- Produces: bean `redisServiceLeaseStore` and bean `serviceLeaseGuard`, active when `app.service.core.persistence=redis`.

- [ ] **Step 1: Write the failing contract tests**

Create `RedisServiceLeaseStoreTests.kt`. Copy the class annotations, the constructor and the `companion object` of `RedisNodeIdClaimStoreTests.kt` exactly. They start the container and set the connection properties. Then add these tests. Each test uses its own lease names, because the class shares one container.

```kotlin
    private fun store(keyType: String = "long") = RedisServiceLeaseStore(stringTemplate, keyType)
    private val long = Duration.ofSeconds(30)

    @Test
    fun `a claim is granted, and a second owner is denied with the holder named`() {
        StepVerifier.create(store().claim("index:contract-a", "owner-1", long)).expectNext(ClaimResult.Granted).verifyComplete()
        StepVerifier.create(store().claim("index:contract-a", "owner-2", long))
            .expectNext(ClaimResult.Denied("owner-1")).verifyComplete()
    }

    @Test
    fun `the owner renews, and another owner is denied`() {
        store().claim("index:contract-b", "owner-1", long).block()
        StepVerifier.create(store().renew("index:contract-b", "owner-1", long)).expectNext(ClaimResult.Granted).verifyComplete()
        StepVerifier.create(store().renew("index:contract-b", "owner-2", long))
            .expectNext(ClaimResult.Denied("owner-1")).verifyComplete()
    }

    @Test
    fun `a release by another owner keeps the lease`() {
        store().claim("index:contract-c", "owner-1", long).block()
        store().release("index:contract-c", "owner-2").block()
        StepVerifier.create(store().claim("index:contract-c", "owner-3", long))
            .expectNext(ClaimResult.Denied("owner-1")).verifyComplete()
        store().release("index:contract-c", "owner-1").block()
        StepVerifier.create(store().claim("index:contract-c", "owner-3", long)).expectNext(ClaimResult.Granted).verifyComplete()
    }

    @Test
    fun `an expired lease reports lost to its owner and admits a new owner`() {
        store().claim("index:contract-d", "owner-1", Duration.ofSeconds(1)).block()
        Thread.sleep(1500)
        StepVerifier.create(store().renew("index:contract-d", "owner-1", long)).expectNext(ClaimResult.Lost).verifyComplete()
        StepVerifier.create(store().claim("index:contract-d", "owner-2", long)).expectNext(ClaimResult.Granted).verifyComplete()
    }

    @Test
    fun `two key types hold separate leases on one Redis`() {
        StepVerifier.create(store("long").claim("index:contract-e", "owner-1", long)).expectNext(ClaimResult.Granted).verifyComplete()
        StepVerifier.create(store("uuid").claim("index:contract-e", "owner-2", long)).expectNext(ClaimResult.Granted).verifyComplete()
    }

    @Test
    fun `the lease key carries the key type and the name`() {
        store().claim("index:contract-f", "owner-1", long).block()
        assertThat(stringTemplate.hasKey("chat:lease:long:index:contract-f").block()).isTrue()
    }
```

- [ ] **Step 2: Run them and see them fail**

```bash
mvn -o -Pintegration -pl chat-core,chat-persistence-redis test -Dtest=RedisServiceLeaseStoreTests > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:|e: file' $LOG | head
```

Expected: a compile failure on `RedisServiceLeaseStore`.

- [ ] **Step 3: Move the Lua scripts to one place**

Create `RedisLeaseScripts.kt` with the two scripts of `RedisNodeIdClaimStore` and its reply mapping:

```kotlin
package com.demo.chat.persistence.redis.impl

import com.demo.chat.domain.ClaimResult
import org.springframework.data.redis.core.script.RedisScript

/** The compare and set scripts of the Redis leases. The node id and service leases share them. */
object RedisLeaseScripts {
    // Lua gives the compare and set that plain commands cannot. The reply is
    // one string, because ReactiveStringRedisTemplate carries a string
    // serializer and a mixed Lua array would need a mixed result type.
    val renew: RedisScript<String> = RedisScript.of(
        """
        local v = redis.call('GET', KEYS[1])
        if v == false then return 'lost' end
        if v == ARGV[1] then
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 'granted'
        end
        return 'denied:' .. v
        """.trimIndent(),
        String::class.java
    )

    val release: RedisScript<String> = RedisScript.of(
        """
        local v = redis.call('GET', KEYS[1])
        if v == false then return 'lost' end
        if v == ARGV[1] then
            redis.call('DEL', KEYS[1])
            return 'granted'
        end
        return 'denied:' .. v
        """.trimIndent(),
        String::class.java
    )

    fun result(reply: String, scope: String): ClaimResult = when {
        reply == "granted" -> ClaimResult.Granted
        reply == "lost" -> ClaimResult.Lost
        reply.startsWith("denied:") -> ClaimResult.Denied(reply.removePrefix("denied:"))
        else -> throw IllegalStateException("The $scope returned an unknown claim reply: $reply")
    }
}
```

In `RedisNodeIdClaimStore`, delete the `companion object`, use `RedisLeaseScripts.renew` and `RedisLeaseScripts.release`, and replace the `when` in `run` with `RedisLeaseScripts.result(reply, scope)`.

- [ ] **Step 4: Write `RedisServiceLeaseStore.kt`**

```kotlin
package com.demo.chat.persistence.redis.impl

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.ServiceLeaseStore
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * The Redis store service lease. See `CHAT-lswjobhz`.
 *
 * The key carries the key type, as the node id lease does. `PX` makes the
 * Redis server the clock.
 */
class RedisServiceLeaseStore(
    private val template: ReactiveStringRedisTemplate,
    private val keyType: String,
) : ServiceLeaseStore {

    override val backendName: String = "redis"

    override val scope: String = "redis store for key type $keyType"

    private fun key(name: String): String = "chat:lease:$keyType:$name"

    override fun claim(name: String, owner: String, ttl: Duration): Mono<ClaimResult> =
        attempt(name, owner, ttl)
            // The holder vanished between the write and the read. Try once more.
            .switchIfEmpty(attempt(name, owner, ttl))
            .switchIfEmpty(Mono.error(IllegalStateException("The $scope denied $name twice and named no holder.")))

    override fun renew(name: String, owner: String, ttl: Duration): Mono<ClaimResult> =
        run(RedisLeaseScripts.renew, name, owner, ttl.toMillis().toString())

    override fun release(name: String, owner: String): Mono<Void> =
        run(RedisLeaseScripts.release, name, owner, "0").then()

    private fun attempt(name: String, owner: String, ttl: Duration): Mono<ClaimResult> =
        template.opsForValue().setIfAbsent(key(name), owner, ttl).flatMap { taken ->
            if (taken) Mono.just(ClaimResult.Granted as ClaimResult)
            else template.opsForValue().get(key(name)).map { holder -> ClaimResult.Denied(holder) as ClaimResult }
        }

    private fun run(script: RedisScript<String>, name: String, owner: String, millis: String): Mono<ClaimResult> =
        template.execute(script, listOf(key(name)), listOf(owner, millis)).next()
            .map { RedisLeaseScripts.result(it, scope) }
}
```

- [ ] **Step 5: Write `ServiceLeaseConfiguration.kt` for Redis**

```kotlin
package com.demo.chat.config.persistence.redis

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.persistence.redis.impl.RedisServiceLeaseStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * Registers the Redis store service lease and its guard. See `CHAT-lswjobhz`.
 *
 * A lease lives in the persistence backend, so the condition reads
 * `app.service.core.persistence` alone. The guard takes the store as a
 * constructor parameter, so Spring destroys the guard before the store.
 */
@Configuration("redisServiceLeaseConfiguration")
@ConditionalOnProperty(name = ["app.service.core.persistence"], havingValue = "redis")
@Import(NodeIdClaimGuardConfiguration::class)
class ServiceLeaseConfiguration {

    @Bean("redisServiceLeaseStore")
    fun redisServiceLeaseStore(
        template: ReactiveStringRedisTemplate,
        @Value("\${app.key.type}") keyType: String,
    ): ServiceLeaseStore = RedisServiceLeaseStore(template, keyType)

    @Bean("serviceLeaseGuard")
    fun serviceLeaseGuard(
        @Qualifier("redisServiceLeaseStore") store: ServiceLeaseStore,
        persistence: ObjectProvider<PersistenceServiceBeans<*, *>>,
        index: ObjectProvider<IndexServiceBeans<*, *, *>>,
        nodeId: NodeId,
        owner: RuntimeOwnerId,
        properties: NodeIdClaimProperties,
        context: ConfigurableApplicationContext,
    ): ServiceLeaseGuard = ServiceLeaseGuard(
        store,
        { ServiceLeaseNames.of(persistence.stream().findAny().isPresent, index.stream().findAny().isPresent) },
        nodeId, owner, properties, context, ExecutorClaimScheduler("service-lease-renew"),
    )
}
```

- [ ] **Step 6: Run the contract tests and the node id store tests**

```bash
mvn -o -Pintegration -pl chat-core,chat-persistence-redis test -Dtest='RedisServiceLeaseStoreTests,RedisNodeIdClaimStoreTests' > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*Redis' $LOG
```

Expected: `RedisServiceLeaseStoreTests` 6 of 6, and `RedisNodeIdClaimStoreTests` passes with no change.

- [ ] **Step 7: Pin the key-only Redis case at boot**

Review Focus 5. Add one test to `RedisClaimBootTests`, beside `a memory key selector with redis persistence still claims`:

```kotlin
    @Test
    fun `a redis key selector with memory persistence claims a node id and no service lease`() {
        val ready = AtomicBoolean(false)

        boot(
            "app.nodeid=12",
            "app.service.core.key=redis",
            "app.service.core.persistence=memory",
            ready = ready
        ).run().use { context ->
            Assertions.assertEquals(1, context.getBeansOfType(NodeIdClaimStore::class.java).size)
            Assertions.assertTrue(context.getBeansOfType(ServiceLeaseStore::class.java).isEmpty())
        }
    }
```

Node id 12 belongs to this class, and the two tests that use it close their context before the next start. Import `com.demo.chat.domain.ServiceLeaseStore`.

```bash
mvn -o -Pintegration -pl chat-deploy-redis -am test -Dtest=RedisClaimBootTests -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*RedisClaimBoot' $LOG
```

Expected: 3 of 3.

- [ ] **Step 8: Commit**

```bash
git add chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisLeaseScripts.kt chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisNodeIdClaimStore.kt chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisServiceLeaseStore.kt chat-persistence-redis/src/main/kotlin/com/demo/chat/config/persistence/redis/ServiceLeaseConfiguration.kt chat-persistence-redis/src/test/kotlin/com/demo/chat/test/persistence/redis/RedisServiceLeaseStoreTests.kt chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisClaimBootTests.kt
git commit -m "CHAT-lswjobhz: Add the Redis store service lease"
fp comment CHAT-lswjobhz "Task 3 done: RedisServiceLeaseStore, its configuration, and the contract tests."
```

---

### Task 4: The Cassandra lease store

**Files:** see the File Map rows for Task 4.

**Interfaces:**
- Consumes: Task 2 types. `NodeIdClaimConfiguration.keyspaceOf(properties)` in `chat-persistence-cassandra`.
- Produces: bean `cassandraServiceLeaseStore` and bean `serviceLeaseGuard`, active when `app.service.core.persistence=cassandra`. Table `service_lease` in both keyspace scripts.

- [ ] **Step 1: Write the failing contract tests**

Create `CassandraServiceLeaseStoreTests.kt`. Copy the class annotations, the constructor and the `companion object` of `CassandraNodeIdClaimStoreTests.kt` exactly. Name the template parameter as that file names it. The tests below call it `template`.

```kotlin
    private fun store(table: String = "service_lease") = CassandraServiceLeaseStore(template, "chat_long", table)
    private val long = Duration.ofSeconds(30)

    @Test
    fun `a claim is granted, and a second owner is denied with the holder named`() {
        StepVerifier.create(store().claim("index:contract-a", "owner-1", long)).expectNext(ClaimResult.Granted).verifyComplete()
        StepVerifier.create(store().claim("index:contract-a", "owner-2", long))
            .expectNext(ClaimResult.Denied("owner-1")).verifyComplete()
    }

    @Test
    fun `the owner renews, and another owner is denied`() {
        store().claim("index:contract-b", "owner-1", long).block()
        StepVerifier.create(store().renew("index:contract-b", "owner-1", long)).expectNext(ClaimResult.Granted).verifyComplete()
        StepVerifier.create(store().renew("index:contract-b", "owner-2", long))
            .expectNext(ClaimResult.Denied("owner-1")).verifyComplete()
    }

    @Test
    fun `a release by another owner keeps the lease`() {
        store().claim("index:contract-c", "owner-1", long).block()
        store().release("index:contract-c", "owner-2").block()
        StepVerifier.create(store().claim("index:contract-c", "owner-3", long))
            .expectNext(ClaimResult.Denied("owner-1")).verifyComplete()
        store().release("index:contract-c", "owner-1").block()
        StepVerifier.create(store().claim("index:contract-c", "owner-3", long)).expectNext(ClaimResult.Granted).verifyComplete()
    }

    @Test
    fun `an expired lease reports lost to its owner and admits a new owner`() {
        store().claim("index:contract-d", "owner-1", Duration.ofSeconds(1)).block()
        Thread.sleep(1500)
        StepVerifier.create(store().renew("index:contract-d", "owner-1", long)).expectNext(ClaimResult.Lost).verifyComplete()
        StepVerifier.create(store().claim("index:contract-d", "owner-2", long)).expectNext(ClaimResult.Granted).verifyComplete()
    }

    @Test
    fun `a keyspace with no service_lease table names the script to apply`() {
        StepVerifier.create(store("service_lease_absent").claim("index:contract-e", "owner-1", long))
            .expectErrorSatisfies {
                assertThat(it.message).contains("has no service_lease_absent table").contains("keyspace-long.cql")
            }.verify()
    }
```

- [ ] **Step 2: Run them and see them fail**

```bash
mvn -o -Pintegration -pl chat-core,shared-resources-cassandra,chat-persistence-cassandra test -Dtest=CassandraServiceLeaseStoreTests > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:|e: file' $LOG | head
```

Expected: a compile failure on `CassandraServiceLeaseStore`.

- [ ] **Step 3: Add the table to both keyspace scripts**

Read both scripts first. Each changed after the branch split. In `keyspace-long.cql`, add this block after the `node_claim` table. In `keyspace-uuid.cql`, add the same block with `chat_uuid` and `truncate-uuid.cql`.

```sql
-- Holds the store service leases, such as index:user. One row per claimed
-- service. The row carries a TTL, so a crashed deployment releases its
-- services. This table is deliberately absent from truncate-long.cql. A live
-- lease must expire. A test cleanup script must not delete it. See
-- docs/SERVICE-LEASE.md and CHAT-lswjobhz.
CREATE TABLE chat_long.service_lease(
    name     text,
    owner_id text,
    PRIMARY KEY(name)
);
```

- [ ] **Step 4: Write `CassandraServiceLeaseStore.kt`**

Copy `CassandraNodeIdClaimStore` and change the statements, the key column and the messages:

```kotlin
package com.demo.chat.persistence.cassandra.impl

import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException
import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.ServiceLeaseStore
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * The Cassandra store service lease. See `CHAT-lswjobhz`.
 *
 * A lightweight transaction runs at SERIAL, and the coordinator applies the
 * TTL. The lease lives in the session keyspace, so `chat_long` and
 * `chat_uuid` hold separate leases.
 */
class CassandraServiceLeaseStore(
    private val template: ReactiveCassandraTemplate,
    private val keyspace: String,
    private val table: String = "service_lease",
) : ServiceLeaseStore {

    override val backendName: String = "cassandra"

    override val scope: String = "cassandra keyspace $keyspace"

    private val cql get() = template.reactiveCqlOperations

    private val schemaFile: String = "keyspace-" + keyspace.removePrefix("chat_") + ".cql"

    override fun claim(name: String, owner: String, ttl: Duration): Mono<ClaimResult> =
        cql.queryForRows("INSERT INTO $table (name, owner_id) VALUES (?, ?) IF NOT EXISTS USING TTL ?", name, owner, ttl.seconds.toInt())
            .next().map { row ->
                if (row.getBoolean("[applied]")) ClaimResult.Granted as ClaimResult
                else ClaimResult.Denied(holderOf(row) ?: throw IllegalStateException("The $scope denied $name and named no holder."))
            }.onErrorMap { mapMissingTable(it) }

    override fun renew(name: String, owner: String, ttl: Duration): Mono<ClaimResult> =
        cql.queryForRows("UPDATE $table USING TTL ? SET owner_id = ? WHERE name = ? IF owner_id = ?", ttl.seconds.toInt(), owner, name, owner)
            .next().map { row ->
                when {
                    row.getBoolean("[applied]") -> ClaimResult.Granted as ClaimResult
                    holderOf(row) == null -> ClaimResult.Lost
                    else -> ClaimResult.Denied(holderOf(row)!!)
                }
            }.onErrorMap { mapMissingTable(it) }

    override fun release(name: String, owner: String): Mono<Void> =
        cql.queryForRows("DELETE FROM $table WHERE name = ? IF owner_id = ?", name, owner).next().then()
            .onErrorMap { mapMissingTable(it) }

    /** A transaction that found no row returns `[applied]` alone. Test for the column. */
    private fun holderOf(row: Row): String? =
        if (row.columnDefinitions.contains("owner_id")) row.getString("owner_id") else null

    private fun mapMissingTable(error: Throwable): Throwable =
        if (generateSequence(error) { it.cause }.any { it is InvalidQueryException && it.message?.contains("does not exist") == true })
            IllegalStateException(
                "The $scope has no $table table. Recreate the store from $schemaFile in shared-resources-cassandra. " +
                    "See docs/SERVICE-LEASE.md.",
                error
            )
        else error
}
```

- [ ] **Step 5: Write `ServiceLeaseConfiguration.kt` for Cassandra**

```kotlin
package com.demo.chat.config.persistence.cassandra

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.persistence.cassandra.impl.CassandraServiceLeaseStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.cassandra.autoconfigure.CassandraProperties
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate

/** Registers the Cassandra store service lease and its guard. See `CHAT-lswjobhz`. */
@Configuration("cassandraServiceLeaseConfiguration")
@ConditionalOnProperty(name = ["app.service.core.persistence"], havingValue = "cassandra")
@Import(NodeIdClaimGuardConfiguration::class)
class ServiceLeaseConfiguration {

    @Bean("cassandraServiceLeaseStore")
    fun cassandraServiceLeaseStore(template: ReactiveCassandraTemplate, properties: CassandraProperties): ServiceLeaseStore =
        CassandraServiceLeaseStore(template, NodeIdClaimConfiguration.keyspaceOf(properties))

    @Bean("serviceLeaseGuard")
    fun serviceLeaseGuard(
        @Qualifier("cassandraServiceLeaseStore") store: ServiceLeaseStore,
        persistence: ObjectProvider<PersistenceServiceBeans<*, *>>,
        index: ObjectProvider<IndexServiceBeans<*, *, *>>,
        nodeId: NodeId,
        owner: RuntimeOwnerId,
        properties: NodeIdClaimProperties,
        context: ConfigurableApplicationContext,
    ): ServiceLeaseGuard = ServiceLeaseGuard(
        store,
        { ServiceLeaseNames.of(persistence.stream().findAny().isPresent, index.stream().findAny().isPresent) },
        nodeId, owner, properties, context, ExecutorClaimScheduler("service-lease-renew"),
    )
}
```

Copy the `CassandraProperties` import from `NodeIdClaimConfiguration.kt` of the same package. Its package can differ from the line above.

- [ ] **Step 6: Require the table in the shape check**

In `CassandraStoreShapeCheck.PERSISTENCE_TABLES`, add:

```kotlin
            // The store service leases. See CHAT-lswjobhz.
            "service_lease" to setOf("name", "owner_id"),
```

The guard claims before the shape check runs, so the claim error from Step 4 reaches an operator first. The shape check still pins the table for a process that claims nothing.

- [ ] **Step 7: Run the store tests and the shape check tests**

```bash
mvn -o -Pintegration -pl chat-core,shared-resources-cassandra,chat-persistence-cassandra test -Dtest='CassandraServiceLeaseStoreTests,CassandraNodeIdClaimStoreTests,CassandraStoreShapeCheckTests' > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*Cassandra' $LOG
```

Expected: `CassandraServiceLeaseStoreTests` 5 of 5, and the two other classes pass. `a complete keyspace passes` proves that both scripts satisfy the new requirement.

- [ ] **Step 8: Commit**

```bash
git add shared-resources-cassandra/src/main/resources/keyspace-long.cql shared-resources-cassandra/src/main/resources/keyspace-uuid.cql chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraServiceLeaseStore.kt chat-persistence-cassandra/src/main/kotlin/com/demo/chat/config/persistence/cassandra/ServiceLeaseConfiguration.kt chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraStoreShapeCheck.kt chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/CassandraServiceLeaseStoreTests.kt
git commit -m "CHAT-lswjobhz: Add the Cassandra store service lease"
fp comment CHAT-lswjobhz "Task 4 done: the service_lease table, CassandraServiceLeaseStore, and the contract tests."
```

---

### Task 5: Writers stop before the release

**Files:** see the File Map rows for Task 5.

**Interfaces:**
- Consumes: `LeasePhases.WRITERS` from Task 1.
- Produces: `class CommandRuntimeLifecycle(runtime: MemoryCommandRuntime<*, *>) : SmartLifecycle` at phase `LeasePhases.WRITERS`.

**The writer inventory, measured on 2026-10-16.** The core process of the Redis and Cassandra boot tests runs `app.service.composite` with `app.command.bus=memory`. In main code, one background loop writes a store on its own schedule: the rescan of `CommandDispatcher`, inside `MemoryCommandRuntime`. Spring closes the runtime at bean destruction, through `destroyMethod = "close"`. That is after the guards release. The other `boundedElastic` work (`SerialWriter`, `VectorStoreMessageVectorIndexer`, `MessageReindexServiceImpl`, `RoomPublications`) runs per request. Review Focus 1 holds it. The Lucene mutation scheduler writes local index files, not the shared store.

- [ ] **Step 1: Write the failing tests**

`CommandRuntimeLifecycleTests.kt`:

```kotlin
package com.demo.chat.test.service.composite.command

import com.demo.chat.domain.LeasePhases
import com.demo.chat.service.composite.command.memory.CommandRuntimeLifecycle
import com.demo.chat.service.composite.command.memory.MemoryCommandRuntime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

/** The command runtime stops before the lease guards release. See `CHAT-lswjobhz`. */
class CommandRuntimeLifecycleTests {

    @Test
    fun `stop closes the runtime in the writers phase`() {
        val runtime = mock(MemoryCommandRuntime::class.java)
        val lifecycle = CommandRuntimeLifecycle(runtime)
        lifecycle.start()
        assertThat(lifecycle.isRunning).isTrue()

        lifecycle.stop()

        verify(runtime).close()
        assertThat(lifecycle.isRunning).isFalse()
        assertThat(lifecycle.phase).isEqualTo(LeasePhases.WRITERS)
    }
}
```

`LeasePhaseOrderTests.kt` in `chat-deploy`, which sees every phase:

```kotlin
package com.demo.chat.test.deploy

import com.demo.chat.config.deploy.init.RootKeyStartup
import com.demo.chat.domain.LeasePhases
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory

/** Pins the start and stop order of the spec. See `CHAT-lswjobhz`. */
class LeasePhaseOrderTests {

    @Test
    fun `the backend, the guards, the writers and the start scan start in that order`() {
        val phases = listOf(
            LettuceConnectionFactory().phase,
            LeasePhases.NODE_ID_GUARD,
            LeasePhases.SERVICE_GUARD,
            LeasePhases.WRITERS,
            RootKeyStartup.PHASE,
            Int.MAX_VALUE - 2048,
        )
        assertThat(phases).isSorted().doesNotHaveDuplicates()
        assertThat(phases.first()).isEqualTo(0)
    }
}
```

`chat-deploy` must see `spring-data-redis` on its test classpath. Run Step 2 first. If the import fails to resolve, replace `LettuceConnectionFactory().phase` with the literal `0`, and keep the comment `// LettuceConnectionFactory phase, spring-data-redis 4.1.1`.

- [ ] **Step 2: Run them and see them fail**

```bash
mvn -o -pl chat-core,chat-service-composite test -Dtest=CommandRuntimeLifecycleTests > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:|e: file' $LOG | head
mvn -o -pl chat-deploy -am test -Dtest=LeasePhaseOrderTests -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:|e: file' $LOG | head
```

Expected: the first fails to compile on `CommandRuntimeLifecycle`. The second passes, because Task 1 made the phases. It is the order pin, and its mutation is Step 6.

- [ ] **Step 3: Make `close` idempotent**

`MemoryCommandRuntime.close` now runs twice: in `stop` and at destruction.

```kotlin
    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        dispatcher.close()
        scheduler.dispose()
    }
```

- [ ] **Step 4: Write `CommandRuntimeLifecycle.kt` and register it**

```kotlin
package com.demo.chat.service.composite.command.memory

import com.demo.chat.domain.LeasePhases
import org.springframework.context.SmartLifecycle

/**
 * Stops the command runtime before the lease guards release. Its dispatcher
 * writes the store on its own schedule, and bean destruction runs after the
 * release. See `CHAT-lswjobhz`.
 */
class CommandRuntimeLifecycle(private val runtime: MemoryCommandRuntime<*, *>) : SmartLifecycle {
    @Volatile
    private var running = false

    override fun start() {
        running = true
    }

    override fun stop() {
        runtime.close()
        running = false
    }

    override fun isRunning(): Boolean = running

    override fun getPhase(): Int = LeasePhases.WRITERS
}
```

In `MessageCommandConfiguration`, after `memoryCommandRuntime`, add:

```kotlin
    @Bean
    fun commandRuntimeLifecycle(runtime: MemoryCommandRuntime<T, V>): CommandRuntimeLifecycle =
        CommandRuntimeLifecycle(runtime)
```

- [ ] **Step 5: Run the command tests**

```bash
mvn -o -pl chat-core,chat-service-composite test -Dtest='CommandRuntimeLifecycleTests,MessageCommandServiceTests,MemoryAdmissionTests' > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*(CommandRuntime|MessageCommand|MemoryAdmission)' $LOG
```

Expected: every class passes.

- [ ] **Step 6: Commit, then measure one mutation**

Commit. Then set `LeasePhases.WRITERS` to `Int.MAX_VALUE - 5632`, below the service guard, and run `LeasePhaseOrderTests`. Expected: it fails. Restore by absolute path.

```bash
git add chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/MemoryCommandRuntime.kt chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/CommandRuntimeLifecycle.kt chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/command/MessageCommandConfiguration.kt chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/CommandRuntimeLifecycleTests.kt chat-deploy/src/test/kotlin/com/demo/chat/test/deploy/LeasePhaseOrderTests.kt
git commit -m "CHAT-lswjobhz: Stop the command runtime before the lease guards release"
fp comment CHAT-lswjobhz "Task 5 done: CommandRuntimeLifecycle at the writers phase, and the phase order pin."
```

---

### Task 6: Redis boot tests

**Files:** see the File Map rows for Task 6.

**Interfaces:**
- Consumes: every earlier task.
- Produces: `RedisLuceneFilesRestartTests.Companion.start(root: Path?, nodeId: Int, vararg extra: String)`, and a `startWhenClaimFree` that also retries on `ServiceLeaseException`.

- [ ] **Step 1: Extend the start helpers**

In `RedisLuceneFilesRestartTests.Companion`:

```kotlin
        fun start(root: Path?, nodeId: Int, vararg extra: String): ConfigurableApplicationContext =
            SpringApplicationBuilder(RedisDeployBootTests.BootApp::class.java)
                .web(WebApplicationType.NONE)
                .properties(*properties(root, nodeId).toTypedArray(), *extra)
                .run()
```

In `startWhenClaimFree`, retry on either lease:

```kotlin
                    val claimed = generateSequence<Throwable>(e) { it.cause }
                        .any { it is NodeIdClaimException || it is ServiceLeaseException }
```

Import `com.demo.chat.domain.ServiceLeaseException`. Update the class KDoc: a Redis close now releases the claim, so the retry covers a crash case alone.

- [ ] **Step 2: Turn the measurement into a refusal**

Replace the two tests of `RedisSharedStoreIndexTests` with:

```kotlin
    @Test
    fun `a second core on one Redis fails at start and names the service and the holder`() {
        startWhenClaimFree(null, 50).use { a ->
            val thrown = assertThrows<Exception> { start(null, 51).close() }
            val lease = generateSequence<Throwable>(thrown) { it.cause }.filterIsInstance<ServiceLeaseException>().first()
            assertThat(lease.name).isEqualTo("index:authmeta")
            assertThat(lease.scope).isEqualTo("redis store for key type long")
            assertThat(lease.holder).startsWith("node 50 ")

            a.addUser("sharedcontrol")
            assertThat(a.findsHandle("sharedcontrol")).describedAs("the first core still serves").isTrue()
        }
    }
```

`index:authmeta` is the first name in sorted order. Remove the unused helpers and imports. Update the KDoc: the class now proves the refusal, and the gap measurement of 2026-10-09 lives in the spec.

- [ ] **Step 3: Write `RedisServiceLeaseBootTests.kt`**

```kotlin
package com.demo.chat.test.deploy.redis

import com.demo.chat.domain.ServiceLeaseException
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.addUser
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.findsHandle
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.start
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.startWhenClaimFree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import java.time.Duration

/**
 * The store service lease on a Redis deployment. See `CHAT-lswjobhz` and
 * `CHAT-ocpojbyy`.
 *
 * Node ids 52 to 57 belong to this class. See docs/NODEID-CLAIM.md. The
 * helpers set a 3 second TTL, a 1 second renew interval and a 1 second
 * safety margin.
 */
@Tag("integration")
class RedisServiceLeaseBootTests {

    private val container = RedisDeployBootTests.redis

    private fun template(): ReactiveStringRedisTemplate = ReactiveStringRedisTemplate(
        LettuceConnectionFactory(RedisStandaloneConfiguration(container.containerIpAddress, container.getMappedPort(6379)))
            .apply { afterPropertiesSet() }
    )

    @Test
    fun `a clean close releases every lease, so a restart starts at once`() {
        startWhenClaimFree(null, 52).use { }
        start(null, 52).use { }
    }

    @Test
    fun `a swap after a clean close finds the user through the rebuilt index`() {
        startWhenClaimFree(null, 53).use { a -> a.addUser("swaprelease") }
        start(null, 54).use { b -> assertThat(b.findsHandle("swaprelease")).isTrue() }
    }

    @Test
    fun `a swap after an expired lease waits for the TTL and then claims`() {
        template().opsForValue().set("chat:lease:long:index:authmeta", "node 99 crashed@host:1#dead", Duration.ofSeconds(2)).block()
        val thrown = assertThrows<Exception> { start(null, 55).close() }
        assertThat(generateSequence<Throwable>(thrown) { it.cause }.any { it is ServiceLeaseException }).isTrue()

        Thread.sleep(2500)
        start(null, 55).use { }
    }

    @Test
    fun `a failed start after the claims leaves no lease`() {
        val thrown = assertThrows<Exception> {
            start(null, 56, "spring.main.sources=${FailingStart::class.java.name}").close()
        }
        assertThat(generateSequence<Throwable>(thrown) { it.cause }.any { it.message == FailingStart.MESSAGE }).isTrue()

        start(null, 56).use { }
    }

    @Test
    fun `a lost lease closes the context`() {
        val context = startWhenClaimFree(null, 57)
        template().delete("chat:lease:long:index:user").block()

        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (context.isActive && System.nanoTime() < deadline) Thread.sleep(100)
        assertThat(context.isActive).describedAs("the context after its lease was deleted").isFalse()
    }

    /** A start step after the guards. Its failure fails the refresh. */
    @Configuration
    open class FailingStart {
        @Bean
        open fun failingStart(): SmartLifecycle = object : SmartLifecycle {
            override fun start() = throw IllegalStateException(MESSAGE)
            override fun stop() {}
            override fun isRunning() = false
            override fun getPhase() = Int.MAX_VALUE - 4500
        }

        companion object {
            const val MESSAGE = "A test start step failed after the lease guards claimed."
        }
    }
}
```

`spring.main.sources` adds a source to `SpringApplicationBuilder`. If the measured start ignores it, add an overload `start(root, nodeId, sources: Array<Class<*>>, vararg extra)` that calls `.sources(*sources)`, and record the change.

- [ ] **Step 4: Run them**

```bash
mvn -o -Pintegration -pl chat-deploy-redis -am test -Dtest='RedisServiceLeaseBootTests,RedisSharedStoreIndexTests,RedisLuceneFilesRestartTests,RedisClaimBootTests' -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*Redis' $LOG
```

Expected: `RedisServiceLeaseBootTests` 5 of 5, `RedisSharedStoreIndexTests` 1 of 1, and the two other classes pass.

- [ ] **Step 5: Commit, then measure three mutations, each alone**

Commit. Then run `RedisServiceLeaseBootTests` and `RedisSharedStoreIndexTests` under each mutation, and restore by absolute path after each one.

| Mutation | Expected |
|---|---|
| `ServiceLeaseGuard.start` claims no name (`names()` replaced by `emptyList()`) | The refusal test fails |
| `ServiceLeaseGuard.stop` and `NodeIdClaimGuard.stop` do not call `releaseAll` | The restart test fails, with a `NodeIdClaimException` or a `ServiceLeaseException` |
| `ServiceLeaseGuard.destroy` and `NodeIdClaimGuard.destroy` do not call `releaseAll` | The failed start test fails |

```bash
git add chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisLuceneFilesRestartTests.kt chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisSharedStoreIndexTests.kt chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisServiceLeaseBootTests.kt
git commit -m "CHAT-lswjobhz: Prove the service lease on a Redis deployment"
fp comment CHAT-lswjobhz "Task 6 done: refusal, swap, restart, failed start and loss on Redis, with three mutations."
```

---

### Task 7: Cassandra boot tests

**Files:** `chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraServiceLeaseBootTests.kt`.

**Interfaces:**
- Consumes: Task 4 and the boot harness of `CassandraClaimBootTests`.

- [ ] **Step 1: Write the tests**

Copy the launch harness of `CassandraClaimBootTests.kt`: its container field, its launch properties and its `boot` function. Then add:

```kotlin
    @Test
    fun `a second core on one keyspace fails at start and names the service`() {
        boot("app.nodeid=45", ready = AtomicBoolean(false)).run().use { a ->
            val thrown = Assertions.assertThrows(Exception::class.java) {
                boot("app.nodeid=46", ready = AtomicBoolean(false)).run().close()
            }
            val lease = generateSequence<Throwable>(thrown) { it.cause }.filterIsInstance<ServiceLeaseException>().first()
            Assertions.assertEquals("cassandra keyspace chat_long", lease.scope)
            Assertions.assertTrue(lease.holder.startsWith("node 45 "), lease.holder)
            Assertions.assertTrue(a.isActive)
        }
    }

    @Test
    fun `a clean close releases every lease, so a restart starts at once`() {
        boot("app.nodeid=47", ready = AtomicBoolean(false)).run().use { }
        boot("app.nodeid=47", ready = AtomicBoolean(false)).run().use { }
    }
```

If `boot` in that file takes other parameters, adapt the calls to its signature and keep the node ids.

- [ ] **Step 2: Run them**

```bash
mvn -o -Pintegration -pl chat-deploy-cassandra -am test -Dtest='CassandraServiceLeaseBootTests,CassandraClaimBootTests' -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*Cassandra' $LOG
docker events --filter event=oom --since 30m --until 0s
```

Expected: 2 of 2, `CassandraClaimBootTests` passes, and no OOM event.

- [ ] **Step 3: Commit**

```bash
git add chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraServiceLeaseBootTests.kt
git commit -m "CHAT-lswjobhz: Prove the service lease on a Cassandra deployment"
fp comment CHAT-lswjobhz "Task 7 done: refusal and restart on Cassandra."
```

---

### Task 8: One store-backed context at a time

**Files:** the store-backed deploy test classes that the `--ci` run names.

The spec states that the size of this task is not known. A test class that keeps two cores open on one store now fails at the second start.

- [ ] **Step 1: Run the gate with the guard on**

```bash
DOCKER_CONFIG=$(mktemp -d) shell-scripts/build-health.sh --ci > $LOG 2>&1; echo exit=$?; tail -40 $LOG
grep -lE 'ServiceLeaseException|is already held in the' $(find . -path '*/target/surefire-reports/*.txt' -newer $LOG -o -path '*/target/surefire-reports/*.txt' -mmin -120 2>/dev/null) | sort -u
```

Record each class that fails with a `ServiceLeaseException` in the ledger.

- [ ] **Step 2: Repair each named class with one of three changes**

1. A Spring test class that shares one context across test classes on one store: add `@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)`, so its context closes before the next class starts.
2. A class that builds contexts by hand: close each context with `use { }` before it starts the next one.
3. A class that runs two cores at once on purpose: it becomes a refusal test, in the shape of Task 6 Step 2.

Run each repaired class alone with the command of its module, and record the count.

- [ ] **Step 3: Run the gate again**

```bash
DOCKER_CONFIG=$(mktemp -d) shell-scripts/build-health.sh --ci > $LOG 2>&1; echo exit=$?; tail -20 $LOG
docker events --filter event=oom --since 2h --until 0s
```

Expected: exit 0, 0 failures, `agent http gate: ok`, and no drift. Record the module, test and skip counts.

- [ ] **Step 4: Commit**

```bash
git add <each repaired file, by name>
git commit -m "CHAT-lswjobhz: Run one store-backed context at a time"
fp comment CHAT-lswjobhz "Task 8 done: <n> classes repaired. --ci exit 0 with <counts>."
```

---

### Task 9: Documents

**Depends on:** `CHAT-jgegpcoi`, for register space. Merge `master` into the branch first if `CHAT-jgegpcoi` merged after Task 8.

- [ ] **Step 1: Write `docs/SERVICE-LEASE.md`**

Sections: the rule and the lease names, the scope and the backend, `The guarantee` with its three cases, the swap procedure, the properties, the start and stop order table, the store recreation step for `service_lease`, split deployments, and the limit of Review Focus 1.

The swap procedure must say: stop the old deployment, or wait a full `ttl` after it was last seen, before you start the replacement.

- [ ] **Step 2: Correct `docs/NODEID-CLAIM.md`**

Replace the correction of 2026-09-27 with the measured fact: a clean close releases the lease on Redis and on Cassandra, since `CHAT-lswjobhz`. Add the node ids: `RedisServiceLeaseBootTests` 52 to 57, `CassandraServiceLeaseBootTests` 45 to 47. Update the `RedisSharedStoreIndexTests` row: it now uses 50 and 51 for the refusal.

- [ ] **Step 3: Update the KDoc of `IndexStartSequence`**

Read the KDoc first, and keep its other statements. Change the start scan limit: the store service lease now keeps another core of the same services off the store during the scan. A split deployment remains outside that protection.

- [ ] **Step 4: Bind and check drift**

```bash
drift refs docs/NODEID-CLAIM.md; drift refs chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/IndexStartSequence.kt
drift link docs/SERVICE-LEASE.md chat-core/src/main/kotlin/com/demo/chat/domain/ServiceLeaseGuard.kt chat-core/src/main/kotlin/com/demo/chat/domain/LeaseKeeper.kt chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisServiceLeaseStore.kt chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraServiceLeaseStore.kt
drift check; git diff --check; echo exit=$?
```

Review each bound document before any relink. Never relink stale prose.

- [ ] **Step 5: Record the gate in `docs/BUILD-HEALTH.md` and add the register section**

`docs/BUILD-HEALTH.md` records the Task 8 counts and their cause. `forward-register.md` gains one dated section: the rule, the guarantee, the phases, the measured mutations, both gate totals, and the closed `CHAT-ocpojbyy`. Do not move the `Checkout` row. Confirm the file stays under 65,536 bytes.

- [ ] **Step 6: Run both gates, commit and hand off**

```bash
shell-scripts/build-health.sh > $LOG 2>&1; echo exit=$?; tail -20 $LOG
DOCKER_CONFIG=$(mktemp -d) shell-scripts/build-health.sh --ci > $LOG 2>&1; echo exit=$?; tail -20 $LOG
git add docs/SERVICE-LEASE.md docs/NODEID-CLAIM.md docs/BUILD-HEALTH.md chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/IndexStartSequence.kt drift.lock forward-register.md
git commit -m "CHAT-lswjobhz: Record the store service lease"
fp comment CHAT-lswjobhz "Task 9 done: documents, drift and register. Both gates green."
fp comment CHAT-ocpojbyy "Closed by CHAT-lswjobhz: a clean Redis close releases the node id lease. RedisServiceLeaseBootTests pins it."
```

Report the gate totals to the owner, and stop. Ask the owner whether to push the branch and open a pull request.

---

## Spec coverage

| Spec section | Task |
|---|---|
| The rule, lease names and scope | 2, 3, 4 |
| The guarantee, cases 1 and 2 | 2 (deadline, loss), 6 (loss at boot) |
| The guarantee, case 3 | 9, recorded |
| The lease store, Redis and Cassandra | 3, 4 |
| Store shape check and no truncate | 4 |
| The guard, sorted claims, release on refusal, holder text | 2 |
| One renew loop | 1 |
| The lifecycle order, start and stop | 1, 2, 5 |
| Other writers | 5 |
| Failed start release | 1, 2, 6 |
| The node id claim before the first key mint | 1 |
| `CHAT-ocpojbyy` | 1, 6, 9 |
| Split deployments | 9, recorded |
| Two cores on Redis, swap, release on close | 6 |
| Cassandra refusal | 7 |
| Mutation proofs | 1, 2, 5, 6 |
| The suite and the rule | 8 |
| Documents | 9 |

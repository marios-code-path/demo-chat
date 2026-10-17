# Store service lease implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task by task, inline in one session. `AGENTS.md` forbids subagent-driven development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each store service on a shared backend at most one current lease holder, through a store-side TTL lease. Drain the store writes and release every lease on a clean close. Release every lease on a failed start. A lease cannot prove that an old holder stopped. The spec section `The guarantee` states the limit.

**Architecture:** A new `LeaseKeeper` in `chat-core` holds the renew loop and the close deadline for `NodeIdClaimGuard` and a new `ServiceLeaseGuard`. Renewals start before the first claim, run together, and run on one thread. The deadline check runs on a second thread that never blocks. Both guards become `SmartLifecycle` beans. The service guard derives its lease names from the store service beans. A `StoreWriteGate` proxy counts each store write, and the guard drains it before the release. Redis and Cassandra each get a `ServiceLeaseStore`. The memory command runtime stops in a phase above the guards.

**Tech Stack:** Kotlin 2.4, Spring Boot 4, Spring Framework `SmartLifecycle` and `BeanPostProcessor`, Reactor, Lettuce, Cassandra driver 4, JUnit 5, AssertJ, StepVerifier, mockito-kotlin, Testcontainers, Maven.

**Spec:** `docs/superpowers/specs/2026-10-09-store-service-lease-design.md`

**Issue:** `CHAT-lswjobhz`. Parent: `CHAT-aqpcacwv`. Branch: `chat-lswjobhz-shared-store-index`, in `.worktrees/chat-lswjobhz-shared-store-index`. `CHAT-ocpojbyy` closes with this work. `CHAT-jgegpcoi` closes the register period, and Task 10 needs its merge for register space.

**Revised on 2026-10-16 after the owner's plan review.** The review required six corrections. The narrow guarantee replaced the promise of one live instance. Renewal now starts before the first claim. The deadline has its own thread. A drain runs before the release. Lease names come from the beans. Each task has an fp child issue. The new Task 3 holds the drain, so the plan has ten tasks.

## Global Constraints

- The guarantee is the narrow one of the spec. **No text in this work promises exactly one live instance.** A lease gives at most one current holder. No task adds fencing.
- A safe swap needs confirmed termination of the old process. One `ttl` of waiting is not proof.
- Lease names are `<kind>:<domain>`. The kind is `persistence` or `index`. `ServiceLeaseNames` maps each store service bean to its name through its domain interface. A store service bean with no mapped interface fails the start.
- A lease lives in the persistence backend. A process claims when `app.service.core.persistence` names `redis` or `cassandra`. A lease never lives in the key backend.
- Redis lease key: `chat:lease:<keyType>:<name>`. Cassandra table: `service_lease(name text, owner_id text, PRIMARY KEY(name))` in both `keyspace-long.cql` and `keyspace-uuid.cql`.
- The truncate scripts do not list `service_lease`. A live lease must expire.
- Phases: `NodeIdClaimGuard` `Int.MAX_VALUE - 6144`. `ServiceLeaseGuard` `Int.MAX_VALUE - 5120`. The command runtime stop `Int.MAX_VALUE - 4608`. `RootKeyStartup` stays `Int.MAX_VALUE - 4096`.
- The guards renew with the node id properties: `app.nodeid.claim.ttl`, `renew-interval`, `safety-margin` and `operation-timeout`. The drain uses `app.service.lease.drain-timeout`, default `10s`.
- Renewals start before the first claim. One renewal round sends every renewal together. The deadline check runs on its own thread and never blocks.
- The write gate wraps `add`, `rem` and `addEnriched` of each `PersistenceStore` and `IndexService` bean, except a `ProcessLocalIndex`.
- Split deployments stay unsupported. No task adds a split path or a split refusal.
- The node id lease keeps its Redis key format and its `node_claim` table.
- The no-migration rule of 2026-09-25 stands. A Cassandra store without `service_lease` fails its start, and the operator recreates it.
- New container node ids: Redis 52 to 58, Cassandra 45 to 47. Record them in `docs/NODEID-CLAIM.md`.
- Run `mvn -o -pl chat-core,<module> test`, never `-pl <module>` alone. A Cassandra module needs `shared-resources-cassandra` in `-pl`. A deploy module uses `-pl <module> -am` with `-Dtest` and `-Dsurefire.failIfNoSpecifiedTests=false`.
- Container tests need `-Pintegration` and a running Docker. Read `docker events --filter event=oom --since 1h` before you trust a red Cassandra result.
- One Maven build at a time in this worktree. Write Maven output to a log file, and read the exit code and the summary lines.
- Commit the implementation before any mutation. Restore a mutation with `git checkout -- <absolute path>`, and prove the restore with `git status --short`.
- `git add` names exact files.
- Each commit message starts with `CHAT-lswjobhz:` and ends with the co-author trailer of recent `master` commits. Each commit names the fp child issue of its task.
- Claim each child issue with `fp issue update --status in-progress <id>` before its task. Set it to `done` after the task commit.
- Push and pull request steps need the owner's approval at that time.
- Agent-authored prose follows ASD-STE100 strict mode from `AGENTS.md`.

## Review Focus

1. **A command that admission accepted, and that has not reached the store at close.** The gate refuses its write after the close, so the memory bus loses it. Expect a failed or uncertain status, and no store write after the release. Stage 1 retry safety already ends at a process restart. Task 6 records it in `docs/SERVICE-LEASE.md`, and Task 7 pins the refusal.
2. **A Redis lease with a key type that the operator changes between runs.** The key carries the key type, so a long run and a uuid run hold separate leases on one Redis. Expect both to start. Task 4 pins it.
3. **A lease release that fails on close.** Expect a warning log line, and a lease that expires after `ttl`. Task 1 logs the release failure at `warn`, and pins it.
4. **A second start right after a crash.** Expect the second start to fail with the holder named, until `ttl` passes. Task 7 pins it with a seeded lease.
5. **A deployment that names `app.service.core.key=redis` with `app.service.core.persistence=memory`.** Expect a node id claim, no service lease and no write gate. Task 4 pins it.

## Child issues

Each task has one fp child issue under `CHAT-lswjobhz`. The dependency edges follow the Interfaces blocks.

| Task | Issue | Depends on |
|---|---|---|
| 1. One renew loop, and the node id guard as a lifecycle | `CHAT-plqmcnit` | none |
| 2. The service lease guard and its lease names | `CHAT-ofkodhjp` | Task 1 |
| 3. The store write gate and the drain | `CHAT-fsfhxpvf` | Task 2 |
| 4. The Redis lease store | `CHAT-jilnadfu` | Task 3 |
| 5. The Cassandra lease store | `CHAT-wkxxmqsy` | Task 3 |
| 6. Writers stop before the release | `CHAT-olsjrdsl` | Task 1 |
| 7. Redis boot tests | `CHAT-elneflzs` | Tasks 4 and 6 |
| 8. Cassandra boot tests | `CHAT-wkdayxhc` | Tasks 5 and 6 |
| 9. One store-backed context at a time | `CHAT-czcmnsvu` | Tasks 7 and 8 |
| 10. Documents | `CHAT-uusjnrgt` | Task 9 and `CHAT-jgegpcoi` |

---

## File Map

| File | Task | Change |
|---|---|---|
| `chat-core/src/main/kotlin/com/demo/chat/domain/LeaseKeeper.kt` | 1 | New: `HeldLease`, `LeaseKeeper`, `LeasePhases` |
| `chat-core/src/main/kotlin/com/demo/chat/domain/ClaimScheduler.kt` | 1 | Thread name parameter |
| `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimGuard.kt` | 1 | `SmartLifecycle`, uses `LeaseKeeper` |
| `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimGuardConfiguration.kt` | 1 | The deadline scheduler, and the guard as a dependent of each store |
| `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardStartupTests.kt` | 1 | `afterPropertiesSet` to `start`, fixed clock |
| `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardRenewalTests.kt` | 1 | `afterPropertiesSet` to `start`, fixed clock, three deadline tests rewritten |
| `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardLifecycleTests.kt` | 1 | New |
| `chat-core/src/test/kotlin/com/demo/chat/test/domain/LeaseKeeperTimingTests.kt` | 1 | New: slow claims and slow renewals on real threads |
| `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisClaimBootTests.kt` | 1, 4 | The first key mint, and the key-only case |
| `chat-core/src/main/kotlin/com/demo/chat/domain/ServiceLease.kt` | 2 | New: `ServiceLeaseStore`, `ServiceLeaseNames`, `ServiceLeaseException` |
| `chat-core/src/main/kotlin/com/demo/chat/domain/ServiceLeaseGuard.kt` | 2, 3 | New. Task 3 adds the drain |
| `chat-core/src/test/kotlin/com/demo/chat/test/domain/ServiceLeaseNamesTests.kt` | 2 | New |
| `chat-core/src/test/kotlin/com/demo/chat/test/domain/ServiceLeaseGuardTests.kt` | 2, 3 | New |
| `chat-core/src/main/kotlin/com/demo/chat/service/core/ProcessLocalIndex.kt` | 3 | New marker |
| `chat-core/src/main/kotlin/com/demo/chat/domain/StoreWriteGate.kt` | 3 | New: `StoreWriteGate`, `StoreWriteRefusedException`, `StoreWriteGatePostProcessor`, `StoreWriteGateConfiguration` |
| `chat-core/src/test/kotlin/com/demo/chat/test/domain/StoreWriteGateTests.kt` | 3 | New |
| `chat-core/src/test/kotlin/com/demo/chat/test/domain/ServiceLeaseGuardDrainTests.kt` | 3 | New |
| `chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/impl/LuceneIndex.kt` | 3 | Implements `ProcessLocalIndex` |
| `chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisLeaseScripts.kt` | 4 | New: the shared Lua scripts |
| `chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisNodeIdClaimStore.kt` | 4 | Uses `RedisLeaseScripts` |
| `chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisServiceLeaseStore.kt` | 4 | New |
| `chat-persistence-redis/src/main/kotlin/com/demo/chat/config/persistence/redis/ServiceLeaseConfiguration.kt` | 4 | New |
| `chat-persistence-redis/src/test/kotlin/com/demo/chat/test/persistence/redis/RedisServiceLeaseStoreTests.kt` | 4 | New |
| `shared-resources-cassandra/src/main/resources/keyspace-long.cql`, `keyspace-uuid.cql` | 5 | `service_lease` table |
| `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraServiceLeaseStore.kt` | 5 | New |
| `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/config/persistence/cassandra/ServiceLeaseConfiguration.kt` | 5 | New |
| `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraStoreShapeCheck.kt` | 5 | `service_lease` in `PERSISTENCE_TABLES` |
| `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/CassandraServiceLeaseStoreTests.kt` | 5 | New |
| `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/MemoryCommandRuntime.kt` | 6 | Idempotent `close` |
| `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/command/MessageCommandConfiguration.kt` | 6 | `CommandRuntimeLifecycle` bean |
| `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/CommandRuntimeLifecycle.kt` | 6 | New |
| `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/CommandRuntimeLifecycleTests.kt` | 6 | New |
| `chat-deploy/src/test/kotlin/com/demo/chat/test/deploy/LeasePhaseOrderTests.kt` | 6 | New |
| `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisLuceneFilesRestartTests.kt` | 7 | `start` takes extra properties; the retry covers a held service lease |
| `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisSharedStoreIndexTests.kt` | 7 | Measurement becomes refusal |
| `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisServiceLeaseBootTests.kt` | 7 | New |
| `chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraServiceLeaseBootTests.kt` | 8 | New |
| Store-backed deploy test classes named by the `--ci` run | 9 | One context at a time |
| `docs/SERVICE-LEASE.md` | 10 | New |
| `docs/NODEID-CLAIM.md`, `docs/BUILD-HEALTH.md`, `IndexStartSequence.kt` KDoc, `drift.lock`, `forward-register.md` | 10 | Records |

---

### Task 1: One renew loop, and the node id guard as a lifecycle

**Child issue:** `CHAT-plqmcnit`.

**Files:** see the File Map rows for Task 1.

**Interfaces:**
- Produces: `interface HeldLease { val label: String; fun renew(): Mono<ClaimResult>; fun release(): Mono<Void>; fun deniedMessage(holder: String): String; fun lostMessage(): String }`.
- Produces: `class LeaseKeeper(subject: String, props: NodeIdClaimProperties, context: ConfigurableApplicationContext, renewals: ClaimScheduler, deadlines: ClaimScheduler, closeThreadName: String, clock: () -> Long = System::nanoTime)` with `fun now(): Long`, `fun begin()`, `fun add(lease: HeldLease, sentAt: Long)`, `fun held()`, `fun releaseAll()`, `fun abandon()` and `val holding: Boolean`.
- Produces: `object LeasePhases { NODE_ID_GUARD, SERVICE_GUARD, WRITERS }`. `class ExecutorClaimScheduler(threadName: String = "nodeid-claim-renew")`.
- `NodeIdClaimGuard(stores, nodeId, owner, props, context, scheduler, deadlines: ClaimScheduler = scheduler, clock: () -> Long = System::nanoTime)` implements `SmartLifecycle` and `DisposableBean`. `start()` replaces `afterPropertiesSet()`.

**The keeper rules.** A guard calls `begin()` before its first claim, so the renew loop already runs during the claims. After each granted claim, the guard calls `add(lease, sentAt)`, with the clock reading taken before the claim was sent. After the last claim, it calls `held()`. `add` and `held` throw when a held lease has passed its deadline, or when a renewal in the batch was denied or lost. The guard then calls `releaseAll()` and fails the start. After `held()`, the deadline watch on the second scheduler closes the context.

- [ ] **Step 1: Write the failing lifecycle tests**

Create `NodeIdClaimGuardLifecycleTests.kt`. Reuse `FakeClaimStore` and `ManualClaimScheduler` from `ClaimTestDoubles.kt`.

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.ClaimResult
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
        NodeIdClaimProperties(null, null, null, null), context, scheduler, clock = { 0L },
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
        redis.claimAnswer = { ClaimResult.Denied("other") }
        runCatching { guard.start() }
        assertThat(guard.isRunning).isFalse()
        assertThat(scheduler.periodic).describedAs("the renew loop after a refused claim").isNull()
    }

    @Test
    fun `the renew loop runs before the claim returns`() {
        var periodicDuringClaim: Any? = null
        redis.claimAnswer = { periodicDuringClaim = scheduler.periodic; ClaimResult.Granted }
        guard().start()
        assertThat(periodicDuringClaim).isNotNull()
    }

    @Test
    fun `the guard starts in its own phase`() {
        assertThat(guard().phase).isEqualTo(LeasePhases.NODE_ID_GUARD).isEqualTo(Int.MAX_VALUE - 6144)
    }
}
```

`RuntimeOwnerId.generate` and the parameter list of `NodeIdClaimProperties` are as measured on 2026-10-16. Read both before you run.

- [ ] **Step 2: Write the failing timing tests**

Create `LeaseKeeperTimingTests.kt`. These tests use real threads and the real clock. Each one takes at most 6 s.

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.ExecutorClaimScheduler
import com.demo.chat.domain.HeldLease
import com.demo.chat.domain.LeaseKeeper
import com.demo.chat.domain.NodeIdClaimProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * The renew loop on real threads. See `CHAT-lswjobhz`, plan review items 2
 * and 3.
 *
 * The TTL is 3 s, the renew interval 1 s, the safety margin 1 s and the
 * operation timeout 500 ms. So the close deadline is 2 s.
 */
class LeaseKeeperTimingTests {

    private val props = NodeIdClaimProperties(
        Duration.ofSeconds(3), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMillis(500)
    )
    private val context = GenericApplicationContext().apply { refresh() }
    private val renewals = ExecutorClaimScheduler("test-lease-renew")
    private val deadlines = ExecutorClaimScheduler("test-lease-deadline")
    private val names = (1..12).map { "lease-%02d".format(it) }

    @AfterEach
    fun shutdown() {
        renewals.shutdownNow()
        deadlines.shutdownNow()
    }

    /** Leases with a TTL on the real clock. The store applies a request when its delay ends. */
    private class ExpiringLeases(private val ttl: Duration) {
        val expiresAt = ConcurrentHashMap<String, Long>()
        val renewStarts = ConcurrentHashMap<String, Long>()

        @Volatile var claimDelay: Duration = Duration.ZERO
        @Volatile var renewDelay: Duration = Duration.ZERO
        @Volatile var renewBlocks: Duration = Duration.ZERO
        @Volatile var renewFails = false

        fun claimOf(name: String): Mono<ClaimResult> = Mono.delay(claimDelay).map {
            expiresAt[name] = System.nanoTime() + ttl.toNanos()
            ClaimResult.Granted
        }

        fun renewOf(name: String): Mono<ClaimResult> = Mono.defer {
            renewStarts.putIfAbsent(name, System.nanoTime())
            // A blocking store call holds the renew thread here.
            if (!renewBlocks.isZero) Thread.sleep(renewBlocks.toMillis())
            Mono.delay(renewDelay).map {
                check(!renewFails) { "the store is down" }
                val at = expiresAt[name]
                if (at == null || at < System.nanoTime()) ClaimResult.Lost
                else {
                    expiresAt[name] = System.nanoTime() + ttl.toNanos()
                    ClaimResult.Granted
                }
            }
        }

        fun releaseOf(name: String): Mono<Void> = Mono.fromRunnable { expiresAt.remove(name) }

        fun live(name: String): Boolean = (expiresAt[name] ?: 0L) > System.nanoTime()

        fun lease(name: String): HeldLease = object : HeldLease {
            override val label = name
            override fun renew() = renewOf(name)
            override fun release() = releaseOf(name)
            override fun deniedMessage(holder: String) = "$name is held by $holder"
            override fun lostMessage() = "$name is lost"
        }
    }

    private fun keeper() = LeaseKeeper("test leases", props, context, renewals, deadlines, "test-lease-close")

    private fun claimAll(keeper: LeaseKeeper, store: ExpiringLeases) {
        keeper.begin()
        names.forEach { name ->
            val sentAt = keeper.now()
            store.claimOf(name).block()
            keeper.add(store.lease(name), sentAt)
        }
        keeper.held()
    }

    private fun waitUntil(limit: Duration, condition: () -> Boolean) {
        val end = System.nanoTime() + limit.toNanos()
        while (!condition() && System.nanoTime() < end) Thread.sleep(20)
    }

    @Test
    fun `slow claims keep every early lease alive`() {
        val store = ExpiringLeases(props.ttl).apply { claimDelay = Duration.ofMillis(400) }
        val keeper = keeper()
        val started = System.nanoTime()

        claimAll(keeper, store)

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThan(props.ttl)
        assertThat(names.filterNot(store::live)).describedAs("leases that expired during the claims").isEmpty()
        keeper.releaseAll()
    }

    @Test
    fun `a claim batch that passes a deadline fails and releases every lease`() {
        val store = ExpiringLeases(props.ttl).apply {
            claimDelay = Duration.ofMillis(400)
            renewFails = true
        }
        val keeper = keeper()

        assertThatThrownBy {
            try {
                claimAll(keeper, store)
            } catch (failure: Throwable) {
                keeper.releaseAll()
                throw failure
            }
        }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("before the claims ended")

        assertThat(store.expiresAt).isEmpty()
        assertThat(context.isActive).describedAs("a claim failure fails the start, not the context").isTrue()
    }

    @Test
    fun `the deadline closes the context while every renewal blocks its thread`() {
        val store = ExpiringLeases(props.ttl)
        val keeper = keeper()
        claimAll(keeper, store)
        val heldAt = System.nanoTime()
        store.renewBlocks = Duration.ofSeconds(10)

        waitUntil(Duration.ofSeconds(6)) { !context.isActive }

        assertThat(context.isActive).describedAs("the context after the deadline").isFalse()
        assertThat(Duration.ofNanos(System.nanoTime() - heldAt)).isLessThan(props.closeDeadline.plusMillis(1500))
    }

    @Test
    fun `one renewal round sends every renewal at once`() {
        val store = ExpiringLeases(props.ttl).apply { renewDelay = Duration.ofMillis(300) }
        val keeper = keeper()
        claimAll(keeper, store)
        store.renewStarts.clear()

        waitUntil(Duration.ofSeconds(3)) { store.renewStarts.size == names.size }

        assertThat(store.renewStarts).hasSize(names.size)
        val spread = store.renewStarts.values.max() - store.renewStarts.values.min()
        assertThat(Duration.ofNanos(spread)).isLessThan(Duration.ofMillis(200))
        assertThat(context.isActive).isTrue()
        keeper.releaseAll()
    }
}
```

- [ ] **Step 3: Run them and see them fail**

```bash
mvn -o -pl chat-core test -Dtest='NodeIdClaimGuardLifecycleTests,LeaseKeeperTimingTests' > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:|e: file' $LOG | head
```

Expected: a compile failure on `LeasePhases`, `LeaseKeeper`, `start` and `stop`.

- [ ] **Step 4: Write `LeaseKeeper.kt`**

```kotlin
package com.demo.chat.domain

import org.slf4j.LoggerFactory
import org.springframework.context.ConfigurableApplicationContext
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

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

/** One lease that a guard holds and renews. The keeper applies the operation timeout. */
interface HeldLease {
    /** Names the lease in a log line. */
    val label: String

    /** Extends the lease. An error is retried at the next interval. */
    fun renew(): Mono<ClaimResult>

    /** Drops the lease when this owner holds it. */
    fun release(): Mono<Void>

    fun deniedMessage(holder: String): String

    fun lostMessage(): String
}

/**
 * Renews the leases of one guard, and closes the context when it cannot.
 * `NodeIdClaimGuard` and `ServiceLeaseGuard` share it.
 *
 * The renew loop starts before the first claim, so a slow claim batch cannot
 * let an early lease expire. One round sends every renewal at once.
 *
 * The deadline check runs on [deadlines], and it never blocks. A renewal that
 * blocks the [renewals] thread therefore cannot delay it. The check closes
 * the context when a lease has no granted request within
 * `ttl - safety-margin`, measured from the send time of the request. A
 * process pause longer than the safety margin defeats this. See the guarantee
 * in `docs/superpowers/specs/2026-10-09-store-service-lease-design.md`.
 *
 * A keeper runs once. A guard that stops cannot start again.
 */
class LeaseKeeper(
    private val subject: String,
    private val props: NodeIdClaimProperties,
    private val context: ConfigurableApplicationContext,
    private val renewals: ClaimScheduler,
    private val deadlines: ClaimScheduler,
    private val closeThreadName: String,
    private val clock: () -> Long = System::nanoTime,
) {
    private enum class State { IDLE, CLAIMING, HOLDING, ENDED }

    /** One lease, and the send time of its last granted claim or renewal. */
    private class Entry(val lease: HeldLease, @Volatile var confirmedAt: Long)

    private data class Outcome(val entry: Entry, val result: ClaimResult, val sentAt: Long)

    private val log = LoggerFactory.getLogger(LeaseKeeper::class.java)
    private val entries = CopyOnWriteArrayList<Entry>()
    private val closing = AtomicBoolean(false)
    private val deadlineNanos = props.closeDeadline.toNanos()

    @Volatile
    private var state = State.IDLE

    @Volatile
    private var claimFailure: String? = null
    private var renewHandle: AutoCloseable? = null
    private var watchHandle: AutoCloseable? = null

    /** True while the keeper holds at least one lease. */
    val holding: Boolean get() = state != State.ENDED && entries.isNotEmpty()

    fun now(): Long = clock()

    /** Starts the renew loop. Call it before the first claim. */
    @Synchronized
    fun begin() {
        check(state == State.IDLE) { "$subject: a lease keeper runs once." }
        state = State.CLAIMING
        renewHandle = renewals.schedulePeriodic(props.renewInterval) { renewOnce() }
    }

    /** Adds a granted lease. [sentAt] is the clock reading taken before the claim was sent. */
    fun add(lease: HeldLease, sentAt: Long) {
        entries.add(Entry(lease, sentAt))
        checkClaimPhase()
    }

    /** Ends the claim batch. From now on, a missed deadline closes the context. */
    @Synchronized
    fun held() {
        checkClaimPhase()
        state = State.HOLDING
        armWatch()
    }

    /** Releases every lease, in reverse order of subscription. A second call releases nothing. */
    fun releaseAll() {
        val leases = end()
        if (leases.isEmpty()) return
        try {
            Flux.fromIterable(leases.reversed())
                .flatMap({ lease ->
                    Mono.defer { lease.release() }
                        .timeout(props.operationTimeout)
                        .onErrorResume { error ->
                            log.warn("{} release failed. The lease expires on its own. Cause: {}", lease.label, error.message)
                            Mono.empty()
                        }
                }, leases.size)
                .then()
                .block(props.operationTimeout.plusSeconds(1))
        } catch (error: RuntimeException) {
            log.warn("{}: the release did not end in time. The leases expire on their own. Cause: {}", subject, error.message)
        }
    }

    /** Stops the renewals and keeps the leases. They expire after `ttl`. */
    fun abandon() {
        val leases = end()
        if (leases.isNotEmpty()) {
            log.error("{}: {} leases are kept, not released. They expire within {}s.", subject, leases.size, props.ttl.seconds)
        }
    }

    @Synchronized
    private fun end(): List<HeldLease> {
        state = State.ENDED
        renewHandle?.close()
        renewHandle = null
        watchHandle?.close()
        watchHandle = null
        val leases = entries.map { it.lease }
        entries.clear()
        return leases
    }

    private fun checkClaimPhase() {
        claimFailure?.let { throw IllegalStateException(it) }
        val now = clock()
        entries.firstOrNull { now - it.confirmedAt >= deadlineNanos }?.let { late ->
            throw IllegalStateException(
                "${late.lease.label} passed its close deadline of ${props.closeDeadline.seconds}s " +
                    "before the claims ended. The start fails, and every lease is released."
            )
        }
    }

    private fun renewOnce() {
        val snapshot = entries.toList()
        if (snapshot.isEmpty() || state == State.ENDED) return
        val outcomes = (try {
            Flux.fromIterable(snapshot)
                .flatMap({ entry ->
                    Mono.defer {
                        val sentAt = clock()
                        entry.lease.renew().timeout(props.operationTimeout).map { Outcome(entry, it, sentAt) }
                    }.onErrorResume { error ->
                        log.warn("{} renew failed. Retrying at the next interval. Cause: {}", entry.lease.label, error.message)
                        Mono.empty()
                    }
                }, snapshot.size)
                .collectList()
                .block(props.operationTimeout.plusSeconds(1))
        } catch (error: RuntimeException) {
            log.warn("{}: a renewal round did not end in time. Cause: {}", subject, error.message)
            null
        }) ?: return
        if (state == State.ENDED) return
        outcomes.forEach { (entry, result, sentAt) ->
            when (result) {
                is ClaimResult.Granted -> entry.confirmedAt = maxOf(entry.confirmedAt, sentAt)
                is ClaimResult.Denied -> fail(entry.lease.deniedMessage(result.holder))
                is ClaimResult.Lost -> fail(entry.lease.lostMessage())
            }
        }
    }

    // A refusal during the claim batch fails the start. The context is still
    // in its refresh, so the keeper does not close it.
    private fun fail(reason: String) {
        if (state == State.CLAIMING) {
            if (claimFailure == null) claimFailure = reason
        } else {
            closeContext(reason)
        }
    }

    @Synchronized
    private fun armWatch() {
        if (state != State.HOLDING) return
        val oldest = entries.minOfOrNull { it.confirmedAt } ?: return
        val delay = Duration.ofNanos(maxOf(0L, oldest + deadlineNanos - clock()))
        watchHandle = deadlines.scheduleOnce(delay) { watch() }
    }

    // Runs on the deadline thread. It reads volatile fields and never blocks.
    private fun watch() {
        if (state != State.HOLDING) return
        val now = clock()
        val late = entries.firstOrNull { now - it.confirmedAt >= deadlineNanos }
        if (late == null) {
            armWatch()
        } else {
            closeContext(
                "${late.lease.label} was not renewed within ${props.closeDeadline.seconds}s. " +
                    "The lease may have expired. Closing the application context."
            )
        }
    }

    // The close runs off both schedulers. A close started on a scheduler thread
    // would run destroy on that same thread, and destroy would wait for it.
    private fun closeContext(reason: String) {
        if (!closing.compareAndSet(false, true)) return
        log.error(reason)
        deadlines.runDetached(closeThreadName) { context.close() }
    }
}
```

- [ ] **Step 5: Give `ExecutorClaimScheduler` a thread name parameter**

In `ClaimScheduler.kt`, change the class header and remove the `threadName` property line:

```kotlin
class ExecutorClaimScheduler(private val threadName: String = "nodeid-claim-renew") : ClaimScheduler {
```

Each guard owns two schedulers, one for renewals and one for the deadline. `isSchedulerThread` compares the thread name, so all four names must differ.

- [ ] **Step 6: Rewrite `NodeIdClaimGuard.kt`**

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
 * [scheduler] runs the renewals. [deadlines] runs the close deadline. A test
 * can pass one manual scheduler for both.
 */
class NodeIdClaimGuard(
    private val stores: List<NodeIdClaimStore>,
    private val nodeId: NodeId,
    private val owner: RuntimeOwnerId,
    private val props: NodeIdClaimProperties,
    context: ConfigurableApplicationContext,
    private val scheduler: ClaimScheduler,
    private val deadlines: ClaimScheduler = scheduler,
    clock: () -> Long = System::nanoTime,
) : SmartLifecycle, DisposableBean {

    private val log = LoggerFactory.getLogger(NodeIdClaimGuard::class.java)
    private val keeper = LeaseKeeper(
        "app.nodeid=${nodeId.value}", props, context, scheduler, deadlines, "nodeid-claim-close", clock
    )

    @Volatile
    private var running = false

    override fun start() {
        if (stores.isEmpty()) {
            running = true
            return
        }

        val taken = mutableListOf<NodeIdClaimStore>()
        keeper.begin()
        try {
            stores.sortedBy { it.backendName }.forEach { store ->
                val sentAt = keeper.now()
                when (val result = store.claim(nodeId, owner.value, props.ttl)
                    .timeout(props.operationTimeout).block()) {
                    is ClaimResult.Granted -> {
                        taken.add(store)
                        keeper.add(lease(store), sentAt)
                    }
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
            keeper.held()
        } catch (failure: Throwable) {
            keeper.releaseAll()
            throw failure
        }

        log.info("app.nodeid={} claimed in {}", nodeId.value, taken.joinToString { it.scope })
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
        if (deadlines !== scheduler) deadlines.shutdownNow()
    }

    private fun lease(store: NodeIdClaimStore) = object : HeldLease {
        override val label = "app.nodeid=${nodeId.value} on ${store.backendName}"

        override fun renew() = store.renew(nodeId, owner.value, props.ttl)

        override fun release() = store.release(nodeId, owner.value)

        override fun deniedMessage(holder: String): String =
            NodeIdClaimException(nodeId, store.scope, holder, props.ttl).message!!

        override fun lostMessage(): String =
            "app.nodeid=${nodeId.value} is no longer held in the ${store.scope}. " +
                "This process lost its lease. Closing the application context."
    }
}
```

- [ ] **Step 7: Give the guard its deadline scheduler, and register it as a dependent of each store**

In `NodeIdClaimGuardConfiguration.nodeIdClaimGuard`, add a `beanFactory: ConfigurableListableBeanFactory` parameter. Register each store bean as a dependency, so Spring destroys the guard first. Pass a second scheduler for the deadline:

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
            stores.orderedStream().toList(), nodeId, owner, properties, context, scheduler,
            deadlines = ExecutorClaimScheduler("nodeid-claim-deadline"),
        )
    }
```

Import `org.springframework.beans.factory.config.ConfigurableListableBeanFactory`.

- [ ] **Step 8: Move the existing guard tests to `start` and a fixed clock**

Replace each `afterPropertiesSet()` call with `start()` in both files. Measured on 2026-10-16: 13 such calls in the two files.

```bash
sed -i '' 's/\.afterPropertiesSet()/.start()/g' chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardStartupTests.kt chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardRenewalTests.kt
```

In `NodeIdClaimGuardStartupTests.guard`, pass `clock = { 0L }` as the last argument. The test `the deadline timer is armed after a successful claim` then reads 25 s exactly.

In `NodeIdClaimGuardRenewalTests`, add a clock field and pass it in both constructor calls:

```kotlin
    private var now = 0L

    // in setUp and in the two-store test:
    //     props, context, scheduler, clock = { now }
```

A renewal no longer rearms the deadline. It moves the send time of its lease, and the watch reads that time when it fires. Replace three tests with these:

```kotlin
    @Test
    fun `a granted renew moves the deadline`() {
        val store = FakeClaimStore("redis")
        val (_, scheduler) = setUp(store)

        now = Duration.ofSeconds(20).toNanos()
        scheduler.firePeriodic()
        now = Duration.ofSeconds(30).toNanos()
        scheduler.fireOnce()

        Assertions.assertTrue(store.calls.contains("renew:redis"))
        Assertions.assertTrue(scheduler.detached.isEmpty())
        Assertions.assertEquals(Duration.ofSeconds(15), scheduler.onceDelay)
    }

    @Test
    fun `a renew error does not move the deadline`() {
        val store = FakeClaimStore("redis")
        val (_, scheduler) = setUp(store)

        store.renewAnswer = { throw IllegalStateException("redis is down") }
        now = Duration.ofSeconds(20).toNanos()
        scheduler.firePeriodic()
        now = Duration.ofSeconds(25).toNanos()
        scheduler.fireOnce()

        Assertions.assertEquals(listOf("nodeid-claim-close"), scheduler.detached)
    }

    @Test
    fun `the deadline timer closes the context when it fires`() {
        val store = FakeClaimStore("redis", renewAnswer = { throw IllegalStateException("redis is down") })
        val (_, scheduler) = setUp(store)

        scheduler.firePeriodic()
        now = Duration.ofSeconds(25).toNanos()
        scheduler.fireOnce()

        Assertions.assertEquals(listOf("nodeid-claim-close"), scheduler.detached)
    }
```

The replaced tests are `a granted renew keeps the context open and rearms the deadline`, `a renew error does not rearm the deadline`, and `the deadline timer closes the context when it fires`. Record the three replacements in the commit message. A test that asserts a log level of a release failure now meets `warn`, not `debug`. Change that assertion if one exists, and record it.

- [ ] **Step 9: Run the guard tests and the backend condition test**

```bash
mvn -o -pl chat-core test -Dtest='NodeIdClaimGuard*Tests,LeaseKeeperTimingTests,SharedBackendConditionTests,ExecutorClaimSchedulerTests' > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*(NodeIdClaim|LeaseKeeper|SharedBackend|ExecutorClaim)|FAIL' $LOG
```

Expected: every class passes, with 6 tests in `NodeIdClaimGuardLifecycleTests` and 4 in `LeaseKeeperTimingTests`.

- [ ] **Step 10: Run the claim boot tests**

The claim moved from bean creation to a lifecycle phase. These tests prove that the failure still comes before ready.

```bash
mvn -o -Pintegration -pl chat-deploy-redis -am test -Dtest='RedisClaimBootTests,RedisGrantRestartTests' -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*(Claim|Grant)' $LOG
mvn -o -pl chat-deploy-memory -am test -Dtest=MemoryClaimAbsenceTests -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*Memory' $LOG
```

Expected: both exit 0.

- [ ] **Step 11: Measure the first key mint of a start**

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

- [ ] **Step 12: Commit, then measure five mutations, each alone**

Commit the task. Run the named test class under each mutation. Restore each mutation by absolute path.

| Mutation | Expected |
|---|---|
| Remove the `keeper.releaseAll()` line from `NodeIdClaimGuard.stop()` | `stop releases the lease` fails alone |
| Set `LeasePhases.NODE_ID_GUARD` to `Int.MAX_VALUE - 3000`, after `RootKeyStartup` | `a refused claim on an empty store mints no key` fails |
| In `LeaseKeeper.armWatch`, schedule on `renewals` and not on `deadlines` | `the deadline closes the context while every renewal blocks its thread` fails |
| Move the `schedulePeriodic` line from `begin()` to `held()` | `slow claims keep every early lease alive` fails |
| Set the `flatMap` concurrency of `renewOnce` to `1` | `one renewal round sends every renewal at once` fails |

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/LeaseKeeper.kt chat-core/src/main/kotlin/com/demo/chat/domain/ClaimScheduler.kt chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimGuard.kt chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimGuardConfiguration.kt chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardLifecycleTests.kt chat-core/src/test/kotlin/com/demo/chat/test/domain/LeaseKeeperTimingTests.kt chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardStartupTests.kt chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardRenewalTests.kt chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisClaimBootTests.kt
git commit -m "CHAT-lswjobhz: Run the node id guard as a lifecycle with one renew loop"
fp comment CHAT-plqmcnit "Task 1 done: LeaseKeeper with an independent deadline thread, and the node id guard as a SmartLifecycle. Five mutations measured."
```

---

### Task 2: The service lease guard and its lease names

**Child issue:** `CHAT-ofkodhjp`.

**Files:** see the File Map rows for Task 2.

**Interfaces:**
- Consumes: `HeldLease`, `LeaseKeeper`, `LeasePhases` from Task 1.
- Produces: `interface ServiceLeaseStore { val backendName: String; val scope: String; fun claim(name: String, owner: String, ttl: Duration): Mono<ClaimResult>; fun renew(name: String, owner: String, ttl: Duration): Mono<ClaimResult>; fun release(name: String, owner: String): Mono<Void> }`.
- Produces: `object ServiceLeaseNames { val ALL: List<String>; fun isStoreService(bean: Any): Boolean; fun of(services: Map<String, Any>): List<String>; fun servicesIn(beans: ListableBeanFactory): Map<String, Any> }`. `class ServiceLeaseException(name, scope, holder, ttl)`.
- Produces: `class ServiceLeaseGuard(store: ServiceLeaseStore, services: () -> Map<String, Any>, nodeId, owner, props, context, scheduler, deadlines: ClaimScheduler = scheduler, clock: () -> Long = System::nanoTime) : SmartLifecycle, DisposableBean`, with `val claimed: List<String>`. The holder text is `node <id> <runtime owner id>`. Task 3 adds a `gate` parameter after `services`.

- [ ] **Step 1: Write the failing name tests**

`ServiceLeaseNamesTests.kt`. A JDK proxy gives an instance of each interface with no implementation.

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.ServiceLeaseNames
import com.demo.chat.service.core.*
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.service.security.AuthMetaPersistence
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/** Lease names come from the present service beans. See `CHAT-lswjobhz`, plan review item 5. */
class ServiceLeaseNamesTests {

    private fun stub(type: Class<*>): Any = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, _, _ -> null }

    private val expected = listOf(
        UserPersistence::class.java to "persistence:user",
        TopicPersistence::class.java to "persistence:topic",
        MessagePersistence::class.java to "persistence:message",
        MembershipPersistence::class.java to "persistence:membership",
        AuthMetaPersistence::class.java to "persistence:authmeta",
        KeyValueStore::class.java to "persistence:keyvalue",
        UserIndexService::class.java to "index:user",
        TopicIndexService::class.java to "index:topic",
        MessageIndexService::class.java to "index:message",
        MembershipIndexService::class.java to "index:membership",
        AuthMetaIndex::class.java to "index:authmeta",
        KeyValueIndexService::class.java to "index:keyvalue",
    )

    @Test
    fun `each domain interface maps to its lease name`() {
        expected.forEach { (type, name) ->
            val service = stub(type)
            assertThat(ServiceLeaseNames.isStoreService(service)).describedAs(type.simpleName).isTrue()
            assertThat(ServiceLeaseNames.of(mapOf("bean" to service))).describedAs(type.simpleName).containsExactly(name)
        }
        assertThat(ServiceLeaseNames.ALL).containsExactlyInAnyOrderElementsOf(expected.map { it.second }).isSorted()
    }

    @Test
    fun `a store service with no domain interface fails and names the bean`() {
        assertThatThrownBy { ServiceLeaseNames.of(mapOf("orphanStore" to stub(PersistenceStore::class.java))) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("orphanStore")
            .hasMessageContaining("has no lease name")
    }

    @Test
    fun `two beans with one lease name claim it once, in sorted order`() {
        val names = ServiceLeaseNames.of(
            mapOf(
                "userPersistence" to stub(UserPersistence::class.java),
                "userPersistenceCopy" to stub(UserPersistence::class.java),
                "userIndex" to stub(UserIndexService::class.java),
            )
        )
        assertThat(names).containsExactly("index:user", "persistence:user")
    }

    @Test
    fun `a bean that is not a store service is not one`() {
        assertThat(ServiceLeaseNames.isStoreService("text")).isFalse()
    }

    @Test
    fun `the bean factory gives both kinds of store service and nothing else`() {
        val beans = org.springframework.beans.factory.support.StaticListableBeanFactory(
            mapOf(
                "userPersistence" to stub(UserPersistence::class.java),
                "userIndex" to stub(UserIndexService::class.java),
                "other" to "text",
            )
        )
        assertThat(ServiceLeaseNames.servicesIn(beans).keys).containsExactlyInAnyOrder("userPersistence", "userIndex")
    }
}
```

The six index interfaces are in `IndexService.kt` of `com.demo.chat.service.core`, and `AuthMetaIndex` and `AuthMetaPersistence` are in `com.demo.chat.service.security`, as measured on 2026-10-16. Read the imports before you run.

- [ ] **Step 2: Write the failing guard tests**

`ServiceLeaseGuardTests.kt`:

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.*
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.core.TopicIndexService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import reactor.core.publisher.Mono
import java.lang.reflect.Proxy
import java.time.Duration

/** The store service lease guard. See `CHAT-lswjobhz`. */
class ServiceLeaseGuardTests {

    /** A store whose answers the test sets, per lease name. */
    class FakeLeaseStore : ServiceLeaseStore {
        override val backendName = "redis"
        override val scope = "redis store for key type long"
        val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
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

    private fun stub(type: Class<*>): Any = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, _, _ -> null }

    private val userServices = mapOf(
        "userPersistence" to stub(UserPersistence::class.java),
        "userIndex" to stub(UserIndexService::class.java),
    )

    private fun guard(services: Map<String, Any> = userServices) = ServiceLeaseGuard(
        store, { services }, NodeId(7), owner, NodeIdClaimProperties(null, null, null, null), context, scheduler,
        clock = { 0L },
    )

    @Test
    fun `the guard claims the name of every present service in sorted order`() {
        val guard = guard()
        guard.start()
        assertThat(store.calls).containsExactly("claim:index:user", "claim:persistence:user")
        assertThat(guard.claimed).containsExactly("index:user", "persistence:user")
    }

    @Test
    fun `a store service with no lease name fails the start before any claim`() {
        val guard = guard(userServices + ("orphanStore" to stub(com.demo.chat.service.core.PersistenceStore::class.java)))
        assertThatThrownBy { guard.start() }.hasMessageContaining("orphanStore")
        assertThat(store.calls).isEmpty()
        assertThat(guard.isRunning).isFalse()
    }

    @Test
    fun `a refused claim releases the leases already taken and names the service, scope and holder`() {
        store.denied["index:user"] = "node 9 other@host:1#abc"
        val guard = guard(userServices + ("topicIndex" to stub(TopicIndexService::class.java)))

        assertThatThrownBy { guard.start() }
            .isInstanceOf(ServiceLeaseException::class.java)
            .hasMessageContaining("index:user")
            .hasMessageContaining("redis store for key type long")
            .hasMessageContaining("node 9 other@host:1#abc")
        assertThat(store.calls).containsExactly("claim:index:topic", "claim:index:user", "release:index:topic")
        assertThat(guard.isRunning).isFalse()
    }

    @Test
    fun `the refusal message asks for confirmed termination`() {
        store.denied["index:user"] = "node 9 other@host:1#abc"
        assertThatThrownBy { guard().start() }
            .hasMessageContaining("confirm that its process exited")
            .hasMessageNotContaining("one live instance")
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
        ServiceLeaseGuard(recording, { mapOf("userIndex" to stub(UserIndexService::class.java)) }, NodeId(7), owner,
            NodeIdClaimProperties(null, null, null, null), context, scheduler, clock = { 0L }).start()
        assertThat(owners.single()).isEqualTo("node 7 ${owner.value}")
    }

    @Test
    fun `stop releases in reverse order, and destroy after stop releases nothing`() {
        val guard = guard()
        guard.start()
        guard.stop()
        guard.destroy()
        assertThat(store.calls.filter { it.startsWith("release") })
            .containsExactly("release:persistence:user", "release:index:user")
    }

    @Test
    fun `destroy releases when stop did not run`() {
        val guard = guard(mapOf("userIndex" to stub(UserIndexService::class.java)))
        guard.start()
        guard.destroy()
        assertThat(store.calls).containsExactly("claim:index:user", "release:index:user")
    }

    @Test
    fun `a lost lease closes the context`() {
        guard(mapOf("userIndex" to stub(UserIndexService::class.java))).start()
        store.renewAnswer = { ClaimResult.Lost }
        scheduler.firePeriodic()
        assertThat(scheduler.detached).containsExactly("service-lease-close")
        assertThat(context.isActive).isFalse()
    }

    @Test
    fun `the guard arms the deadline at 25 s`() {
        guard(mapOf("userIndex" to stub(UserIndexService::class.java))).start()
        assertThat(scheduler.onceDelay).isEqualTo(Duration.ofSeconds(25))
        // The clock stays at 0, so a watch that fires early only rearms.
        scheduler.fireOnce()
        assertThat(context.isActive).isTrue()
    }

    @Test
    fun `no store services means no claim and a running guard`() {
        val guard = guard(emptyMap())
        guard.start()
        assertThat(store.calls).isEmpty()
        assertThat(guard.isRunning).isTrue()
        assertThat(guard.phase).isEqualTo(LeasePhases.SERVICE_GUARD)
    }
}
```

The deadline itself is pinned in Task 1, with a moving clock. This class pins that the service guard arms it.

- [ ] **Step 3: Run them and see them fail**

```bash
mvn -o -pl chat-core test -Dtest='ServiceLeaseNamesTests,ServiceLeaseGuardTests' > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:|e: file' $LOG | head
```

Expected: a compile failure on `ServiceLeaseStore` and `ServiceLeaseNames`.

- [ ] **Step 4: Write `ServiceLease.kt`**

```kotlin
package com.demo.chat.domain

import com.demo.chat.service.core.*
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.service.security.AuthMetaPersistence
import org.springframework.beans.factory.ListableBeanFactory
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

/**
 * Maps each store service bean to its lease name, through the domain
 * interface that the bean implements.
 *
 * A store service bean implements `PersistenceStore` or `IndexService`. A
 * store service bean with no mapped domain interface fails the start. So a new
 * store service cannot run without a lease name.
 */
object ServiceLeaseNames {
    private val NAMES: List<Pair<Class<*>, String>> = listOf(
        UserPersistence::class.java to "persistence:user",
        TopicPersistence::class.java to "persistence:topic",
        MessagePersistence::class.java to "persistence:message",
        MembershipPersistence::class.java to "persistence:membership",
        AuthMetaPersistence::class.java to "persistence:authmeta",
        KeyValueStore::class.java to "persistence:keyvalue",
        UserIndexService::class.java to "index:user",
        TopicIndexService::class.java to "index:topic",
        MessageIndexService::class.java to "index:message",
        MembershipIndexService::class.java to "index:membership",
        AuthMetaIndex::class.java to "index:authmeta",
        KeyValueIndexService::class.java to "index:keyvalue",
    )

    /** Every name that the mapping knows, in sorted order. */
    val ALL: List<String> = NAMES.map { it.second }.sorted()

    fun isStoreService(bean: Any): Boolean = bean is PersistenceStore<*, *> || bean is IndexService<*, *, *>

    /** The store service beans of [beans], keyed by bean name. It creates no bean. */
    fun servicesIn(beans: ListableBeanFactory): Map<String, Any> =
        beans.getBeansOfType(PersistenceStore::class.java, false, false) +
            beans.getBeansOfType(IndexService::class.java, false, false)

    /** The names of [services], keyed by bean name, without repeats and in sorted order. */
    fun of(services: Map<String, Any>): List<String> =
        services.flatMap { (bean, service) ->
            NAMES.filter { it.first.isInstance(service) }.map { it.second }.ifEmpty {
                throw IllegalStateException(
                    "The store service bean $bean (${service.javaClass.name}) has no lease name. " +
                        "Add its domain interface to ServiceLeaseNames. See docs/SERVICE-LEASE.md."
                )
            }
        }.distinct().sorted()
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
                "A store service has at most one current lease holder on a shared backend. See docs/SERVICE-LEASE.md.\n" +
                "Stop the other deployment, and confirm that its process exited. A clean stop releases the lease. " +
                "After a crash, the lease expires within ${ttl.seconds}s."
    }
}
```

- [ ] **Step 5: Write `ServiceLeaseGuard.kt`**

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
 * [services] is read at start, when every singleton exists. It maps bean
 * names to the store service beans. `ServiceLeaseNames` derives the lease
 * names from them.
 */
class ServiceLeaseGuard(
    private val store: ServiceLeaseStore,
    private val services: () -> Map<String, Any>,
    private val nodeId: NodeId,
    owner: RuntimeOwnerId,
    private val props: NodeIdClaimProperties,
    context: ConfigurableApplicationContext,
    private val scheduler: ClaimScheduler,
    private val deadlines: ClaimScheduler = scheduler,
    clock: () -> Long = System::nanoTime,
) : SmartLifecycle, DisposableBean {

    private val log = LoggerFactory.getLogger(ServiceLeaseGuard::class.java)
    private val holder = "node ${nodeId.value} ${owner.value}"
    private val keeper = LeaseKeeper(
        "The store service leases of app.nodeid=${nodeId.value}", props, context, scheduler, deadlines,
        "service-lease-close", clock,
    )

    @Volatile
    private var running = false

    /** The names that this guard claimed, in claim order. */
    @Volatile
    var claimed: List<String> = emptyList()
        private set

    override fun start() {
        val names = ServiceLeaseNames.of(services())
        if (names.isEmpty()) {
            running = true
            return
        }
        val taken = mutableListOf<String>()
        keeper.begin()
        try {
            names.forEach { name ->
                val sentAt = keeper.now()
                when (val result = store.claim(name, holder, props.ttl).timeout(props.operationTimeout).block()) {
                    is ClaimResult.Granted -> {
                        taken.add(name)
                        keeper.add(lease(name), sentAt)
                    }
                    is ClaimResult.Denied -> throw ServiceLeaseException(name, store.scope, result.holder, props.ttl)
                    is ClaimResult.Lost, null -> throw IllegalStateException(
                        "The ${store.backendName} store returned $result from a claim of $name. " +
                            "A claim returns Granted or Denied."
                    )
                }
            }
            keeper.held()
        } catch (failure: Throwable) {
            keeper.releaseAll()
            throw failure
        }
        claimed = taken.toList()
        log.info("Store service leases {} claimed in the {}", taken, store.scope)
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
        if (deadlines !== scheduler) deadlines.shutdownNow()
    }

    private fun lease(name: String) = object : HeldLease {
        override val label = "The store service $name"

        override fun renew() = store.renew(name, holder, props.ttl)

        override fun release() = store.release(name, holder)

        override fun deniedMessage(holder: String): String =
            ServiceLeaseException(name, store.scope, holder, props.ttl).message!!

        override fun lostMessage(): String =
            "The store service $name is no longer held in the ${store.scope}. " +
                "This process lost its lease. Closing the application context."
    }
}
```

- [ ] **Step 6: Run the tests**

```bash
mvn -o -pl chat-core test -Dtest='ServiceLeaseNamesTests,ServiceLeaseGuardTests,NodeIdClaimGuard*Tests' > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*(ServiceLease|NodeIdClaim)' $LOG
```

Expected: `ServiceLeaseNamesTests` 5 of 5, `ServiceLeaseGuardTests` 10 of 10, and every node id guard class passes.

- [ ] **Step 7: Commit, then measure two mutations, each alone**

Commit. Restore each mutation by absolute path.

| Mutation | Expected |
|---|---|
| Delete the `keeper.releaseAll()` line of the `catch` block in `start` | Only the refused claim case fails |
| Replace the `ifEmpty { throw ... }` block with nothing | Only the two no-lease-name cases fail |

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/ServiceLease.kt chat-core/src/main/kotlin/com/demo/chat/domain/ServiceLeaseGuard.kt chat-core/src/test/kotlin/com/demo/chat/test/domain/ServiceLeaseNamesTests.kt chat-core/src/test/kotlin/com/demo/chat/test/domain/ServiceLeaseGuardTests.kt
git commit -m "CHAT-lswjobhz: Add the store service lease guard with names from the beans"
fp comment CHAT-ofkodhjp "Task 2 done: ServiceLeaseStore, ServiceLeaseNames from the beans, and ServiceLeaseGuard. Two mutations measured."
```

---

### Task 3: The store write gate and the drain

**Child issue:** `CHAT-fsfhxpvf`.

**Files:** see the File Map rows for Task 3.

**Interfaces:**
- Consumes: `ServiceLeaseGuard` and `ServiceLeaseNames` from Task 2.
- Produces: `interface ProcessLocalIndex` in `com.demo.chat.service.core`. `class StoreWriteGate(drainTimeout: Duration)` with `fun <T> admit(write: () -> Mono<T>): Mono<T>`, `fun closeAndDrain(): Boolean`, `val active: Int` and `val drainTimeout: Duration`. `class StoreWriteRefusedException`. `class StoreWriteGatePostProcessor(gate: ObjectProvider<StoreWriteGate>) : BeanPostProcessor, Ordered`. `class StoreWriteGateConfiguration` with bean `storeWriteGate`, which reads `app.service.lease.drain-timeout` with a default of `10s`.
- Changes: `ServiceLeaseGuard(store, services, gate: StoreWriteGate, nodeId, owner, props, context, scheduler, deadlines, clock)`. `stop` and `destroy` drain the gate before the release.

**Why a proxy.** The servers stop before the guards. A canceled request chain does not prove that its store write stopped. The proxy counts each write from its start to its end in the store. It does not stop the write when a caller cancels. So the drain waits for every write that started.

**Why Lucene is not wrapped.** `LuceneIndexBeans` casts its index beans to `LuceneIndex` at lines 105 to 110 and 170, as measured on 2026-10-16. A JDK proxy would break each cast. Lucene writes go to local files, never to the shared store. So `LuceneIndex` implements `ProcessLocalIndex`, and the post processor skips it.

- [ ] **Step 1: Write the failing gate tests**

`StoreWriteGateTests.kt`:

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.Key
import com.demo.chat.domain.StoreWriteGate
import com.demo.chat.domain.StoreWriteGatePostProcessor
import com.demo.chat.domain.StoreWriteRefusedException
import com.demo.chat.domain.User
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.ProcessLocalIndex
import com.demo.chat.service.core.UserPersistence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.support.StaticListableBeanFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import java.time.Duration

/** The store write gate. See `CHAT-lswjobhz`, plan review item 4. */
class StoreWriteGateTests {

    private val gate = StoreWriteGate(Duration.ofSeconds(2))

    /** A user store whose `add` ends when the test emits on [addDone]. */
    private class SlowUsers : UserPersistence<Long> {
        val addDone: Sinks.Empty<Void> = Sinks.empty()
        val calls = mutableListOf<String>()
        override fun key(): Mono<out Key<Long>> = Mono.empty()
        override fun add(ent: User<Long>): Mono<Void> = Mono.defer { calls.add("add"); addDone.asMono() }
        override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { calls.add("rem") }
        override fun get(key: Key<Long>): Mono<out User<Long>> = Mono.defer { calls.add("get"); Mono.empty() }
        override fun all(): Flux<out User<Long>> = Flux.empty()
    }

    private fun processor() = StoreWriteGatePostProcessor(
        StaticListableBeanFactory(mapOf("storeWriteGate" to gate)).getBeanProvider(StoreWriteGate::class.java)
    )

    @Test
    fun `a write counts from its start to its end`() {
        val users = SlowUsers()
        val gated = processor().postProcessAfterInitialization(users, "userPersistence") as UserPersistence<Long>

        val subscription = gated.add(mock()).subscribe()
        assertThat(gate.active).isEqualTo(1)
        users.addDone.tryEmitEmpty()
        assertThat(gate.active).isEqualTo(0)
        subscription.dispose()
    }

    @Test
    fun `a caller that cancels does not end the count`() {
        val users = SlowUsers()
        val gated = processor().postProcessAfterInitialization(users, "userPersistence") as UserPersistence<Long>

        gated.add(mock()).subscribe().dispose()
        assertThat(gate.active).describedAs("the count after the caller canceled").isEqualTo(1)
        users.addDone.tryEmitEmpty()
        assertThat(gate.active).isEqualTo(0)
    }

    @Test
    fun `a closed gate refuses a write and passes a read`() {
        val users = SlowUsers()
        val gated = processor().postProcessAfterInitialization(users, "userPersistence") as UserPersistence<Long>

        assertThat(gate.closeAndDrain()).isTrue()

        StepVerifier.create(gated.add(mock())).expectError(StoreWriteRefusedException::class.java).verify()
        StepVerifier.create(gated.get(mock())).verifyComplete()
        assertThat(users.calls).containsExactly("get")
    }

    @Test
    fun `a drain waits for an active write and times out when it never ends`() {
        val short = StoreWriteGate(Duration.ofMillis(300))
        short.admit { Mono.never<Void>() }.subscribe()
        val started = System.nanoTime()
        assertThat(short.closeAndDrain()).isFalse()
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofMillis(300))
        assertThat(short.active).isEqualTo(1)
    }

    @Test
    fun `the processor skips a process local index and a bean that is not a store service`() {
        val local = mock<IndexService<Long, Any, Any>>(extraInterfaces = arrayOf(ProcessLocalIndex::class))
        assertThat(processor().postProcessAfterInitialization(local, "luceneIndex")).isSameAs(local)
        assertThat(processor().postProcessAfterInitialization("text", "name")).isEqualTo("text")
    }
}
```

`mock<T>(extraInterfaces = ...)` is the mockito-kotlin form, as measured in `chat-core/pom.xml` on 2026-10-16. If its signature differs, create the mock with `Mockito.mock(IndexService::class.java, Mockito.withSettings().extraInterfaces(ProcessLocalIndex::class.java))`.

- [ ] **Step 2: Write the failing drain tests**

`ServiceLeaseGuardDrainTests.kt`. It reuses `ServiceLeaseGuardTests.FakeLeaseStore`.

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.*
import com.demo.chat.service.core.UserIndexService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import java.lang.reflect.Proxy
import java.time.Duration
import kotlin.concurrent.thread

/** A clean close drains the store writes before the release. See `CHAT-lswjobhz`, plan review item 4. */
class ServiceLeaseGuardDrainTests {

    private val store = ServiceLeaseGuardTests.FakeLeaseStore()
    private val scheduler = ManualClaimScheduler()
    private val context = GenericApplicationContext().apply { refresh() }
    private val userIndex = Proxy.newProxyInstance(
        UserIndexService::class.java.classLoader, arrayOf(UserIndexService::class.java)
    ) { _, _, _ -> null }

    private fun guard(gate: StoreWriteGate) = ServiceLeaseGuard(
        store, { mapOf("userIndex" to userIndex) }, gate, NodeId(7), RuntimeOwnerId.generate("test"),
        NodeIdClaimProperties(null, null, null, null), context, scheduler, clock = { 0L },
    )

    /** Starts a write that ends when the test emits. The write adds `write-end` to the store calls. */
    private fun activeWrite(gate: StoreWriteGate): Sinks.Empty<Void> {
        val done = Sinks.empty<Void>()
        gate.admit { done.asMono().doOnSuccess { store.calls.add("write-end") } }.subscribe()
        return done
    }

    @Test
    fun `stop waits for an active write and then releases`() {
        val gate = StoreWriteGate(Duration.ofSeconds(5))
        val guard = guard(gate).apply { start() }
        val done = activeWrite(gate)

        val stopper = thread { guard.stop() }
        Thread.sleep(200)
        assertThat(store.calls).describedAs("the calls while the write is active").doesNotContain("release:index:user")

        done.tryEmitEmpty()
        stopper.join(5000)
        assertThat(store.calls).endsWith("write-end", "release:index:user")
    }

    @Test
    fun `destroy drains before it releases`() {
        val gate = StoreWriteGate(Duration.ofSeconds(5))
        val guard = guard(gate).apply { start() }
        val done = activeWrite(gate)

        val destroyer = thread { guard.destroy() }
        Thread.sleep(200)
        assertThat(store.calls).doesNotContain("release:index:user")

        done.tryEmitEmpty()
        destroyer.join(5000)
        assertThat(store.calls).endsWith("write-end", "release:index:user")
    }

    @Test
    fun `a write after the close starts is refused`() {
        val gate = StoreWriteGate(Duration.ofSeconds(5))
        guard(gate).apply { start() }.stop()

        StepVerifier.create(gate.admit { Mono.empty<Void>() }).expectError(StoreWriteRefusedException::class.java).verify()
    }

    @Test
    fun `a drain timeout keeps the leases`() {
        val gate = StoreWriteGate(Duration.ofMillis(300))
        val guard = guard(gate).apply { start() }
        gate.admit { Mono.never<Void>() }.subscribe()

        guard.stop()

        assertThat(store.calls.filter { it.startsWith("release") }).isEmpty()
        assertThat(scheduler.periodic).describedAs("the renew loop after the timeout").isNull()
        guard.destroy()
        assertThat(store.calls.filter { it.startsWith("release") }).describedAs("a second drain").isEmpty()
    }
}
```

- [ ] **Step 3: Run them and see them fail**

```bash
mvn -o -pl chat-core test -Dtest='StoreWriteGateTests,ServiceLeaseGuardDrainTests' > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:|e: file' $LOG | head
```

Expected: a compile failure on `StoreWriteGate` and `ProcessLocalIndex`.

- [ ] **Step 4: Write `ProcessLocalIndex.kt`**

```kotlin
package com.demo.chat.service.core

/**
 * Marks an index whose writes stay in local files of this process. The store
 * write gate does not wrap it, because its writes never reach a shared store.
 * See `CHAT-lswjobhz` and `docs/SERVICE-LEASE.md`.
 */
interface ProcessLocalIndex
```

- [ ] **Step 5: Write `StoreWriteGate.kt`**

```kotlin
package com.demo.chat.domain

import com.demo.chat.service.core.ProcessLocalIndex
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.util.ClassUtils
import reactor.core.publisher.Mono
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Duration

/** A closed gate refuses a store write. See `CHAT-lswjobhz`. */
class StoreWriteRefusedException :
    IllegalStateException("The store write gate is closed. This process is releasing its store service leases.")

/**
 * Counts the store writes of a leased process. See `CHAT-lswjobhz` and
 * `docs/SERVICE-LEASE.md`.
 *
 * [admit] starts the write at once and runs it to its end. A caller that
 * cancels does not stop the write, and it does not end the count. A cancel
 * does not prove that the store write stopped.
 */
class StoreWriteGate(val drainTimeout: Duration) {
    private val lock = Object()
    private var count = 0
    private var closed = false

    val active: Int get() = synchronized(lock) { count }

    fun <T> admit(write: () -> Mono<T>): Mono<T> = Mono.defer {
        if (!enter()) return@defer Mono.error<T>(StoreWriteRefusedException())
        val done = Mono.defer(write).doFinally { exit() }.toFuture()
        Mono.fromFuture(done, true)
    }

    /** Refuses new writes, and waits for the active ones. Returns false when the drain timeout passes first. */
    fun closeAndDrain(): Boolean = synchronized(lock) {
        closed = true
        val end = System.nanoTime() + drainTimeout.toNanos()
        while (count > 0) {
            val left = end - System.nanoTime()
            if (left <= 0) return false
            lock.wait(maxOf(1L, left / 1_000_000))
        }
        true
    }

    private fun enter(): Boolean = synchronized(lock) {
        if (closed) false else {
            count++
            true
        }
    }

    private fun exit() = synchronized(lock) {
        count--
        if (count == 0) lock.notifyAll()
    }
}

/**
 * Puts a gate proxy around each store service bean. See `CHAT-lswjobhz`.
 *
 * A store service bean implements `PersistenceStore` or `IndexService`. A
 * `ProcessLocalIndex` is not wrapped. The proxy implements every interface of
 * the bean, so an injection by interface still resolves.
 */
class StoreWriteGatePostProcessor(private val gate: ObjectProvider<StoreWriteGate>) : BeanPostProcessor, Ordered {

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (!ServiceLeaseNames.isStoreService(bean) || bean is ProcessLocalIndex) return bean
        val type = bean.javaClass
        return Proxy.newProxyInstance(
            type.classLoader, ClassUtils.getAllInterfacesForClass(type, type.classLoader), GatedWrites(bean, gate.getObject())
        )
    }

    private class GatedWrites(private val target: Any, private val gate: StoreWriteGate) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? = when {
            method.name == "equals" && method.parameterCount == 1 -> proxy === args!![0]
            method.name == "hashCode" && method.parameterCount == 0 -> System.identityHashCode(proxy)
            method.name == "toString" && method.parameterCount == 0 -> "gated $target"
            method.name in WRITES && Mono::class.java.isAssignableFrom(method.returnType) ->
                gate.admit { call(method, args) as Mono<Any?> }
            else -> call(method, args)
        }

        private fun call(method: Method, args: Array<out Any?>?): Any? =
            try {
                method.invoke(target, *(args ?: emptyArray()))
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
    }

    companion object {
        /** The store service methods that write. Every other method passes through. */
        val WRITES = setOf("add", "rem", "addEnriched")
    }
}

/** Registers the gate and its post processor. The backend lease configurations import it. */
@Configuration(proxyBeanMethods = false)
class StoreWriteGateConfiguration {

    @Bean
    fun storeWriteGate(@Value("\${app.service.lease.drain-timeout:10s}") drainTimeout: Duration): StoreWriteGate =
        StoreWriteGate(drainTimeout)

    companion object {
        // A static bean method keeps the post processor out of the early
        // creation of this configuration.
        @Bean
        @JvmStatic
        fun storeWriteGatePostProcessor(gate: ObjectProvider<StoreWriteGate>): StoreWriteGatePostProcessor =
            StoreWriteGatePostProcessor(gate)
    }
}
```

- [ ] **Step 6: Mark the Lucene index as local**

In `LuceneIndex.kt`, add `ProcessLocalIndex` to the supertypes:

```kotlin
) : IndexService<T, E, IndexSearchRequest>, ProcessLocalIndex {
```

Import `com.demo.chat.service.core.ProcessLocalIndex`. Read the class headers of the six Lucene index classes of `LuceneIndexBeans`. Each must extend `LuceneIndex`. Measured on 2026-10-16: `KeyValueLuceneIndex` extends it. If a class does not, add the marker to that class too, and record it in the commit message.

- [ ] **Step 7: Drain in the guard**

In `ServiceLeaseGuard`, add the `gate: StoreWriteGate` parameter after `services`, and replace `stop` and `destroy`:

```kotlin
    @Volatile
    private var drained = false

    override fun stop() {
        drainAndRelease()
        running = false
    }

    override fun destroy() {
        drainAndRelease()
        scheduler.shutdownNow()
        if (deadlines !== scheduler) deadlines.shutdownNow()
    }

    // The renewals continue during the drain. A drain that times out keeps the
    // leases, so they expire after ttl and not while a write is active.
    @Synchronized
    private fun drainAndRelease() {
        if (drained) return
        drained = true
        if (gate.closeAndDrain()) {
            keeper.releaseAll()
        } else {
            log.error(
                "{} store writes were still active {}s after the close started. The leases are kept.",
                gate.active, gate.drainTimeout.seconds
            )
            keeper.abandon()
        }
    }
```

Update the KDoc of the class: "[stop] closes the gate, waits for the active writes, and then releases. See `The write drain` in the spec." In `ServiceLeaseGuardTests`, pass `StoreWriteGate(Duration.ofSeconds(1))` as the gate in each constructor call.

- [ ] **Step 8: Run the tests**

```bash
mvn -o -pl chat-core test -Dtest='StoreWriteGateTests,ServiceLeaseGuardDrainTests,ServiceLeaseGuardTests,ServiceLeaseNamesTests' > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*(StoreWrite|ServiceLease)' $LOG
mvn -o -pl chat-core,chat-index-lucene test > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:|FAIL' $LOG | tail -5
```

Expected: `StoreWriteGateTests` 5 of 5, `ServiceLeaseGuardDrainTests` 4 of 4, `ServiceLeaseGuardTests` 10 of 10. `chat-index-lucene` passes with no change to its tests.

- [ ] **Step 9: Commit, then measure three mutations, each alone**

Commit. Restore each mutation by absolute path.

| Mutation | Expected |
|---|---|
| `drainAndRelease` calls `keeper.releaseAll()` and skips `gate.closeAndDrain()` | `stop waits for an active write and then releases`, `destroy drains before it releases`, and `a write after the close starts is refused` fail |
| `admit` returns `Mono.defer(write).doFinally { exit() }`, so a cancel ends the count | `a caller that cancels does not end the count` fails |
| `drainAndRelease` calls `keeper.releaseAll()` when the drain times out | `a drain timeout keeps the leases` fails |

```bash
git add chat-core/src/main/kotlin/com/demo/chat/service/core/ProcessLocalIndex.kt chat-core/src/main/kotlin/com/demo/chat/domain/StoreWriteGate.kt chat-core/src/main/kotlin/com/demo/chat/domain/ServiceLeaseGuard.kt chat-core/src/test/kotlin/com/demo/chat/test/domain/StoreWriteGateTests.kt chat-core/src/test/kotlin/com/demo/chat/test/domain/ServiceLeaseGuardDrainTests.kt chat-core/src/test/kotlin/com/demo/chat/test/domain/ServiceLeaseGuardTests.kt chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/impl/LuceneIndex.kt
git commit -m "CHAT-lswjobhz: Drain the store writes before the service lease release"
fp comment CHAT-fsfhxpvf "Task 3 done: StoreWriteGate, its post processor, and the drain in ServiceLeaseGuard. Three mutations measured."
```

---

### Task 4: The Redis lease store

**Child issue:** `CHAT-jilnadfu`.

**Files:** see the File Map rows for Task 3.

**Interfaces:**
- Consumes: `ServiceLeaseStore`, `ServiceLeaseGuard`, `ServiceLeaseNames.servicesIn` from Task 2. `StoreWriteGate` and `StoreWriteGateConfiguration` from Task 3. `NodeIdClaimGuardConfiguration` supplies `NodeId`, `RuntimeOwnerId` and `NodeIdClaimProperties`.
- Produces: bean `redisServiceLeaseStore`, bean `serviceLeaseGuard`, bean `storeWriteGate` and the gate post processor, active when `app.service.core.persistence=redis`.

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

import com.demo.chat.domain.*
import com.demo.chat.persistence.redis.impl.RedisServiceLeaseStore
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * Registers the Redis store service lease, its guard and the store write
 * gate. See `CHAT-lswjobhz`.
 *
 * A lease lives in the persistence backend, so the condition reads
 * `app.service.core.persistence` alone. The guard takes the store as a
 * constructor parameter, so Spring destroys the guard before the store.
 */
@Configuration("redisServiceLeaseConfiguration")
@ConditionalOnProperty(name = ["app.service.core.persistence"], havingValue = "redis")
@Import(NodeIdClaimGuardConfiguration::class, StoreWriteGateConfiguration::class)
class ServiceLeaseConfiguration {

    @Bean("redisServiceLeaseStore")
    fun redisServiceLeaseStore(
        template: ReactiveStringRedisTemplate,
        @Value("\${app.key.type}") keyType: String,
    ): ServiceLeaseStore = RedisServiceLeaseStore(template, keyType)

    @Bean("serviceLeaseGuard")
    fun serviceLeaseGuard(
        @Qualifier("redisServiceLeaseStore") store: ServiceLeaseStore,
        gate: StoreWriteGate,
        beans: ListableBeanFactory,
        nodeId: NodeId,
        owner: RuntimeOwnerId,
        properties: NodeIdClaimProperties,
        context: ConfigurableApplicationContext,
    ): ServiceLeaseGuard = ServiceLeaseGuard(
        store, { ServiceLeaseNames.servicesIn(beans) }, gate, nodeId, owner, properties, context,
        ExecutorClaimScheduler("service-lease-renew"),
        deadlines = ExecutorClaimScheduler("service-lease-deadline"),
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
            Assertions.assertTrue(context.getBeansOfType(StoreWriteGate::class.java).isEmpty())
        }
    }
```

Node id 12 belongs to this class, and the two tests that use it close their context before the next start. Import `com.demo.chat.domain.ServiceLeaseStore` and `com.demo.chat.domain.StoreWriteGate`.

```bash
mvn -o -Pintegration -pl chat-deploy-redis -am test -Dtest=RedisClaimBootTests -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*RedisClaimBoot' $LOG
```

Expected: 3 of 3.

- [ ] **Step 8: Commit**

```bash
git add chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisLeaseScripts.kt chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisNodeIdClaimStore.kt chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisServiceLeaseStore.kt chat-persistence-redis/src/main/kotlin/com/demo/chat/config/persistence/redis/ServiceLeaseConfiguration.kt chat-persistence-redis/src/test/kotlin/com/demo/chat/test/persistence/redis/RedisServiceLeaseStoreTests.kt chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisClaimBootTests.kt
git commit -m "CHAT-lswjobhz: Add the Redis store service lease"
fp comment CHAT-jilnadfu "Task 4 done: RedisServiceLeaseStore, its configuration, and the contract tests."
```

---

### Task 5: The Cassandra lease store

**Child issue:** `CHAT-wkxxmqsy`.

**Files:** see the File Map rows for Task 4.

**Interfaces:**
- Consumes: Task 2 and Task 3 types. `NodeIdClaimConfiguration.keyspaceOf(properties)` in `chat-persistence-cassandra`.
- Produces: bean `cassandraServiceLeaseStore`, bean `serviceLeaseGuard`, bean `storeWriteGate` and the gate post processor, active when `app.service.core.persistence=cassandra`. Table `service_lease` in both keyspace scripts.

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

import com.demo.chat.domain.*
import com.demo.chat.persistence.cassandra.impl.CassandraServiceLeaseStore
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.cassandra.autoconfigure.CassandraProperties
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate

/** Registers the Cassandra store service lease, its guard and the store write gate. See `CHAT-lswjobhz`. */
@Configuration("cassandraServiceLeaseConfiguration")
@ConditionalOnProperty(name = ["app.service.core.persistence"], havingValue = "cassandra")
@Import(NodeIdClaimGuardConfiguration::class, StoreWriteGateConfiguration::class)
class ServiceLeaseConfiguration {

    @Bean("cassandraServiceLeaseStore")
    fun cassandraServiceLeaseStore(template: ReactiveCassandraTemplate, properties: CassandraProperties): ServiceLeaseStore =
        CassandraServiceLeaseStore(template, NodeIdClaimConfiguration.keyspaceOf(properties))

    @Bean("serviceLeaseGuard")
    fun serviceLeaseGuard(
        @Qualifier("cassandraServiceLeaseStore") store: ServiceLeaseStore,
        gate: StoreWriteGate,
        beans: ListableBeanFactory,
        nodeId: NodeId,
        owner: RuntimeOwnerId,
        properties: NodeIdClaimProperties,
        context: ConfigurableApplicationContext,
    ): ServiceLeaseGuard = ServiceLeaseGuard(
        store, { ServiceLeaseNames.servicesIn(beans) }, gate, nodeId, owner, properties, context,
        ExecutorClaimScheduler("service-lease-renew"),
        deadlines = ExecutorClaimScheduler("service-lease-deadline"),
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
fp comment CHAT-wkxxmqsy "Task 5 done: the service_lease table, CassandraServiceLeaseStore, and the contract tests."
```

---

### Task 6: Writers stop before the release

**Child issue:** `CHAT-olsjrdsl`.

**Files:** see the File Map rows for Task 5.

**Interfaces:**
- Consumes: `LeasePhases.WRITERS` from Task 1. The drain of Task 3 is not a consumed interface, so this task can run before Task 3.
- Produces: `class CommandRuntimeLifecycle(runtime: MemoryCommandRuntime<*, *>) : SmartLifecycle` at phase `LeasePhases.WRITERS`.

**The writer inventory, measured on 2026-10-16.** The core process of the Redis and Cassandra boot tests runs `app.service.composite` with `app.command.bus=memory`. In main code, one background loop writes a store on its own schedule: the rescan of `CommandDispatcher`, inside `MemoryCommandRuntime`. Spring closes the runtime at bean destruction, through `destroyMethod = "close"`. That is after the guards release. The other `boundedElastic` work (`SerialWriter`, `VectorStoreMessageVectorIndexer`, `MessageReindexServiceImpl`, `RoomPublications`) runs per request. The handlers of `BackendRuntime` run detached from the request that admitted the command. Each of these reaches the shared store through a store service bean, so the write gate of Task 3 counts it, and the drain waits for it. A command that has not reached the store when the gate closes gets a refusal. Review Focus 1 holds that case. The Lucene mutation scheduler writes local index files, not the shared store.

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
fp comment CHAT-olsjrdsl "Task 6 done: CommandRuntimeLifecycle at the writers phase, and the phase order pin."
```

---

### Task 7: Redis boot tests

**Child issue:** `CHAT-elneflzs`.

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

Give `startWhenClaimFree` the same `vararg extra: String` parameter, and pass it to each `start` call. Then retry on either lease:

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
import com.demo.chat.domain.ServiceLeaseGuard
import com.demo.chat.domain.ServiceLeaseNames
import com.demo.chat.domain.StoreWriteRefusedException
import com.demo.chat.domain.User
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.addUser
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.findsHandle
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.start
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.startWhenClaimFree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * The store service lease on a Redis deployment. See `CHAT-lswjobhz` and
 * `CHAT-ocpojbyy`.
 *
 * Node ids 52 to 58 belong to this class. See docs/NODEID-CLAIM.md. The
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
        startWhenClaimFree(null, 52).use { context ->
            val guard = context.getBean(ServiceLeaseGuard::class.java)
            assertThat(guard.claimed).describedAs("the claimed names").isEqualTo(ServiceLeaseNames.of(ServiceLeaseNames.servicesIn(context)))
            assertThat(guard.claimed).describedAs("every store service of a full core").isEqualTo(ServiceLeaseNames.ALL)
        }
        start(null, 52).use { }
    }

    @Test
    fun `a swap after a clean close finds the user through the rebuilt index`() {
        startWhenClaimFree(null, 53).use { a -> a.addUser("swaprelease") }
        start(null, 54).use { b -> assertThat(b.findsHandle("swaprelease")).isTrue() }
    }

    @Test
    fun `a seeded lease refuses a start until it expires`() {
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

    @Test
    fun `a write that is active during the close ends before the release`() {
        val context = startWhenClaimFree(null, 58, "spring.main.sources=${SlowUserWrites::class.java.name}")
        val leases = template()
        val releasedAt = AtomicLong(0)
        val watch = thread {
            val end = System.nanoTime() + Duration.ofSeconds(20).toNanos()
            while (releasedAt.get() == 0L && System.nanoTime() < end) {
                if (leases.hasKey("chat:lease:long:persistence:user").block() == false) releasedAt.set(System.nanoTime())
                else Thread.sleep(10)
            }
        }
        @Suppress("UNCHECKED_CAST")
        val users = context.getBean("userPersistence") as UserPersistence<Long>
        val key = users.key().block(Duration.ofSeconds(5))!!
        val write = users.add(User.create(key, "Drain", "drainwrite", "http://u")).toFuture()
        assertThat(SlowUserWrites.entered.await(5, TimeUnit.SECONDS)).isTrue()

        context.close()
        runCatching { write.get(10, TimeUnit.SECONDS) }
        watch.join(20_000)

        assertThat(releasedAt.get()).describedAs("the release time").isNotZero()
        assertThat(SlowUserWrites.finishedAt.get()).describedAs("the write end").isNotZero().isLessThan(releasedAt.get())
        val refused = runCatching { users.add(User.create(key, "Late", "latewrite", "http://u")).block(Duration.ofSeconds(5)) }
        assertThat(refused.exceptionOrNull()).isInstanceOf(StoreWriteRefusedException::class.java)
    }

    /** Delays each user write by 2 s. The delay runs inside the write gate. */
    @Configuration
    open class SlowUserWrites {
        companion object {
            val entered = CountDownLatch(1)
            val finishedAt = AtomicLong(0)

            // PriorityOrdered runs before the gate post processor, so the gate
            // proxy wraps this delay.
            @Bean
            @JvmStatic
            fun slowUserWrites(): BeanPostProcessor = object : BeanPostProcessor, PriorityOrdered {
                override fun getOrder() = Ordered.HIGHEST_PRECEDENCE

                @Suppress("UNCHECKED_CAST")
                override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
                    if (beanName != "userPersistence") return bean
                    val target = bean as UserPersistence<Long>
                    return object : UserPersistence<Long> by target {
                        override fun add(ent: User<Long>): Mono<Void> = Mono.defer {
                            entered.countDown()
                            Mono.delay(Duration.ofSeconds(2)).then(target.add(ent))
                                .doFinally { finishedAt.set(System.nanoTime()) }
                        }
                    }
                }
            }
        }
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

The seeded lease models a crashed holder. It proves the lease mechanics, not a safe swap. A safe swap needs confirmed termination. See the spec section `The swap`.

`ServiceLeaseNames.servicesIn(context)` reads the context as a `ListableBeanFactory`. If `guard.claimed` differs from `ServiceLeaseNames.ALL`, the core has a store service set other than the twelve. Record the measured set in the ledger, and keep the first assertion alone.

`spring.main.sources` adds a source to `SpringApplicationBuilder`. If the measured start ignores it, add an overload `start(root, nodeId, sources: Array<Class<*>>, vararg extra)` that calls `.sources(*sources)`, and record the change.

- [ ] **Step 4: Run them**

```bash
mvn -o -Pintegration -pl chat-deploy-redis -am test -Dtest='RedisServiceLeaseBootTests,RedisSharedStoreIndexTests,RedisLuceneFilesRestartTests,RedisClaimBootTests' -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E 'Tests run:.*Redis' $LOG
```

Expected: `RedisServiceLeaseBootTests` 6 of 6, `RedisSharedStoreIndexTests` 1 of 1, and the two other classes pass.

- [ ] **Step 5: Commit, then measure four mutations, each alone**

Commit. Then run `RedisServiceLeaseBootTests` and `RedisSharedStoreIndexTests` under each mutation, and restore by absolute path after each one.

| Mutation | Expected |
|---|---|
| `ServiceLeaseGuard.start` claims no name (`names()` replaced by `emptyList()`) | The refusal test fails |
| `ServiceLeaseGuard.stop` and `NodeIdClaimGuard.stop` do not call `releaseAll` | The restart test fails, with a `NodeIdClaimException` or a `ServiceLeaseException` |
| `ServiceLeaseGuard.destroy` and `NodeIdClaimGuard.destroy` do not call `releaseAll` | The failed start test fails |
| `ServiceLeaseGuard.drainAndRelease` skips `gate.closeAndDrain()` | The drain test fails |

```bash
git add chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisLuceneFilesRestartTests.kt chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisSharedStoreIndexTests.kt chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisServiceLeaseBootTests.kt
git commit -m "CHAT-lswjobhz: Prove the service lease on a Redis deployment"
fp comment CHAT-elneflzs "Task 7 done: refusal, swap, restart, failed start, loss and drain on Redis, with four mutations."
```

---

### Task 8: Cassandra boot tests

**Child issue:** `CHAT-wkdayxhc`.

**Files:** `chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraServiceLeaseBootTests.kt`.

**Interfaces:**
- Consumes: Task 5, Task 6 and the boot harness of `CassandraClaimBootTests`.

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
fp comment CHAT-wkdayxhc "Task 8 done: refusal and restart on Cassandra."
```

---

### Task 9: One store-backed context at a time

**Child issue:** `CHAT-czcmnsvu`.

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
3. A class that runs two cores at once on purpose: it becomes a refusal test, in the shape of Task 7 Step 2.

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
fp comment CHAT-czcmnsvu "Task 9 done: <n> classes repaired. --ci exit 0 with <counts>."
```

---

### Task 10: Documents

**Child issue:** `CHAT-uusjnrgt`.

**Depends on:** `CHAT-jgegpcoi`, for register space. Merge `master` into the branch first if `CHAT-jgegpcoi` merged after Task 9.

- [ ] **Step 1: Write `docs/SERVICE-LEASE.md`**

Sections: the rule and the lease names, the scope and the backend, `The guarantee` with its three cases, the swap procedure, the properties with `app.service.lease.drain-timeout`, the start and stop order table, the write drain, the store recreation step for `service_lease`, split deployments, and the limit of Review Focus 1.

The rule section must say: a lease gives at most one current holder. It cannot prove that an old process stopped. No sentence promises exactly one live instance.

The swap procedure must say: a safe swap needs confirmed termination of the old process. Confirm that the process exited before you start the replacement. A wait of one `ttl` after the old process was last seen is not proof, because a paused process can resume. A swap without confirmed termination keeps the case 3 risk.

- [ ] **Step 2: Correct `docs/NODEID-CLAIM.md`**

Replace the correction of 2026-09-27 with the measured fact: a clean close releases the lease on Redis and on Cassandra, since `CHAT-lswjobhz`. Add the node ids: `RedisServiceLeaseBootTests` 52 to 58, `CassandraServiceLeaseBootTests` 45 to 47. Update the `RedisSharedStoreIndexTests` row: it now uses 50 and 51 for the refusal.

- [ ] **Step 3: Update the KDoc of `IndexStartSequence`**

Read the KDoc first, and keep its other statements. Change the start scan limit: the store service lease now keeps another core of the same services off the store during the scan. A split deployment remains outside that protection.

- [ ] **Step 4: Bind and check drift**

```bash
drift refs docs/NODEID-CLAIM.md; drift refs chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/IndexStartSequence.kt
drift link docs/SERVICE-LEASE.md chat-core/src/main/kotlin/com/demo/chat/domain/ServiceLeaseGuard.kt chat-core/src/main/kotlin/com/demo/chat/domain/LeaseKeeper.kt chat-core/src/main/kotlin/com/demo/chat/domain/StoreWriteGate.kt chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisServiceLeaseStore.kt chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraServiceLeaseStore.kt
drift check; git diff --check; echo exit=$?
```

Review each bound document before any relink. Never relink stale prose.

- [ ] **Step 5: Record the gate in `docs/BUILD-HEALTH.md` and add the register section**

`docs/BUILD-HEALTH.md` records the Task 9 counts and their cause. `forward-register.md` gains one dated section: the rule, the guarantee, the phases, the drain, the measured mutations, both gate totals, and the closed `CHAT-ocpojbyy`. Do not move the `Checkout` row. Confirm the file stays under 65,536 bytes.

- [ ] **Step 6: Run both gates, commit and hand off**

```bash
shell-scripts/build-health.sh > $LOG 2>&1; echo exit=$?; tail -20 $LOG
DOCKER_CONFIG=$(mktemp -d) shell-scripts/build-health.sh --ci > $LOG 2>&1; echo exit=$?; tail -20 $LOG
git add docs/SERVICE-LEASE.md docs/NODEID-CLAIM.md docs/BUILD-HEALTH.md chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/storage/IndexStartSequence.kt drift.lock forward-register.md
git commit -m "CHAT-lswjobhz: Record the store service lease"
fp comment CHAT-uusjnrgt "Task 10 done: documents, drift and register. Both gates green."
fp comment CHAT-ocpojbyy "Closed by CHAT-lswjobhz: a clean Redis close releases the node id lease. RedisServiceLeaseBootTests pins it."
```

Set each child issue to `done` after its task commit. Report the gate totals to the owner, and stop. Ask the owner whether to push the branch and open a pull request.

---

## Spec coverage

| Spec section | Task |
|---|---|
| The rule, at most one current lease holder | 2, 4, 5, 10 |
| The guarantee, cases 1 and 2 | 1 (deadline, slow renewals), 2 (loss), 7 (loss at boot) |
| The guarantee, case 3 | 10, recorded |
| The swap, confirmed termination | 2 (refusal text), 10 |
| What a process claims, names from the beans | 2, 7 |
| The lease store, Redis and Cassandra | 4, 5 |
| Store shape check and no truncate | 5 |
| The guard, sorted claims, release on refusal, holder text | 2 |
| The renew loop: renewal during claims, one round together, an independent deadline | 1 |
| The write drain | 3, 7 |
| The lifecycle order, start and stop | 1, 2, 6 |
| Other writers | 6 |
| Failed start release | 1, 2, 3, 7 |
| The node id claim before the first key mint | 1 |
| `CHAT-ocpojbyy` | 1, 7, 10 |
| Split deployments | 10, recorded |
| Two cores on Redis, swap, release on close | 7 |
| Cassandra refusal | 8 |
| Mutation proofs | 1, 2, 3, 6, 7 |
| The suite and the rule | 9 |
| Documents | 10 |

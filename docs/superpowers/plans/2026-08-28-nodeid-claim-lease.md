# Node id claim lease Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This repository forbids subagent-driven development. See `CLAUDE.md`.

**Goal:** Make a Redis or Cassandra deployment fail to start when a live `app.nodeid` claim for the same key space is already held in the same shared store.

**Architecture:** A `NodeIdClaimStore` contract in `chat-core` exposes `claim`, `renew`, and `release`. Redis implements it with `SET NX PX` and Lua owner checks. Cassandra implements it with a `node_claim` table and lightweight transactions. One `NodeIdClaimGuard` claims during context refresh, renews on its own scheduler, and closes the context when the lease is lost.

**Tech Stack:** Kotlin, Spring Boot, Project Reactor, Spring Data Redis reactive, Spring Data Cassandra reactive, JUnit 5, Testcontainers, Maven.

**Spec:** `docs/superpowers/specs/2026-08-28-nodeid-claim-lease-design.md`

## Global Constraints

- Kotlin is the primary language. Follow the file and package layout of the module you edit.
- Prose in comments, messages, and commits uses plain controlled English. Short sentences. Active voice. One instruction per sentence. No semicolons.
- Every new integration test carries `@Tag("integration")`. The root `pom.xml` excludes that group unless `-Pintegration` runs.
- `KNOWN_FAILING_INTEGRATION` in `shell-scripts/build-health.sh` stays empty. A container-backed regression is signal.
- `truncate-long.cql` and `truncate-uuid.cql` never gain `node_claim`. A live lease expires. A cleanup script must not delete it.
- Property defaults: `app.nodeid.claim.ttl=30s`, `renew-interval=10s`, `safety-margin=5s`, `operation-timeout=5s`.
- Property rules: `ttl >= 1s` and whole seconds. `renew-interval <= ttl / 3`. `safety-margin > 0` and `< ttl`. `operation-timeout < renew-interval`. `ttl - safety-margin > renew-interval`.
- Do not use `grep` or `find` for symbol lookup. Use the language server or treesitter tools.
- Every `mvn -pl` command outside `chat-core` carries `-am`. This task chain adds new types to `chat-core` and new CQL to `shared-resources-cassandra`. Without `-am`, Maven resolves the stale installed artifact and the module compiles against the old code.

### Node id allocation

Every container-backed test that activates a claim store must use its own `app.nodeid`. Spring caches contexts, so two open contexts against one store would collide. This table is the allocation. Do not reuse a value.

| Module and test | `app.nodeid` |
|---|---|
| `chat-core` unit tests (no store) | any |
| `chat-persistence-redis` claim store tests | 100 to 109 |
| `chat-persistence-cassandra` claim store tests | 200 to 209 |
| `chat-deploy-redis` `RedisDeployBootTests` (existing) | 1 |
| `chat-deploy-redis` duplicate boot test | 11 |
| `chat-deploy-redis` memory key seam boot test | 12 |
| `chat-deploy-cassandra` `CassandraDeployTest` (existing) | 1 |
| `chat-deploy-cassandra` duplicate boot test | 21 |
| `chat-deploy-cassandra` memory key seam boot test | 22 |
| `chat-deploy-memory`, `chat-deploy-kafka` (no claim) | 1 |

---

## File structure

| File | Responsibility |
|---|---|
| `chat-core/.../domain/ClaimResult.kt` | The three claim outcomes |
| `chat-core/.../domain/NodeIdClaimStore.kt` | The store contract |
| `chat-core/.../domain/NodeIdClaimException.kt` | The one duplicate-node message template |
| `chat-core/.../domain/NodeIdClaimProperties.kt` | Bound properties and their rules |
| `chat-core/.../domain/ClaimScheduler.kt` | Scheduling seam, plus the executor implementation |
| `chat-core/.../domain/RuntimeOwnerId.kt` | The per-process owner value |
| `chat-core/.../domain/NodeIdClaimGuard.kt` | Claim, renew, deadline, release |
| `chat-core/.../domain/ConditionalOnSharedBackend.kt` | The OR condition on the two selectors |
| `chat-core/.../domain/NodeIdClaimGuardConfiguration.kt` | The guard beans, imported by every store config |
| `chat-persistence-redis/.../persistence/redis/impl/RedisNodeIdClaimStore.kt` | The Redis store |
| `chat-persistence-redis/.../config/persistence/redis/NodeIdClaimConfiguration.kt` | Registers the Redis store |
| `chat-persistence-cassandra/.../persistence/cassandra/impl/CassandraNodeIdClaimStore.kt` | The Cassandra store |
| `chat-persistence-cassandra/.../config/persistence/cassandra/NodeIdClaimConfiguration.kt` | Registers the Cassandra store |
| `shared-resources-cassandra/src/main/resources/keyspace-long.cql` | Adds `node_claim` |
| `shared-resources-cassandra/src/main/resources/keyspace-uuid.cql` | Adds `node_claim` |
| `docs/NODEID-CLAIM.md` | Operator document, including the upgrade statement |

---

### Task 1: Prove the Cassandra expiry behaviour

This task is the probe for the load-bearing unverified claim. It adds no production Kotlin. If the probe fails, stop and report. The fallback is an explicit `expires_at` column, and that changes the design.

**Files:**
- Modify: `shared-resources-cassandra/src/main/resources/keyspace-long.cql`
- Modify: `shared-resources-cassandra/src/main/resources/keyspace-uuid.cql`
- Test: `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/NodeClaimTableProbeTests.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: the table `node_claim(node_id int PRIMARY KEY, owner_id text)` in keyspaces `chat_long` and `chat_uuid`.

- [ ] **Step 1: Add the table to the long keyspace**

Add this block to `shared-resources-cassandra/src/main/resources/keyspace-long.cql`, after the `chat_long.keys` table.

```sql
-- Holds the app.nodeid lease. One row per claimed node id.
-- The row carries a TTL, so a crashed deployment releases its own id.
-- This table is deliberately absent from truncate-long.cql. A live lease
-- must expire. A test cleanup script must not delete it.
CREATE TABLE chat_long.node_claim(
    node_id  int,
    owner_id text,
    PRIMARY KEY(node_id)
);
```

- [ ] **Step 2: Add the table to the uuid keyspace**

Add the same block to `shared-resources-cassandra/src/main/resources/keyspace-uuid.cql`, with `chat_uuid` in place of `chat_long`. The column types do not change. The claim holds a node id, not a domain key.

```sql
-- Holds the app.nodeid lease. One row per claimed node id.
-- The row carries a TTL, so a crashed deployment releases its own id.
-- This table is deliberately absent from truncate-uuid.cql. A live lease
-- must expire. A test cleanup script must not delete it.
CREATE TABLE chat_uuid.node_claim(
    node_id  int,
    owner_id text,
    PRIMARY KEY(node_id)
);
```

- [ ] **Step 3: Write the probe test**

Create `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/NodeClaimTableProbeTests.kt`.

```kotlin
package com.demo.chat.test.persistence.integration

import com.demo.chat.test.CassandraTestContainerConfiguration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.test.context.TestPropertySource
import java.time.Duration

/**
 * Probe for one load-bearing claim in the design.
 *
 * The claim: when owner_id expires, node_claim holds a primary key with no
 * live columns, and IF NOT EXISTS then treats the row as absent.
 *
 * This test exists before any guard code. A failure here changes the
 * schema. The fallback adds an explicit expires_at column, and that
 * returns a clock question to the design.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [NodeClaimTableProbeTests.ProbeApp::class]
)
@Import(CassandraTestContainerConfiguration::class)
@TestPropertySource(properties = ["app.key.type=long", "app.nodeid=200"])
@Tag("integration")
class NodeClaimTableProbeTests {

    @SpringBootApplication
    class ProbeApp

    @Autowired
    private lateinit var template: ReactiveCassandraTemplate

    private val cql get() = template.reactiveCqlOperations

    private fun claim(nodeId: Int, owner: String, ttlSeconds: Int): Boolean =
        cql.queryForRows(
            "INSERT INTO node_claim (node_id, owner_id) VALUES (?, ?) IF NOT EXISTS USING TTL ?",
            nodeId, owner, ttlSeconds
        ).next().map { it.getBoolean("[applied]") }.block()!!

    @Test
    fun `a second owner cannot take a live claim`() {
        Assertions.assertTrue(claim(201, "owner-one", 30))
        Assertions.assertFalse(claim(201, "owner-two", 30))
    }

    @Test
    fun `an expired claim is absent for IF NOT EXISTS`() {
        Assertions.assertTrue(claim(202, "owner-one", 1))
        Thread.sleep(Duration.ofSeconds(3).toMillis())
        Assertions.assertTrue(
            claim(202, "owner-two", 30),
            "A TTL expired row must be absent for IF NOT EXISTS. " +
                "If this fails, the design needs an explicit expires_at column."
        )
    }

    @Test
    fun `a deleted claim is absent for IF NOT EXISTS`() {
        Assertions.assertTrue(claim(203, "owner-one", 30))
        cql.execute("DELETE FROM node_claim WHERE node_id = ?", 203).block()
        Assertions.assertTrue(claim(203, "owner-two", 30))
    }
}
```

- [ ] **Step 4: Run the probe**

Run: `mvn -o -pl chat-persistence-cassandra -am -Pintegration test -Dtest=NodeClaimTableProbeTests`

Expected: PASS, all three tests. A Docker daemon must be running.

If `an expired claim is absent for IF NOT EXISTS` fails, STOP. Report the failure and the exact assertion text. Do not continue to Task 2.

- [ ] **Step 5: Commit**

```bash
git add shared-resources-cassandra/src/main/resources/keyspace-long.cql \
        shared-resources-cassandra/src/main/resources/keyspace-uuid.cql \
        chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/NodeClaimTableProbeTests.kt
git commit -m "add the node_claim table and prove its expiry behaviour"
```

---

### Task 2: The core contract, the message, and the properties

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/ClaimResult.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimStore.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimException.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimProperties.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimPropertiesTests.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimExceptionTests.kt`

**Interfaces:**
- Consumes: `com.demo.chat.domain.NodeId` from stage 1.
- Produces:
  - `sealed class ClaimResult` with `Granted`, `Denied(holder: String)`, `Lost`.
  - `interface NodeIdClaimStore` with `backendName: String`, `scope: String`, `claim/renew(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult>`, `release(nodeId: NodeId, owner: String): Mono<Void>`.
  - `class NodeIdClaimException(nodeId: NodeId, scope: String, holder: String, ttl: Duration)`.
  - `class NodeIdClaimProperties(ttl, renewInterval, safetyMargin, operationTimeout)` with `closeDeadline: Duration`.

- [ ] **Step 1: Write the failing property tests**

Create `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimPropertiesTests.kt`.

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.NodeIdClaimProperties
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration

class NodeIdClaimPropertiesTests {

    private fun props(
        ttl: Duration? = null,
        renew: Duration? = null,
        margin: Duration? = null,
        timeout: Duration? = null
    ) = NodeIdClaimProperties(ttl, renew, margin, timeout)

    private fun failure(block: () -> Unit): String =
        Assertions.assertThrows(IllegalArgumentException::class.java) { block() }.message!!

    @Test
    fun `defaults are thirty ten five and five`() {
        val p = props()
        Assertions.assertEquals(Duration.ofSeconds(30), p.ttl)
        Assertions.assertEquals(Duration.ofSeconds(10), p.renewInterval)
        Assertions.assertEquals(Duration.ofSeconds(5), p.safetyMargin)
        Assertions.assertEquals(Duration.ofSeconds(5), p.operationTimeout)
    }

    @Test
    fun `the default close deadline is twenty five seconds`() {
        Assertions.assertEquals(Duration.ofSeconds(25), props().closeDeadline)
    }

    @Test
    fun `a ttl under one second fails and states the rule`() {
        val text = failure { props(ttl = Duration.ofMillis(500)) }
        Assertions.assertTrue(text.contains("app.nodeid.claim.ttl"), text)
        Assertions.assertTrue(text.contains("at least 1s"), text)
    }

    @Test
    fun `a fractional ttl fails and states the rule`() {
        val text = failure { props(ttl = Duration.ofMillis(1500)) }
        Assertions.assertTrue(text.contains("whole seconds"), text)
    }

    @Test
    fun `a renew interval over one third of the ttl fails and states the rule`() {
        val text = failure { props(ttl = Duration.ofSeconds(30), renew = Duration.ofSeconds(11)) }
        Assertions.assertTrue(text.contains("app.nodeid.claim.renew-interval"), text)
        Assertions.assertTrue(text.contains("ttl / 3"), text)
    }

    @Test
    fun `a renew interval of exactly one third of the ttl is accepted`() {
        Assertions.assertEquals(
            Duration.ofSeconds(10),
            props(ttl = Duration.ofSeconds(30), renew = Duration.ofSeconds(10)).renewInterval
        )
    }

    @Test
    fun `a safety margin at or over the ttl fails and states the rule`() {
        val text = failure { props(ttl = Duration.ofSeconds(30), margin = Duration.ofSeconds(30)) }
        Assertions.assertTrue(text.contains("app.nodeid.claim.safety-margin"), text)
    }

    @Test
    fun `an operation timeout equal to the renew interval fails and states the rule`() {
        val text = failure {
            props(renew = Duration.ofSeconds(10), timeout = Duration.ofSeconds(10))
        }
        Assertions.assertTrue(text.contains("app.nodeid.claim.operation-timeout"), text)
        Assertions.assertTrue(text.contains("less than"), text)
    }

    @Test
    fun `a close deadline at or under the renew interval fails and states the rule`() {
        val text = failure {
            props(
                ttl = Duration.ofSeconds(12),
                renew = Duration.ofSeconds(4),
                margin = Duration.ofSeconds(8),
                timeout = Duration.ofSeconds(1)
            )
        }
        Assertions.assertTrue(text.contains("greater than"), text)
        Assertions.assertTrue(text.contains("renew-interval"), text)
    }
}
```

- [ ] **Step 2: Write the failing message test**

Create `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimExceptionTests.kt`.

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration

class NodeIdClaimExceptionTests {

    @Test
    fun `the redis message names the property the scope the holder and the wait`() {
        val text = NodeIdClaimException(
            NodeId(7),
            "redis store for key type long",
            "core-service@host-a:4711#a3f19c2b",
            Duration.ofSeconds(30)
        ).message!!

        Assertions.assertTrue(text.contains("app.nodeid=7 is already claimed"), text)
        Assertions.assertTrue(text.contains("the redis store for key type long"), text)
        Assertions.assertTrue(text.contains("Holder: core-service@host-a:4711#a3f19c2b"), text)
        Assertions.assertTrue(text.contains("wait 30s"), text)
    }

    @Test
    fun `the cassandra message names the keyspace`() {
        val text = NodeIdClaimException(
            NodeId(7),
            "cassandra keyspace chat_long",
            "core-service@host-b:5122#77c0aa41",
            Duration.ofSeconds(30)
        ).message!!

        Assertions.assertTrue(text.contains("the cassandra keyspace chat_long"), text)
        Assertions.assertFalse(text.contains("one store"), text)
    }
}
```

- [ ] **Step 3: Run both tests to verify they fail**

Run: `mvn -o -pl chat-core test -Dtest='NodeIdClaimPropertiesTests+NodeIdClaimExceptionTests'`

Expected: FAIL to compile, with unresolved references to `NodeIdClaimProperties` and `NodeIdClaimException`.

- [ ] **Step 4: Write the contract types**

Create `chat-core/src/main/kotlin/com/demo/chat/domain/ClaimResult.kt`.

```kotlin
package com.demo.chat.domain

/**
 * The outcome of a claim, a renew, or a release.
 *
 * [Denied] means one thing only. The store answered, and it named another
 * owner. [Lost] means the store answered and found no live claim. Every
 * infrastructure failure is an error, not a result.
 */
sealed class ClaimResult {
    object Granted : ClaimResult()
    data class Denied(val holder: String) : ClaimResult()
    object Lost : ClaimResult()
}
```

Create `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimStore.kt`.

```kotlin
package com.demo.chat.domain

import reactor.core.publisher.Mono
import java.time.Duration

/**
 * A store-side lease on one [NodeId].
 *
 * A registry check cannot enforce node id uniqueness. One store can be
 * reached by deployments that do not share a registry. The claim therefore
 * lives in the store that the deployments share.
 *
 * Implementations return a [Mono]. The guard is the only place that blocks.
 */
interface NodeIdClaimStore {

    /** `redis` or `cassandra`. This orders the stores in the guard. */
    val backendName: String

    /**
     * The full phrase that names the space this claim covers.
     *
     * Redis supplies `redis store for key type long`. Cassandra supplies
     * `cassandra keyspace chat_long`. The phrase is complete, so that one
     * message template serves every backend.
     */
    val scope: String

    /** Takes the lease. Returns [ClaimResult.Granted] or [ClaimResult.Denied]. Never [ClaimResult.Lost]. */
    fun claim(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult>

    /** Extends the lease when this owner still holds it. */
    fun renew(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult>

    /** Drops the lease when this owner holds it. Best effort. */
    fun release(nodeId: NodeId, owner: String): Mono<Void>
}
```

Create `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimException.kt`.

```kotlin
package com.demo.chat.domain

import java.time.Duration

/**
 * Reports a duplicate node id.
 *
 * The message states the scope, because uniqueness is per key type per
 * store. A message that said "one store" would be false for a redis long
 * deployment beside a redis uuid deployment.
 */
class NodeIdClaimException(
    val nodeId: NodeId,
    val scope: String,
    val holder: String,
    val ttl: Duration
) : RuntimeException(message(nodeId, scope, holder, ttl)) {

    companion object {
        fun message(nodeId: NodeId, scope: String, holder: String, ttl: Duration): String =
            "app.nodeid=${nodeId.value} is already claimed in the $scope.\n" +
                "Holder: $holder\n" +
                "Two deployments that write to the $scope must not use the same app.nodeid.\n" +
                "Set a different app.nodeid, or stop the other deployment and wait " +
                "${ttl.seconds}s\nfor its lease to expire."
    }
}
```

- [ ] **Step 5: Write the properties**

Create `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimProperties.kt`.

```kotlin
package com.demo.chat.domain

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * The lease timings.
 *
 * The constructor takes nullable parameters and applies the defaults in the
 * body. It declares no Kotlin default arguments on purpose. A default
 * argument makes Kotlin emit a synthetic constructor, and Spring Boot then
 * has more than one constructor to choose from. The same trap already bit
 * `ConfigurationPropertiesRedisTopics`.
 */
@ConfigurationProperties(prefix = "app.nodeid.claim")
class NodeIdClaimProperties(
    ttl: Duration?,
    renewInterval: Duration?,
    safetyMargin: Duration?,
    operationTimeout: Duration?
) {
    val ttl: Duration = ttl ?: Duration.ofSeconds(30)
    val renewInterval: Duration = renewInterval ?: Duration.ofSeconds(10)
    val safetyMargin: Duration = safetyMargin ?: Duration.ofSeconds(5)
    val operationTimeout: Duration = operationTimeout ?: Duration.ofSeconds(5)

    /** The process closes this long after the last successful claim or renew. */
    val closeDeadline: Duration = this.ttl.minus(this.safetyMargin)

    init {
        require(this.ttl >= Duration.ofSeconds(1)) {
            "app.nodeid.claim.ttl must be at least 1s. Got: ${this.ttl}"
        }
        // Cassandra applies a TTL in whole seconds. A fractional value would
        // silently round on one backend and not on the other.
        require(this.ttl.nano == 0) {
            "app.nodeid.claim.ttl must use whole seconds. Got: ${this.ttl}"
        }
        require(!this.renewInterval.isZero && !this.renewInterval.isNegative) {
            "app.nodeid.claim.renew-interval must be greater than zero. Got: ${this.renewInterval}"
        }
        require(this.renewInterval <= this.ttl.dividedBy(3)) {
            "app.nodeid.claim.renew-interval must be at most ttl / 3. " +
                "ttl is ${this.ttl}. Got: ${this.renewInterval}"
        }
        require(!this.safetyMargin.isZero && !this.safetyMargin.isNegative) {
            "app.nodeid.claim.safety-margin must be greater than zero. Got: ${this.safetyMargin}"
        }
        require(this.safetyMargin < this.ttl) {
            "app.nodeid.claim.safety-margin must be less than ttl. " +
                "ttl is ${this.ttl}. Got: ${this.safetyMargin}"
        }
        // A blocked call must not consume the next renewal slot.
        require(this.operationTimeout < this.renewInterval) {
            "app.nodeid.claim.operation-timeout must be less than renew-interval. " +
                "renew-interval is ${this.renewInterval}. Got: ${this.operationTimeout}"
        }
        require(this.closeDeadline > this.renewInterval) {
            "ttl minus app.nodeid.claim.safety-margin must be greater than renew-interval. " +
                "The deadline is ${this.closeDeadline}. renew-interval is ${this.renewInterval}."
        }
    }
}
```

- [ ] **Step 6: Run both tests to verify they pass**

Run: `mvn -o -pl chat-core test -Dtest='NodeIdClaimPropertiesTests+NodeIdClaimExceptionTests'`

Expected: PASS, 11 tests.

- [ ] **Step 7: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/ClaimResult.kt \
        chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimStore.kt \
        chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimException.kt \
        chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimProperties.kt \
        chat-core/src/test/kotlin/com/demo/chat/test/domain/
git commit -m "add the node id claim contract, message, and lease properties"
```

---

### Task 3: The scheduling seam and the owner id

The guard needs deterministic tests. A real `ScheduledExecutorService` would make every timing test wait in real time. This task adds a small seam instead.

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/ClaimScheduler.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/RuntimeOwnerId.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/domain/RuntimeOwnerIdTests.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/domain/ExecutorClaimSchedulerTests.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `interface ClaimScheduler` with `schedulePeriodic(period: Duration, task: () -> Unit): AutoCloseable`, `scheduleOnce(delay: Duration, task: () -> Unit): AutoCloseable`, `runDetached(name: String, task: () -> Unit)`, `shutdownNow()`, `isSchedulerThread(): Boolean`.
  - `class ExecutorClaimScheduler : ClaimScheduler`.
  - `class RuntimeOwnerId(val value: String)` with `companion object { fun generate(applicationName: String): RuntimeOwnerId }`.

- [ ] **Step 1: Write the failing owner id test**

Create `chat-core/src/test/kotlin/com/demo/chat/test/domain/RuntimeOwnerIdTests.kt`.

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.RuntimeOwnerId
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RuntimeOwnerIdTests {

    @Test
    fun `the value names the application the host and the process`() {
        val owner = RuntimeOwnerId.generate("core-service").value
        Assertions.assertTrue(owner.startsWith("core-service@"), owner)
        Assertions.assertTrue(owner.contains(":"), owner)
        Assertions.assertTrue(owner.contains("#"), owner)
    }

    @Test
    fun `two values generated in one process differ`() {
        Assertions.assertNotEquals(
            RuntimeOwnerId.generate("core-service").value,
            RuntimeOwnerId.generate("core-service").value
        )
    }

    @Test
    fun `the random suffix is eight hex characters`() {
        val suffix = RuntimeOwnerId.generate("core-service").value.substringAfterLast("#")
        Assertions.assertTrue(suffix.matches(Regex("[0-9a-f]{8}")), suffix)
    }
}
```

- [ ] **Step 2: Write the failing scheduler test**

Create `chat-core/src/test/kotlin/com/demo/chat/test/domain/ExecutorClaimSchedulerTests.kt`.

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.ExecutorClaimScheduler
import org.awaitility.Awaitility
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

class ExecutorClaimSchedulerTests {

    @Test
    fun `a periodic task reports that it runs on a scheduler thread`() {
        val scheduler = ExecutorClaimScheduler()
        val onScheduler = AtomicReference<Boolean>()
        try {
            scheduler.schedulePeriodic(Duration.ofMillis(20)) {
                onScheduler.compareAndSet(null, scheduler.isSchedulerThread())
            }
            Awaitility.await().atMost(Duration.ofSeconds(2)).until { onScheduler.get() != null }
            Assertions.assertTrue(onScheduler.get())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `a detached task does not run on a scheduler thread`() {
        val scheduler = ExecutorClaimScheduler()
        val onScheduler = AtomicReference<Boolean>()
        try {
            scheduler.runDetached("nodeid-claim-close") {
                onScheduler.set(scheduler.isSchedulerThread())
            }
            Awaitility.await().atMost(Duration.ofSeconds(2)).until { onScheduler.get() != null }
            Assertions.assertFalse(onScheduler.get())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `shutdown from a scheduler thread returns and does not wait for itself`() {
        val scheduler = ExecutorClaimScheduler()
        val done = AtomicReference<Boolean>()
        scheduler.schedulePeriodic(Duration.ofMillis(20)) {
            scheduler.shutdownNow()
            done.compareAndSet(null, true)
        }
        Awaitility.await().atMost(Duration.ofSeconds(2)).until { done.get() == true }
    }

    @Test
    fun `a closed periodic handle stops the task`() {
        val scheduler = ExecutorClaimScheduler()
        try {
            var runs = 0
            val handle = scheduler.schedulePeriodic(Duration.ofMillis(20)) { runs++ }
            Awaitility.await().atMost(Duration.ofSeconds(2)).until { runs > 0 }
            handle.close()
            val seen = runs
            Thread.sleep(200)
            Assertions.assertEquals(seen, runs)
        } finally {
            scheduler.shutdownNow()
        }
    }
}
```

- [ ] **Step 3: Run both tests to verify they fail**

Run: `mvn -o -pl chat-core test -Dtest='RuntimeOwnerIdTests+ExecutorClaimSchedulerTests'`

Expected: FAIL to compile, with unresolved references to `RuntimeOwnerId` and `ExecutorClaimScheduler`.

- [ ] **Step 4: Write the owner id**

Create `chat-core/src/main/kotlin/com/demo/chat/domain/RuntimeOwnerId.kt`.

```kotlin
package com.demo.chat.domain

import java.net.InetAddress
import java.security.SecureRandom

/**
 * The owner of a node id lease, for one process.
 *
 * The value is unique per process. A restart never reuses it, so a restarted
 * deployment cannot mistake its own stale lease for a live one. The value is
 * readable, so the duplicate node message names a real host and process.
 */
class RuntimeOwnerId(val value: String) {

    override fun toString(): String = value

    companion object {
        private val random = SecureRandom()

        fun generate(applicationName: String): RuntimeOwnerId {
            val host = try {
                InetAddress.getLocalHost().hostName
            } catch (e: Exception) {
                "unknown-host"
            }
            val pid = ProcessHandle.current().pid()
            val suffix = String.format("%08x", random.nextInt())
            return RuntimeOwnerId("$applicationName@$host:$pid#$suffix")
        }
    }
}
```

- [ ] **Step 5: Write the scheduler**

Create `chat-core/src/main/kotlin/com/demo/chat/domain/ClaimScheduler.kt`.

```kotlin
package com.demo.chat.domain

import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * The scheduling seam for [NodeIdClaimGuard].
 *
 * The guard owns its schedule. A shared task scheduler can delay a renew
 * behind unrelated work, and a late renew can cost the lease.
 *
 * A test supplies its own implementation and fires tasks by hand, so the
 * timing tests need no real waiting.
 */
interface ClaimScheduler {

    fun schedulePeriodic(period: Duration, task: () -> Unit): AutoCloseable

    fun scheduleOnce(delay: Duration, task: () -> Unit): AutoCloseable

    /**
     * Runs a task off every scheduler thread.
     *
     * The guard closes the context this way. A close started on a scheduler
     * thread would run `destroy` on that same thread, and a `destroy` that
     * waited for the scheduler would wait for itself.
     */
    fun runDetached(name: String, task: () -> Unit)

    fun shutdownNow()

    fun isSchedulerThread(): Boolean
}

class ExecutorClaimScheduler : ClaimScheduler {

    private val threadName = "nodeid-claim-renew"

    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor(ThreadFactory { runnable ->
            Thread(runnable, threadName).apply { isDaemon = true }
        })

    override fun schedulePeriodic(period: Duration, task: () -> Unit): AutoCloseable {
        val future = executor.scheduleAtFixedRate(
            { runQuietly(task) }, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS
        )
        return AutoCloseable { future.cancel(false) }
    }

    override fun scheduleOnce(delay: Duration, task: () -> Unit): AutoCloseable {
        val future = executor.schedule(
            { runQuietly(task) }, delay.toMillis(), TimeUnit.MILLISECONDS
        )
        return AutoCloseable { future.cancel(false) }
    }

    override fun runDetached(name: String, task: () -> Unit) {
        Thread({ runQuietly(task) }, name).apply { isDaemon = false }.start()
    }

    override fun shutdownNow() {
        executor.shutdownNow()
        // Never wait from a scheduler thread. That would wait for this task.
        if (!isSchedulerThread()) {
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    override fun isSchedulerThread(): Boolean = Thread.currentThread().name == threadName

    // A thrown task would silently cancel a fixed rate schedule. The guard
    // handles its own failures, so anything reaching here is a defect.
    private fun runQuietly(task: () -> Unit) {
        try {
            task()
        } catch (e: Throwable) {
            System.err.println("nodeid claim scheduler task failed: ${e.message}")
        }
    }
}
```

- [ ] **Step 6: Run both tests to verify they pass**

Run: `mvn -o -pl chat-core test -Dtest='RuntimeOwnerIdTests+ExecutorClaimSchedulerTests'`

Expected: PASS, 7 tests.

- [ ] **Step 7: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/ClaimScheduler.kt \
        chat-core/src/main/kotlin/com/demo/chat/domain/RuntimeOwnerId.kt \
        chat-core/src/test/kotlin/com/demo/chat/test/domain/RuntimeOwnerIdTests.kt \
        chat-core/src/test/kotlin/com/demo/chat/test/domain/ExecutorClaimSchedulerTests.kt
git commit -m "add the claim scheduler seam and the runtime owner id"
```

---

### Task 4: The guard, startup path

This task covers the claim, the ordering, and the release on failure. The renewal path is Task 5.

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimGuard.kt`
- Create: `chat-core/src/test/kotlin/com/demo/chat/test/domain/ClaimTestDoubles.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardStartupTests.kt`

**Interfaces:**
- Consumes: `ClaimResult`, `NodeIdClaimStore`, `NodeIdClaimException`, `NodeIdClaimProperties`, `ClaimScheduler`, `RuntimeOwnerId`, `NodeId`.
- Produces:
  - `class NodeIdClaimGuard(stores: List<NodeIdClaimStore>, nodeId: NodeId, owner: RuntimeOwnerId, props: NodeIdClaimProperties, context: ConfigurableApplicationContext, scheduler: ClaimScheduler) : InitializingBean, DisposableBean`.
  - Test doubles `FakeClaimStore` and `ManualClaimScheduler` used by Task 5 as well.

- [ ] **Step 1: Write the test doubles**

Create `chat-core/src/test/kotlin/com/demo/chat/test/domain/ClaimTestDoubles.kt`.

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.ClaimScheduler
import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimStore
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * A store whose answers the test sets.
 *
 * `calls` records every operation in order, so a test can assert the claim
 * order and the release order.
 */
class FakeClaimStore(
    override val backendName: String,
    override val scope: String = "$backendName store for key type long",
    var claimAnswer: () -> ClaimResult = { ClaimResult.Granted },
    var renewAnswer: () -> ClaimResult = { ClaimResult.Granted }
) : NodeIdClaimStore {

    val calls = mutableListOf<String>()

    override fun claim(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult> =
        Mono.fromCallable { calls.add("claim:$backendName"); claimAnswer() }

    override fun renew(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult> =
        Mono.fromCallable { calls.add("renew:$backendName"); renewAnswer() }

    override fun release(nodeId: NodeId, owner: String): Mono<Void> =
        Mono.fromRunnable { calls.add("release:$backendName") }
}

/**
 * A scheduler the test drives by hand.
 *
 * Nothing runs until the test calls [firePeriodic] or [fireOnce]. The guard
 * timing tests therefore need no real waiting.
 */
class ManualClaimScheduler : ClaimScheduler {

    var periodic: (() -> Unit)? = null
    var once: (() -> Unit)? = null
    var onceDelay: Duration? = null
    val detached = mutableListOf<String>()
    var shutdownCount = 0
    var pretendSchedulerThread = false

    override fun schedulePeriodic(period: Duration, task: () -> Unit): AutoCloseable {
        periodic = task
        return AutoCloseable { periodic = null }
    }

    override fun scheduleOnce(delay: Duration, task: () -> Unit): AutoCloseable {
        once = task
        onceDelay = delay
        return AutoCloseable { once = null; onceDelay = null }
    }

    override fun runDetached(name: String, task: () -> Unit) {
        detached.add(name)
        task()
    }

    override fun shutdownNow() {
        shutdownCount++
    }

    override fun isSchedulerThread(): Boolean = pretendSchedulerThread

    fun firePeriodic() = periodic?.invoke()

    fun fireOnce() = once?.invoke()
}
```

- [ ] **Step 2: Write the failing startup tests**

Create `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardStartupTests.kt`.

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimException
import com.demo.chat.domain.NodeIdClaimGuard
import com.demo.chat.domain.NodeIdClaimProperties
import com.demo.chat.domain.RuntimeOwnerId
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import java.time.Duration

class NodeIdClaimGuardStartupTests {

    private val props = NodeIdClaimProperties(null, null, null, null)

    private fun guard(
        stores: List<com.demo.chat.domain.NodeIdClaimStore>,
        scheduler: ManualClaimScheduler = ManualClaimScheduler()
    ) = NodeIdClaimGuard(
        stores, NodeId(7), RuntimeOwnerId("core-service@host-a:4711#a3f19c2b"),
        props, GenericApplicationContext(), scheduler
    )

    @Test
    fun `one granted store starts and schedules a renew`() {
        val store = FakeClaimStore("redis")
        val scheduler = ManualClaimScheduler()
        guard(listOf(store), scheduler).afterPropertiesSet()

        Assertions.assertEquals(listOf("claim:redis"), store.calls)
        Assertions.assertNotNull(scheduler.periodic)
    }

    @Test
    fun `two stores are claimed in backend name order`() {
        val redis = FakeClaimStore("redis")
        val cassandra = FakeClaimStore("cassandra")
        guard(listOf(redis, cassandra)).afterPropertiesSet()

        Assertions.assertEquals(listOf("claim:cassandra"), cassandra.calls)
        Assertions.assertEquals(listOf("claim:redis"), redis.calls)
    }

    @Test
    fun `a denial at the second store releases the first store`() {
        val cassandra = FakeClaimStore("cassandra")
        val redis = FakeClaimStore(
            "redis",
            claimAnswer = { ClaimResult.Denied("core-service@host-b:5122#77c0aa41") }
        )

        val thrown = Assertions.assertThrows(NodeIdClaimException::class.java) {
            guard(listOf(redis, cassandra)).afterPropertiesSet()
        }

        Assertions.assertEquals(listOf("claim:cassandra", "release:cassandra"), cassandra.calls)
        Assertions.assertTrue(thrown.message!!.contains("app.nodeid=7 is already claimed"))
        Assertions.assertTrue(thrown.message!!.contains("redis store for key type long"))
    }

    @Test
    fun `a store error at the second store releases the first store`() {
        val cassandra = FakeClaimStore("cassandra")
        val redis = FakeClaimStore("redis", claimAnswer = { throw IllegalStateException("redis is down") })

        Assertions.assertThrows(Exception::class.java) {
            guard(listOf(redis, cassandra)).afterPropertiesSet()
        }

        Assertions.assertEquals(listOf("claim:cassandra", "release:cassandra"), cassandra.calls)
    }

    @Test
    fun `a release failure during rollback does not hide the claim failure`() {
        val cassandra = object : com.demo.chat.domain.NodeIdClaimStore {
            override val backendName = "cassandra"
            override val scope = "cassandra keyspace chat_long"
            override fun claim(nodeId: NodeId, owner: String, ttl: Duration) =
                reactor.core.publisher.Mono.just(ClaimResult.Granted as ClaimResult)
            override fun renew(nodeId: NodeId, owner: String, ttl: Duration) =
                reactor.core.publisher.Mono.just(ClaimResult.Granted as ClaimResult)
            override fun release(nodeId: NodeId, owner: String) =
                reactor.core.publisher.Mono.error<Void>(IllegalStateException("release failed"))
        }
        val redis = FakeClaimStore("redis", claimAnswer = { ClaimResult.Denied("other") })

        val thrown = Assertions.assertThrows(NodeIdClaimException::class.java) {
            guard(listOf(redis, cassandra)).afterPropertiesSet()
        }
        Assertions.assertTrue(thrown.message!!.contains("app.nodeid=7"))
    }

    @Test
    fun `the deadline timer is armed after a successful claim`() {
        val scheduler = ManualClaimScheduler()
        guard(listOf(FakeClaimStore("redis")), scheduler).afterPropertiesSet()

        Assertions.assertNotNull(scheduler.once)
        Assertions.assertEquals(Duration.ofSeconds(25), scheduler.onceDelay)
    }

    @Test
    fun `no stores means no claim and no schedule`() {
        val scheduler = ManualClaimScheduler()
        guard(emptyList(), scheduler).afterPropertiesSet()

        Assertions.assertNull(scheduler.periodic)
        Assertions.assertNull(scheduler.once)
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `mvn -o -pl chat-core test -Dtest=NodeIdClaimGuardStartupTests`

Expected: FAIL to compile, with an unresolved reference to `NodeIdClaimGuard`.

- [ ] **Step 4: Write the guard**

Create `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimGuard.kt`. The renewal body is written now and exercised in Task 5.

```kotlin
package com.demo.chat.domain

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ConfigurableApplicationContext

/**
 * Holds the store-side lease on `app.nodeid` for the life of the process.
 *
 * The guard claims during context refresh. A duplicate therefore fails
 * startup before the application is ready and before the process serves
 * normal traffic.
 *
 * The guard is the only place that blocks. The stores stay reactive.
 */
class NodeIdClaimGuard(
    private val stores: List<NodeIdClaimStore>,
    private val nodeId: NodeId,
    private val owner: RuntimeOwnerId,
    private val props: NodeIdClaimProperties,
    private val context: ConfigurableApplicationContext,
    private val scheduler: ClaimScheduler
) : InitializingBean, DisposableBean {

    private val log = LoggerFactory.getLogger(NodeIdClaimGuard::class.java)

    private var granted: List<NodeIdClaimStore> = emptyList()
    private var renewHandle: AutoCloseable? = null
    private var deadlineHandle: AutoCloseable? = null

    override fun afterPropertiesSet() {
        if (stores.isEmpty()) {
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
            releaseAll(taken.reversed())
            throw failure
        }

        granted = taken
        log.info("app.nodeid={} claimed in {}", nodeId.value, taken.joinToString { it.scope })
        armDeadline()
        renewHandle = scheduler.schedulePeriodic(props.renewInterval) { renewOnce() }
    }

    override fun destroy() {
        renewHandle?.close()
        deadlineHandle?.close()
        scheduler.shutdownNow()
        releaseAll(granted.reversed())
        granted = emptyList()
    }

    private fun armDeadline() {
        deadlineHandle?.close()
        deadlineHandle = scheduler.scheduleOnce(props.closeDeadline) {
            closeContext(
                "app.nodeid=${nodeId.value} was not renewed within ${props.closeDeadline.seconds}s. " +
                    "The lease may have expired. Closing the application context."
            )
        }
    }

    private fun renewOnce() {
        granted.forEach { store ->
            val result = try {
                store.renew(nodeId, owner.value, props.ttl).timeout(props.operationTimeout).block()
            } catch (error: Throwable) {
                log.warn(
                    "app.nodeid={} renew failed on {}. Retrying at the next interval. Cause: {}",
                    nodeId.value, store.backendName, error.message
                )
                return
            }

            when (result) {
                is ClaimResult.Granted -> Unit
                is ClaimResult.Denied -> {
                    closeContext(NodeIdClaimException(nodeId, store.scope, result.holder, props.ttl).message!!)
                    return
                }
                is ClaimResult.Lost -> {
                    closeContext(
                        "app.nodeid=${nodeId.value} is no longer held in the ${store.scope}. " +
                            "This process lost its lease. Closing the application context."
                    )
                    return
                }
                null -> {
                    log.warn(
                        "app.nodeid={} renew returned no result from {}. Retrying at the next interval.",
                        nodeId.value, store.backendName
                    )
                    return
                }
            }
        }
        armDeadline()
    }

    // The close runs off the scheduler. A close started on a scheduler thread
    // would run destroy on that same thread, and destroy would wait for it.
    private fun closeContext(reason: String) {
        log.error(reason)
        scheduler.runDetached("nodeid-claim-close") { context.close() }
    }

    private fun releaseAll(ordered: List<NodeIdClaimStore>) {
        ordered.forEach { store ->
            try {
                store.release(nodeId, owner.value).timeout(props.operationTimeout).block()
            } catch (error: Throwable) {
                log.debug(
                    "app.nodeid={} release failed on {}. The lease expires on its own. Cause: {}",
                    nodeId.value, store.backendName, error.message
                )
            }
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -o -pl chat-core test -Dtest=NodeIdClaimGuardStartupTests`

Expected: PASS, 7 tests.

- [ ] **Step 6: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimGuard.kt \
        chat-core/src/test/kotlin/com/demo/chat/test/domain/ClaimTestDoubles.kt \
        chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardStartupTests.kt
git commit -m "claim the node id lease during context refresh"
```

---

### Task 5: The guard, renewal and shutdown

**Files:**
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardRenewalTests.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimGuard.kt` only if a test fails

**Interfaces:**
- Consumes: everything from Task 4.
- Produces: no new type. This task proves the renewal contract.

- [ ] **Step 1: Write the failing renewal tests**

Create `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardRenewalTests.kt`.

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimGuard
import com.demo.chat.domain.NodeIdClaimProperties
import com.demo.chat.domain.RuntimeOwnerId
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import java.time.Duration

class NodeIdClaimGuardRenewalTests {

    private val props = NodeIdClaimProperties(null, null, null, null)

    private fun setUp(store: FakeClaimStore): Pair<NodeIdClaimGuard, ManualClaimScheduler> {
        val scheduler = ManualClaimScheduler()
        val context = GenericApplicationContext()
        context.refresh()
        val guard = NodeIdClaimGuard(
            listOf(store), NodeId(7), RuntimeOwnerId("core-service@host-a:4711#a3f19c2b"),
            props, context, scheduler
        )
        guard.afterPropertiesSet()
        return guard to scheduler
    }

    @Test
    fun `a granted renew keeps the context open and rearms the deadline`() {
        val store = FakeClaimStore("redis")
        val (_, scheduler) = setUp(store)

        scheduler.firePeriodic()

        Assertions.assertTrue(store.calls.contains("renew:redis"))
        Assertions.assertTrue(scheduler.detached.isEmpty())
        Assertions.assertEquals(Duration.ofSeconds(25), scheduler.onceDelay)
    }

    @Test
    fun `a denied renew closes the context at once`() {
        val store = FakeClaimStore("redis", renewAnswer = { ClaimResult.Denied("other-owner") })
        val (_, scheduler) = setUp(store)

        scheduler.firePeriodic()

        Assertions.assertEquals(listOf("nodeid-claim-close"), scheduler.detached)
    }

    @Test
    fun `a lost renew closes the context at once`() {
        val store = FakeClaimStore("redis", renewAnswer = { ClaimResult.Lost })
        val (_, scheduler) = setUp(store)

        scheduler.firePeriodic()

        Assertions.assertEquals(listOf("nodeid-claim-close"), scheduler.detached)
    }

    @Test
    fun `a renew error keeps the context open`() {
        val store = FakeClaimStore("redis", renewAnswer = { throw IllegalStateException("redis is down") })
        val (_, scheduler) = setUp(store)

        scheduler.firePeriodic()
        scheduler.firePeriodic()

        Assertions.assertTrue(scheduler.detached.isEmpty())
    }

    @Test
    fun `a renew error does not rearm the deadline`() {
        val store = FakeClaimStore("redis")
        val (_, scheduler) = setUp(store)

        scheduler.firePeriodic()
        val armedAfterSuccess = scheduler.once
        store.renewAnswer = { throw IllegalStateException("redis is down") }
        scheduler.firePeriodic()

        Assertions.assertSame(armedAfterSuccess, scheduler.once)
    }

    @Test
    fun `the deadline timer closes the context when it fires`() {
        val store = FakeClaimStore("redis", renewAnswer = { throw IllegalStateException("redis is down") })
        val (_, scheduler) = setUp(store)

        scheduler.firePeriodic()
        scheduler.fireOnce()

        Assertions.assertEquals(listOf("nodeid-claim-close"), scheduler.detached)
    }

    @Test
    fun `the close runs off the scheduler thread`() {
        val store = FakeClaimStore("redis", renewAnswer = { ClaimResult.Lost })
        val (_, scheduler) = setUp(store)
        scheduler.pretendSchedulerThread = true

        scheduler.firePeriodic()

        Assertions.assertEquals(listOf("nodeid-claim-close"), scheduler.detached)
    }

    @Test
    fun `destroy shuts the scheduler down and releases the store`() {
        val store = FakeClaimStore("redis")
        val (guard, scheduler) = setUp(store)

        guard.destroy()

        Assertions.assertEquals(1, scheduler.shutdownCount)
        Assertions.assertTrue(store.calls.contains("release:redis"))
    }

    @Test
    fun `destroy releases two stores in reverse backend name order`() {
        val redis = FakeClaimStore("redis")
        val cassandra = FakeClaimStore("cassandra")
        val scheduler = ManualClaimScheduler()
        val context = GenericApplicationContext()
        context.refresh()
        val guard = NodeIdClaimGuard(
            listOf(redis, cassandra), NodeId(7),
            RuntimeOwnerId("core-service@host-a:4711#a3f19c2b"), props, context, scheduler
        )
        guard.afterPropertiesSet()

        guard.destroy()

        Assertions.assertTrue(redis.calls.contains("release:redis"))
        Assertions.assertTrue(cassandra.calls.contains("release:cassandra"))
    }

    @Test
    fun `destroy twice releases once`() {
        val store = FakeClaimStore("redis")
        val (guard, _) = setUp(store)

        guard.destroy()
        guard.destroy()

        Assertions.assertEquals(1, store.calls.count { it == "release:redis" })
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `mvn -o -pl chat-core test -Dtest=NodeIdClaimGuardRenewalTests`

Expected: PASS, 10 tests. The guard body from Task 4 already implements this contract.

If a test fails, fix `NodeIdClaimGuard.kt` and run again. Do not change a test to match the code. The tests state the spec.

- [ ] **Step 3: Commit**

```bash
git add chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdClaimGuardRenewalTests.kt \
        chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimGuard.kt
git commit -m "pin the renewal, deadline, and shutdown rules of the claim guard"
```

---

### Task 6: The activation seam

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/ConditionalOnSharedBackend.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimGuardConfiguration.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/domain/SharedBackendConditionTests.kt`

**Interfaces:**
- Consumes: `NodeIdClaimGuard`, `NodeIdClaimProperties`, `ClaimScheduler`, `RuntimeOwnerId`, `NodeIdConfiguration`.
- Produces:
  - `annotation class ConditionalOnSharedBackend(val value: String)`.
  - `class NodeIdClaimGuardConfiguration` supplying `runtimeOwnerId`, `nodeIdClaimScheduler`, and `nodeIdClaimGuard`.

- [ ] **Step 1: Write the failing condition tests**

Create `chat-core/src/test/kotlin/com/demo/chat/test/domain/SharedBackendConditionTests.kt`.

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.ConditionalOnSharedBackend
import com.demo.chat.domain.NodeIdClaimGuard
import com.demo.chat.domain.NodeIdClaimGuardConfiguration
import com.demo.chat.domain.NodeIdClaimStore
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.mock.env.MockEnvironment

class SharedBackendConditionTests {

    @Configuration
    @ConditionalOnSharedBackend("redis")
    @Import(NodeIdClaimGuardConfiguration::class)
    open class RedisClaimConfiguration {
        @Bean
        open fun redisClaimStore(): NodeIdClaimStore = FakeClaimStore("redis")
    }

    private fun contextWith(vararg properties: Pair<String, String>): AnnotationConfigApplicationContext {
        val context = AnnotationConfigApplicationContext()
        val environment = MockEnvironment()
        properties.forEach { environment.setProperty(it.first, it.second) }
        context.environment = environment
        context.register(RedisClaimConfiguration::class.java)
        context.refresh()
        return context
    }

    @Test
    fun `the key selector activates the store`() {
        contextWith("app.service.core.key" to "redis", "app.nodeid" to "7").use { context ->
            Assertions.assertEquals(1, context.getBeansOfType(NodeIdClaimStore::class.java).size)
            Assertions.assertEquals(1, context.getBeansOfType(NodeIdClaimGuard::class.java).size)
        }
    }

    @Test
    fun `the persistence selector activates the store`() {
        contextWith("app.service.core.persistence" to "redis", "app.nodeid" to "7").use { context ->
            Assertions.assertEquals(1, context.getBeansOfType(NodeIdClaimStore::class.java).size)
        }
    }

    @Test
    fun `a memory pair activates nothing`() {
        contextWith(
            "app.service.core.key" to "memory",
            "app.service.core.persistence" to "memory"
        ).use { context ->
            Assertions.assertTrue(context.getBeansOfType(NodeIdClaimStore::class.java).isEmpty())
            Assertions.assertTrue(context.getBeansOfType(NodeIdClaimGuard::class.java).isEmpty())
        }
    }

    @Test
    fun `an unset pair activates nothing and needs no app nodeid`() {
        contextWith().use { context ->
            Assertions.assertTrue(context.getBeansOfType(NodeIdClaimStore::class.java).isEmpty())
            Assertions.assertTrue(context.getBeansOfType(com.demo.chat.domain.NodeId::class.java).isEmpty())
        }
    }

    @Test
    fun `a cassandra pair does not activate the redis store`() {
        contextWith(
            "app.service.core.key" to "cassandra",
            "app.service.core.persistence" to "cassandra"
        ).use { context ->
            Assertions.assertTrue(context.getBeansOfType(NodeIdClaimStore::class.java).isEmpty())
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -o -pl chat-core test -Dtest=SharedBackendConditionTests`

Expected: FAIL to compile, with unresolved references to `ConditionalOnSharedBackend` and `NodeIdClaimGuardConfiguration`.

- [ ] **Step 3: Write the condition**

Create `chat-core/src/main/kotlin/com/demo/chat/domain/ConditionalOnSharedBackend.kt`.

```kotlin
package com.demo.chat.domain

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * Activates a configuration when either core selector names this backend.
 *
 * Generated ids reach the key store and the persistence store. A condition
 * on one selector alone would leave `key=memory` with `persistence=redis`
 * unprotected, and the configuration permits that pair.
 *
 * `@ConditionalOnProperty` cannot express an OR across two properties.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Conditional(OnSharedBackendCondition::class)
annotation class ConditionalOnSharedBackend(val value: String)

class OnSharedBackendCondition : Condition {

    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        val backend = metadata
            .getAnnotationAttributes(ConditionalOnSharedBackend::class.java.name)
            ?.get("value") as? String
            ?: return false

        val environment = context.environment
        return environment.getProperty("app.service.core.key") == backend ||
            environment.getProperty("app.service.core.persistence") == backend
    }
}
```

- [ ] **Step 4: Write the guard configuration**

Create `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimGuardConfiguration.kt`.

```kotlin
package com.demo.chat.domain

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment

/**
 * Supplies the guard beans.
 *
 * Every claim store configuration imports this class. An import is
 * idempotent, so a classpath that names two shared backends still gets one
 * guard. This mirrors how `KeyGenConfiguration` imports
 * [NodeIdConfiguration].
 *
 * This class lives in `com.demo.chat.domain` on purpose.
 * `com.demo.chat.config` is component-scanned by every deployment, so a copy
 * placed there would supply the guard in processes that claim nothing.
 */
@Configuration
@EnableConfigurationProperties(NodeIdClaimProperties::class)
@Import(NodeIdConfiguration::class)
open class NodeIdClaimGuardConfiguration {

    @Bean
    open fun runtimeOwnerId(environment: Environment): RuntimeOwnerId =
        RuntimeOwnerId.generate(environment.getProperty("spring.application.name") ?: "chat")

    @Bean
    open fun nodeIdClaimScheduler(): ClaimScheduler = ExecutorClaimScheduler()

    // ObjectProvider, not List. A List parameter with no candidate bean fails
    // to resolve, and this configuration must also be safe to import early.
    @Bean
    open fun nodeIdClaimGuard(
        stores: ObjectProvider<NodeIdClaimStore>,
        nodeId: NodeId,
        owner: RuntimeOwnerId,
        properties: NodeIdClaimProperties,
        context: ConfigurableApplicationContext,
        scheduler: ClaimScheduler
    ): NodeIdClaimGuard =
        NodeIdClaimGuard(
            stores.orderedStream().toList(), nodeId, owner, properties, context, scheduler
        )
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -o -pl chat-core test -Dtest=SharedBackendConditionTests`

Expected: PASS, 5 tests.

- [ ] **Step 6: Run the whole core module**

Run: `mvn -o -pl chat-core test`

Expected: PASS. No existing `chat-core` test changes behaviour.

- [ ] **Step 7: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/ConditionalOnSharedBackend.kt \
        chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdClaimGuardConfiguration.kt \
        chat-core/src/test/kotlin/com/demo/chat/test/domain/SharedBackendConditionTests.kt
git commit -m "activate the claim guard from either core selector"
```

---

### Task 7: The Redis claim store

**Files:**
- Create: `chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisNodeIdClaimStore.kt`
- Create: `chat-persistence-redis/src/main/kotlin/com/demo/chat/config/persistence/redis/NodeIdClaimConfiguration.kt`
- Test: `chat-persistence-redis/src/test/kotlin/com/demo/chat/test/persistence/redis/RedisNodeIdClaimStoreTests.kt`

**Interfaces:**
- Consumes: `NodeIdClaimStore`, `ClaimResult`, `NodeId`, `ConditionalOnSharedBackend`, `NodeIdClaimGuardConfiguration`.
- Produces: `class RedisNodeIdClaimStore(template: ReactiveStringRedisTemplate, keyType: String)`.

- [ ] **Step 1: Write the failing store tests**

Create `chat-persistence-redis/src/test/kotlin/com/demo/chat/test/persistence/redis/RedisNodeIdClaimStoreTests.kt`.

```kotlin
package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.NodeId
import com.demo.chat.persistence.redis.impl.RedisNodeIdClaimStore
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Duration

/**
 * Store level tests for the redis claim.
 *
 * These call the store directly and pass the TTL as an argument. The guard
 * property rules do not apply here, so a one second lease is legal.
 *
 * Node ids 100 to 109 belong to this class. See the allocation table in the
 * plan. Two contexts against one container must not share a node id.
 */
@SpringBootTest(classes = [RedisPersistenceTestContext::class])
@Import(RedisPersistenceTestContext::class)
@Tag("integration")
class RedisNodeIdClaimStoreTests(
    @Autowired private val template: ReactiveStringRedisTemplate
) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }

    private val longStore = RedisNodeIdClaimStore(template, "long")
    private val uuidStore = RedisNodeIdClaimStore(template, "uuid")

    @BeforeEach
    fun flush() {
        template.connectionFactory.reactiveConnection.serverCommands().flushDb().block()
    }

    @Test
    fun `the scope names the backend and the key type`() {
        Assertions.assertEquals("redis", longStore.backendName)
        Assertions.assertEquals("redis store for key type long", longStore.scope)
    }

    @Test
    fun `a second owner is denied and the holder is named`() {
        val node = NodeId(100)
        Assertions.assertEquals(
            ClaimResult.Granted,
            longStore.claim(node, "owner-one", Duration.ofSeconds(30)).block()
        )

        val second = longStore.claim(node, "owner-two", Duration.ofSeconds(30)).block()

        Assertions.assertEquals(ClaimResult.Denied("owner-one"), second)
    }

    @Test
    fun `a release allows a takeover`() {
        val node = NodeId(101)
        longStore.claim(node, "owner-one", Duration.ofSeconds(30)).block()
        longStore.release(node, "owner-one").block()

        Assertions.assertEquals(
            ClaimResult.Granted,
            longStore.claim(node, "owner-two", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a release by another owner does nothing`() {
        val node = NodeId(102)
        longStore.claim(node, "owner-one", Duration.ofSeconds(30)).block()
        longStore.release(node, "owner-two").block()

        Assertions.assertEquals(
            ClaimResult.Denied("owner-one"),
            longStore.claim(node, "owner-three", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `an expiry allows a takeover`() {
        val node = NodeId(103)
        longStore.claim(node, "owner-one", Duration.ofSeconds(1)).block()
        Thread.sleep(1500)

        Assertions.assertEquals(
            ClaimResult.Granted,
            longStore.claim(node, "owner-two", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a renew by the holder extends the lease`() {
        val node = NodeId(104)
        longStore.claim(node, "owner-one", Duration.ofSeconds(1)).block()

        Assertions.assertEquals(
            ClaimResult.Granted,
            longStore.renew(node, "owner-one", Duration.ofSeconds(30)).block()
        )
        Thread.sleep(1500)
        Assertions.assertEquals(
            ClaimResult.Denied("owner-one"),
            longStore.claim(node, "owner-two", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a renew by another owner is denied and names the holder`() {
        val node = NodeId(105)
        longStore.claim(node, "owner-one", Duration.ofSeconds(30)).block()

        Assertions.assertEquals(
            ClaimResult.Denied("owner-one"),
            longStore.renew(node, "owner-two", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a renew with no live claim reports lost`() {
        Assertions.assertEquals(
            ClaimResult.Lost,
            longStore.renew(NodeId(106), "owner-one", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a long claim and a uuid claim on one node id both hold`() {
        val node = NodeId(107)

        Assertions.assertEquals(
            ClaimResult.Granted,
            longStore.claim(node, "owner-long", Duration.ofSeconds(30)).block()
        )
        Assertions.assertEquals(
            ClaimResult.Granted,
            uuidStore.claim(node, "owner-uuid", Duration.ofSeconds(30)).block()
        )
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -o -pl chat-persistence-redis -am -Pintegration test -Dtest=RedisNodeIdClaimStoreTests`

Expected: FAIL to compile, with an unresolved reference to `RedisNodeIdClaimStore`.

- [ ] **Step 3: Write the store**

Create `chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisNodeIdClaimStore.kt`.

```kotlin
package com.demo.chat.persistence.redis.impl

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimStore
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * The redis node id lease.
 *
 * The key carries the key type, because a cassandra deployment already
 * separates key types by keyspace. A long deployment and a uuid deployment
 * on one redis may both hold node id 7. Their value spaces do not intersect.
 *
 * `PX` makes the redis server the clock. No application clock enters the
 * decision.
 */
class RedisNodeIdClaimStore(
    private val template: ReactiveStringRedisTemplate,
    private val keyType: String
) : NodeIdClaimStore {

    override val backendName: String = "redis"

    override val scope: String = "redis store for key type $keyType"

    private fun key(nodeId: NodeId): String = "chat:nodeclaim:$keyType:${nodeId.value}"

    override fun claim(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult> =
        attempt(nodeId, owner, ttl)
            // The holder vanished between the write and the read. Try once more.
            .switchIfEmpty(attempt(nodeId, owner, ttl))
            .switchIfEmpty(
                Mono.error(
                    IllegalStateException(
                        "The $scope denied app.nodeid=${nodeId.value} twice and named no holder."
                    )
                )
            )

    override fun renew(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult> =
        run(renewScript, nodeId, owner, ttl.toMillis().toString())

    override fun release(nodeId: NodeId, owner: String): Mono<Void> =
        run(releaseScript, nodeId, owner, "0").then()

    private fun attempt(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult> =
        template.opsForValue().setIfAbsent(key(nodeId), owner, ttl)
            .flatMap { taken ->
                if (taken) Mono.just(ClaimResult.Granted as ClaimResult)
                // A diagnostic read. A stale value costs message quality only.
                else template.opsForValue().get(key(nodeId))
                    .map { holder -> ClaimResult.Denied(holder) as ClaimResult }
            }

    private fun run(
        script: RedisScript<String>,
        nodeId: NodeId,
        owner: String,
        millis: String
    ): Mono<ClaimResult> =
        template.execute(script, listOf(key(nodeId)), listOf(owner, millis))
            .next()
            .map { reply ->
                when {
                    reply == "granted" -> ClaimResult.Granted
                    reply == "lost" -> ClaimResult.Lost
                    reply.startsWith("denied:") -> ClaimResult.Denied(reply.removePrefix("denied:"))
                    else -> throw IllegalStateException(
                        "The $scope returned an unknown claim reply: $reply"
                    )
                }
            }

    companion object {
        // Lua gives the compare and set that plain commands cannot. The reply
        // is one string, because ReactiveStringRedisTemplate carries a string
        // serializer and a mixed Lua array would need a mixed result type.
        private val renewScript: RedisScript<String> = RedisScript.of(
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

        private val releaseScript: RedisScript<String> = RedisScript.of(
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
    }
}
```

- [ ] **Step 4: Write the store configuration**

Create `chat-persistence-redis/src/main/kotlin/com/demo/chat/config/persistence/redis/NodeIdClaimConfiguration.kt`.

```kotlin
package com.demo.chat.config.persistence.redis

import com.demo.chat.domain.ConditionalOnSharedBackend
import com.demo.chat.domain.NodeIdClaimGuardConfiguration
import com.demo.chat.domain.NodeIdClaimStore
import com.demo.chat.persistence.redis.impl.RedisNodeIdClaimStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * Registers the redis node id claim store.
 *
 * The bean name is explicit. The cassandra module ships a class with the
 * same simple name, and a classpath that names both backends registers both.
 * The `KeyGenConfiguration` classes carry explicit names for the same
 * reason.
 */
@Configuration("redisNodeIdClaimConfiguration")
@ConditionalOnSharedBackend("redis")
@Import(NodeIdClaimGuardConfiguration::class)
class NodeIdClaimConfiguration {

    @Bean("redisNodeIdClaimStore")
    fun redisNodeIdClaimStore(
        template: ReactiveStringRedisTemplate,
        @Value("\${app.key.type}") keyType: String
    ): NodeIdClaimStore = RedisNodeIdClaimStore(template, keyType)
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -o -pl chat-persistence-redis -am -Pintegration test -Dtest=RedisNodeIdClaimStoreTests`

Expected: PASS, 9 tests.

- [ ] **Step 6: Commit**

```bash
git add chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisNodeIdClaimStore.kt \
        chat-persistence-redis/src/main/kotlin/com/demo/chat/config/persistence/redis/NodeIdClaimConfiguration.kt \
        chat-persistence-redis/src/test/kotlin/com/demo/chat/test/persistence/redis/RedisNodeIdClaimStoreTests.kt
git commit -m "add the redis node id claim store"
```

---

### Task 8: The Cassandra claim store

**Files:**
- Create: `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraNodeIdClaimStore.kt`
- Create: `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/config/persistence/cassandra/NodeIdClaimConfiguration.kt`
- Test: `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/CassandraNodeIdClaimStoreTests.kt`

**Interfaces:**
- Consumes: `NodeIdClaimStore`, `ClaimResult`, `NodeId`, `ConditionalOnSharedBackend`, `NodeIdClaimGuardConfiguration`, and the `node_claim` table from Task 1.
- Produces: `class CassandraNodeIdClaimStore(template: ReactiveCassandraTemplate, keyspace: String)`.

- [ ] **Step 1: Write the failing store tests**

Create `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/CassandraNodeIdClaimStoreTests.kt`.

```kotlin
package com.demo.chat.test.persistence.integration

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.NodeId
import com.demo.chat.persistence.cassandra.impl.CassandraNodeIdClaimStore
import com.demo.chat.test.CassandraTestContainerConfiguration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.test.context.TestPropertySource
import java.time.Duration

/**
 * Store level tests for the cassandra claim.
 *
 * These call the store directly and pass the TTL as an argument. The guard
 * property rules do not apply here. Cassandra applies a TTL in whole
 * seconds, so the expiry test uses three seconds.
 *
 * Node ids 200 to 209 belong to this package. See the allocation table in
 * the plan. `truncate-long.cql` does not clear `node_claim`, so a reused
 * node id would meet an earlier claim.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [CassandraNodeIdClaimStoreTests.ClaimApp::class]
)
@Import(CassandraTestContainerConfiguration::class)
@TestPropertySource(properties = ["app.key.type=long", "app.nodeid=204"])
@Tag("integration")
class CassandraNodeIdClaimStoreTests {

    @SpringBootApplication
    class ClaimApp

    @Autowired
    private lateinit var template: ReactiveCassandraTemplate

    private val store by lazy { CassandraNodeIdClaimStore(template, "chat_long") }

    @Test
    fun `the scope names the backend and the keyspace`() {
        Assertions.assertEquals("cassandra", store.backendName)
        Assertions.assertEquals("cassandra keyspace chat_long", store.scope)
    }

    @Test
    fun `a second owner is denied and the holder is named`() {
        val node = NodeId(205)
        Assertions.assertEquals(
            ClaimResult.Granted,
            store.claim(node, "owner-one", Duration.ofSeconds(30)).block()
        )
        Assertions.assertEquals(
            ClaimResult.Denied("owner-one"),
            store.claim(node, "owner-two", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a release allows a takeover`() {
        val node = NodeId(206)
        store.claim(node, "owner-one", Duration.ofSeconds(30)).block()
        store.release(node, "owner-one").block()

        Assertions.assertEquals(
            ClaimResult.Granted,
            store.claim(node, "owner-two", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a release by another owner does nothing`() {
        val node = NodeId(207)
        store.claim(node, "owner-one", Duration.ofSeconds(30)).block()
        store.release(node, "owner-two").block()

        Assertions.assertEquals(
            ClaimResult.Denied("owner-one"),
            store.claim(node, "owner-three", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `an expiry allows a takeover`() {
        val node = NodeId(208)
        store.claim(node, "owner-one", Duration.ofSeconds(1)).block()
        Thread.sleep(Duration.ofSeconds(3).toMillis())

        Assertions.assertEquals(
            ClaimResult.Granted,
            store.claim(node, "owner-two", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a renew by the holder is granted`() {
        val node = NodeId(209)
        store.claim(node, "owner-one", Duration.ofSeconds(30)).block()

        Assertions.assertEquals(
            ClaimResult.Granted,
            store.renew(node, "owner-one", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a renew by another owner is denied and names the holder`() {
        val node = NodeId(200)
        store.claim(node, "owner-one", Duration.ofSeconds(30)).block()

        Assertions.assertEquals(
            ClaimResult.Denied("owner-one"),
            store.renew(node, "owner-two", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a renew with no live claim reports lost`() {
        Assertions.assertEquals(
            ClaimResult.Lost,
            store.renew(NodeId(201), "owner-one", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a missing table names the table and the schema file`() {
        val missing = CassandraNodeIdClaimStore(template, "chat_long", "node_claim_absent")

        val thrown = Assertions.assertThrows(Exception::class.java) {
            missing.claim(NodeId(202), "owner-one", Duration.ofSeconds(30)).block()
        }
        val text = generateSequence(thrown as Throwable) { it.cause }
            .mapNotNull { it.message }.joinToString(" | ")

        Assertions.assertTrue(text.contains("node_claim_absent"), text)
        Assertions.assertTrue(text.contains("keyspace-long.cql"), text)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -o -pl chat-persistence-cassandra -am -Pintegration test -Dtest=CassandraNodeIdClaimStoreTests`

Expected: FAIL to compile, with an unresolved reference to `CassandraNodeIdClaimStore`.

- [ ] **Step 3: Write the store**

Create `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraNodeIdClaimStore.kt`.

```kotlin
package com.demo.chat.persistence.cassandra.impl

import com.datastax.oss.driver.api.servererrors.InvalidQueryException
import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimStore
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * The cassandra node id lease.
 *
 * A lightweight transaction runs at SERIAL, and the coordinator applies the
 * TTL. No application clock enters the decision.
 *
 * The claim lives in the session keyspace, so `chat_long` and `chat_uuid`
 * hold separate leases. That matches the redis key type scoping.
 *
 * The table name is a parameter so that a test can point at an absent table
 * and prove the message.
 */
class CassandraNodeIdClaimStore(
    private val template: ReactiveCassandraTemplate,
    private val keyspace: String,
    private val table: String = "node_claim"
) : NodeIdClaimStore {

    override val backendName: String = "cassandra"

    override val scope: String = "cassandra keyspace $keyspace"

    private val cql get() = template.reactiveCqlOperations

    private val schemaFile: String = "keyspace-" + keyspace.removePrefix("chat_") + ".cql"

    override fun claim(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult> =
        cql.queryForRows(
            "INSERT INTO $table (node_id, owner_id) VALUES (?, ?) IF NOT EXISTS USING TTL ?",
            nodeId.value, owner, ttl.seconds.toInt()
        ).next().map { row ->
            if (row.getBoolean("[applied]")) ClaimResult.Granted as ClaimResult
            else ClaimResult.Denied(
                row.getString("owner_id")
                    ?: throw IllegalStateException(
                        "The $scope denied app.nodeid=${nodeId.value} and named no holder."
                    )
            )
        }.onErrorMap(InvalidQueryException::class.java) { missingTable(it) }

    override fun renew(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult> =
        cql.queryForRows(
            "UPDATE $table USING TTL ? SET owner_id = ? WHERE node_id = ? IF owner_id = ?",
            ttl.seconds.toInt(), owner, nodeId.value, owner
        ).next().map { row ->
            when {
                row.getBoolean("[applied]") -> ClaimResult.Granted as ClaimResult
                // No live row. The lease is gone, and no other owner is named.
                row.getString("owner_id") == null -> ClaimResult.Lost
                else -> ClaimResult.Denied(row.getString("owner_id")!!)
            }
        }.onErrorMap(InvalidQueryException::class.java) { missingTable(it) }

    override fun release(nodeId: NodeId, owner: String): Mono<Void> =
        cql.queryForRows(
            "DELETE FROM $table WHERE node_id = ? IF owner_id = ?",
            nodeId.value, owner
        ).next().then()
            .onErrorMap(InvalidQueryException::class.java) { missingTable(it) }

    private fun missingTable(cause: Throwable): Throwable =
        IllegalStateException(
            "The $scope has no $table table. " +
                "Apply $schemaFile from shared-resources-cassandra, or run the CREATE TABLE " +
                "statement in docs/NODEID-CLAIM.md against an existing keyspace.",
            cause
        )
}
```

- [ ] **Step 4: Write the store configuration**

Create `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/config/persistence/cassandra/NodeIdClaimConfiguration.kt`.

```kotlin
package com.demo.chat.config.persistence.cassandra

import com.demo.chat.domain.ConditionalOnSharedBackend
import com.demo.chat.domain.NodeIdClaimGuardConfiguration
import com.demo.chat.domain.NodeIdClaimStore
import com.demo.chat.persistence.cassandra.impl.CassandraNodeIdClaimStore
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate

/**
 * Registers the cassandra node id claim store.
 *
 * The bean name is explicit. The redis module ships a class with the same
 * simple name, and a classpath that names both backends registers both.
 */
@Configuration("cassandraNodeIdClaimConfiguration")
@ConditionalOnSharedBackend("cassandra")
@Import(NodeIdClaimGuardConfiguration::class)
class NodeIdClaimConfiguration {

    @Bean("cassandraNodeIdClaimStore")
    fun cassandraNodeIdClaimStore(
        template: ReactiveCassandraTemplate,
        properties: CassandraProperties
    ): NodeIdClaimStore = CassandraNodeIdClaimStore(template, properties.keyspaceName)
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -o -pl chat-persistence-cassandra -am -Pintegration test -Dtest=CassandraNodeIdClaimStoreTests`

Expected: PASS, 9 tests.

- [ ] **Step 6: Commit**

```bash
git add chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraNodeIdClaimStore.kt \
        chat-persistence-cassandra/src/main/kotlin/com/demo/chat/config/persistence/cassandra/NodeIdClaimConfiguration.kt \
        chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/CassandraNodeIdClaimStoreTests.kt
git commit -m "add the cassandra node id claim store"
```

---

### Task 9: Boot the redis seams

**Files:**
- Test: `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisClaimBootTests.kt`

**Interfaces:**
- Consumes: the redis store and configuration from Task 7. Reuses two members of `RedisDeployBootTests` as they already stand: the companion `val redis`, which is the shared `GenericContainer`, and the nested `@SpringBootApplication class BootApp`. Neither needs a change.
- Produces: no new type.

**Expected result.** Task 7 already put the guard on this classpath. These tests pass as soon as they are written. There is no red step here. The red step for this behaviour was Task 4, `a denial at the second store releases the first store`. If the duplicate test starts cleanly, the seam is broken. Read Step 3 before changing anything.

**Container properties.** `@DynamicPropertySource` applies to a `@SpringBootTest` context only. These tests drive `SpringApplicationBuilder` directly, so they pass the container address themselves. `RedisConfiguration` reads it from `ConfigurationPropertiesRedisTopics`, whose prefix is `redis-topics`.

- [ ] **Step 1: Write the boot tests**

Create `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisClaimBootTests.kt`.

```kotlin
package com.demo.chat.test.deploy.redis

import com.demo.chat.domain.NodeIdClaimStore
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Boot level tests for the redis claim seam.
 *
 * Node ids 11 and 12 belong to this class. See the allocation table in the
 * plan. `RedisDeployBootTests` holds node id 1 in the same container.
 *
 * The foreign claim is written straight to redis, not through a booted
 * context. A guarded context would claim the id itself and then release it
 * on close, and the duplicate would never be present for the second boot.
 */
@Tag("integration")
class RedisClaimBootTests {

    private val container = RedisDeployBootTests.redis

    private fun template(): ReactiveStringRedisTemplate {
        val factory = LettuceConnectionFactory(
            RedisStandaloneConfiguration(container.containerIpAddress, container.getMappedPort(6379))
        ).apply { afterPropertiesSet() }
        return ReactiveStringRedisTemplate(factory)
    }

    private fun seedForeignClaim(nodeId: Int, owner: String) {
        val applied = template().opsForValue()
            .setIfAbsent("chat:nodeclaim:long:$nodeId", owner, Duration.ofSeconds(120))
            .block()
        Assertions.assertEquals(true, applied, "the foreign claim seed must be applied")
    }

    // The launch surface of RedisDeployBootTests. Each test overrides only
    // what it needs after this list.
    private fun launchProperties(): Array<String> = arrayOf(
        "spring.application.name=redis-claim-boot-test",
        "spring.main.web-application-type=reactive",
        "server.port=0",
        "spring.rsocket.server.port=0",
        "app.server.proto=rsocket",
        "app.key.type=long",
        "app.service.core.pubsub=redis-pubsub",
        "app.service.core.index=lucene",
        "app.service.core.secrets=memory",
        "app.service.composite",
        "app.service.composite.auth",
        "app.controller.persistence",
        "app.controller.index",
        "app.controller.key",
        "app.controller.pubsub",
        "app.controller.secrets",
        "app.controller.user",
        "app.controller.topic",
        "app.controller.message",
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "spring.cloud.consul.config.enabled=false",
        "redis-topics.host=${container.containerIpAddress}",
        "redis-topics.port=${container.getMappedPort(6379)}"
    )

    private fun boot(vararg extra: String, ready: AtomicBoolean) =
        SpringApplicationBuilder(RedisDeployBootTests.BootApp::class.java)
            .web(WebApplicationType.REACTIVE)
            .properties(*launchProperties(), *extra)
            .listeners(ApplicationListener<ApplicationReadyEvent> { ready.set(true) })

    private fun allMessages(thrown: Throwable): String =
        generateSequence(thrown) { it.cause }.mapNotNull { it.message }.joinToString(" | ")

    @Test
    fun `a live claim fails startup with the actionable message`() {
        seedForeignClaim(11, "foreign-owner@host-z:1#deadbeef")
        val ready = AtomicBoolean(false)

        val thrown = Assertions.assertThrows(Exception::class.java) {
            boot(
                "app.nodeid=11",
                "app.service.core.key=redis",
                "app.service.core.persistence=redis",
                ready = ready
            ).run().close()
        }

        val text = allMessages(thrown)
        Assertions.assertTrue(text.contains("app.nodeid=11 is already claimed"), text)
        Assertions.assertTrue(text.contains("redis store for key type long"), text)
        Assertions.assertTrue(text.contains("foreign-owner@host-z:1#deadbeef"), text)
        // The requirement is failure before ready and before normal traffic.
        Assertions.assertFalse(ready.get(), "ApplicationReadyEvent must not be published")
    }

    @Test
    fun `a memory key selector with redis persistence still claims`() {
        val ready = AtomicBoolean(false)

        boot(
            "app.nodeid=12",
            "app.service.core.key=memory",
            "app.service.core.persistence=redis",
            ready = ready
        ).run().use { context ->
            val stores = context.getBeansOfType(NodeIdClaimStore::class.java)
            Assertions.assertEquals(1, stores.size)
            Assertions.assertEquals("redis", stores.values.first().backendName)
        }

        Assertions.assertTrue(ready.get())
    }
}
```

- [ ] **Step 2: Run the boot tests**

Run: `mvn -o -pl chat-deploy-redis -am -Pintegration test -Dtest=RedisClaimBootTests`

Expected: PASS, 2 tests.

- [ ] **Step 3: Read a clean start as a seam failure**

If `a live claim fails startup with the actionable message` reports that no exception was thrown, the guard did not activate. Check three things in order:

1. `app.key.type` is set. `NodeIdClaimConfiguration` reads it with `@Value` and fails when it is absent.
2. `app.service.core.key` or `app.service.core.persistence` is `redis`, so `OnSharedBackendCondition` matches.
3. `chat-persistence-redis` is on the test classpath of `chat-deploy-redis`.

Fix the launch properties or the condition. Do not weaken the test.

- [ ] **Step 4: Run the whole redis deploy module**

Run: `mvn -o -pl chat-deploy-redis -am -Pintegration test`

Expected: PASS. `RedisDeployBootTests` still boots on node id 1, and it now takes a real claim in the shared container.

- [ ] **Step 5: Commit**

```bash
git add chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisClaimBootTests.kt
git commit -m "boot the redis claim seams and prove the duplicate failure"
```

---

### Task 10: Boot the cassandra seams

**Files:**
- Test: `chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraClaimBootTests.kt`

**Interfaces:**
- Consumes: the cassandra store and configuration from Task 8, and the `node_claim` table from Task 1. Extends `CassandraContainerBase`, which already starts the container and applies both keyspaces.
- Produces: no new type.

**Expected result.** Task 8 already put the guard on this classpath. These tests pass as soon as they are written. There is no red step here.

**Seeding the foreign claim.** The seed must not run through a booted deploy context. A guarded context claims the id itself, so the seed insert would find the row taken. The context then releases the id on close, and the second boot would start cleanly. The seed therefore runs through `cqlsh` in the container, which is the pattern `CassandraContainerBase` already uses to apply the long keyspace. The test asserts that the seed row is present before it boots.

- [ ] **Step 1: Write the boot tests**

Create `chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraClaimBootTests.kt`.

```kotlin
package com.demo.chat.test.deploy.cassandra

import com.demo.chat.ChatApp
import com.demo.chat.domain.NodeIdClaimStore
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Boot level tests for the cassandra claim seam.
 *
 * Node ids 21 and 22 belong to this class. See the allocation table in the
 * plan. `CassandraDeployTest` holds node id 1 in the same container, and
 * truncate-long.cql does not clear node_claim.
 */
@Tag("integration")
class CassandraClaimBootTests : CassandraContainerBase() {

    private fun cqlsh(statement: String): String {
        val result = cassandraContainer.execInContainer(
            "cqlsh",
            "-u", cassandraContainer.username,
            "-p", cassandraContainer.password,
            "-e", statement
        )
        Assertions.assertEquals(
            0, result.exitCode,
            "cqlsh failed. stdout: ${result.stdout} stderr: ${result.stderr}"
        )
        return result.stdout
    }

    /**
     * Writes the claim with cqlsh, outside every application context.
     *
     * A booted context would take the id itself and release it on close, and
     * the duplicate would not be there for the second boot.
     */
    private fun seedForeignClaim(nodeId: Int, owner: String) {
        cqlsh(
            "INSERT INTO chat_long.node_claim (node_id, owner_id) " +
                "VALUES ($nodeId, '$owner') IF NOT EXISTS USING TTL 300;"
        )
        val readBack = cqlsh("SELECT owner_id FROM chat_long.node_claim WHERE node_id = $nodeId;")
        Assertions.assertTrue(
            readBack.contains(owner),
            "the foreign claim seed must be applied. cqlsh said: $readBack"
        )
    }

    private fun launchProperties(): Array<String> = arrayOf(
        "spring.config.location=classpath:/application.yml",
        "spring.config.additional-location=classpath:/config/logging.yml," +
            "classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "server.port=0",
        "spring.rsocket.server.port=0",
        "app.key.type=long",
        "app.service.core.pubsub=memory",
        "app.service.core.index=cassandra",
        "app.service.core.secrets=cassandra",
        "app.service.composite",
        "app.service.composite.auth",
        "app.controller.secrets",
        "app.controller.key",
        "app.controller.persistence",
        "app.controller.index",
        "app.controller.user",
        "app.controller.message",
        "app.controller.topic",
        "app.controller.pubsub",
        "app.service.security.userdetails",
        "spring.profiles.active=cassandra-contact-point",
        // SpringApplicationBuilder does not read @DynamicPropertySource.
        "spring.cassandra.contact-points=${cassandraContainer.host}",
        "spring.cassandra.port=${cassandraContainer.getMappedPort(9042)}",
        "spring.cassandra.username=${cassandraContainer.username}",
        "spring.cassandra.password=${cassandraContainer.password}"
    )

    private fun boot(vararg extra: String, ready: AtomicBoolean) =
        SpringApplicationBuilder(ChatApp::class.java)
            .web(WebApplicationType.NONE)
            .properties(*launchProperties(), *extra)
            .listeners(ApplicationListener<ApplicationReadyEvent> { ready.set(true) })

    private fun allMessages(thrown: Throwable): String =
        generateSequence(thrown) { it.cause }.mapNotNull { it.message }.joinToString(" | ")

    @Test
    fun `a live claim fails startup with the actionable message`() {
        seedForeignClaim(21, "foreign-owner@host-z:1#deadbeef")
        val ready = AtomicBoolean(false)

        val thrown = Assertions.assertThrows(Exception::class.java) {
            boot(
                "app.nodeid=21",
                "app.service.core.key=cassandra",
                "app.service.core.persistence=cassandra",
                ready = ready
            ).run().close()
        }

        val text = allMessages(thrown)
        Assertions.assertTrue(text.contains("app.nodeid=21 is already claimed"), text)
        Assertions.assertTrue(text.contains("cassandra keyspace chat_long"), text)
        Assertions.assertTrue(text.contains("foreign-owner@host-z:1#deadbeef"), text)
        Assertions.assertFalse(ready.get(), "ApplicationReadyEvent must not be published")
    }

    @Test
    fun `a memory key selector with cassandra persistence still claims`() {
        val ready = AtomicBoolean(false)

        boot(
            "app.nodeid=22",
            "app.service.core.key=memory",
            "app.service.core.persistence=cassandra",
            ready = ready
        ).run().use { context ->
            val stores = context.getBeansOfType(NodeIdClaimStore::class.java)
            Assertions.assertEquals(1, stores.size)
            Assertions.assertEquals("cassandra", stores.values.first().backendName)
        }

        Assertions.assertTrue(ready.get())
    }
}
```

- [ ] **Step 2: Run the boot tests**

Run: `mvn -o -pl chat-deploy-cassandra -am -Pintegration test -Dtest=CassandraClaimBootTests`

Expected: PASS, 2 tests.

- [ ] **Step 3: Read a clean start as a seam failure**

If the duplicate test reports that no exception was thrown, check the same three points as Task 9, Step 3, with `cassandra` in place of `redis`. Then check that the seed assertion passed. A seed that did not apply means node 21 was already held, and the allocation table was broken.

Fix the launch properties or the condition. Do not weaken the test.

- [ ] **Step 4: Run the whole cassandra deploy module**

Run: `mvn -o -pl chat-deploy-cassandra -am -Pintegration test`

Expected: PASS. `CassandraDeployTest` still boots on node id 1.

- [ ] **Step 5: Commit**

```bash
git add chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraClaimBootTests.kt
git commit -m "boot the cassandra claim seams and prove the duplicate failure"
```

---

### Task 11: Memory contributes no false safety

**Files:**
- Test: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MemoryClaimAbsenceTests.kt`

**Interfaces:**
- Consumes: `NodeIdClaimStore`, `NodeIdClaimGuard`.
- Produces: no new type.

- [ ] **Step 1: Write the failing absence test**

Create `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MemoryClaimAbsenceTests.kt`. Copy the property list from `MemoryDeploymentTests` in the same package.

```kotlin
package com.demo.chat.test.deploy.memory

import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimGuard
import com.demo.chat.domain.NodeIdClaimStore
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

/**
 * Memory offers no claim, and it must not look as though it does.
 *
 * The limit of this test, stated so it is not overclaimed: memory still
 * requires `app.nodeid`. Stage 1 requires it wherever a key generator
 * activates. What this test pins is that the claim design adds no
 * requirement to memory and supplies no no-operation store.
 *
 * A no-operation store that returned Granted would be false safety. A
 * memory store is per process, so it can prove nothing about another
 * deployment.
 */
@SpringBootTest(
    // Copy the classes and properties from MemoryDeploymentTests in this package.
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class MemoryClaimAbsenceTests {

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `a memory deployment registers no claim store`() {
        Assertions.assertTrue(context.getBeansOfType(NodeIdClaimStore::class.java).isEmpty())
    }

    @Test
    fun `a memory deployment registers no claim guard`() {
        Assertions.assertTrue(context.getBeansOfType(NodeIdClaimGuard::class.java).isEmpty())
    }

    @Test
    fun `a memory deployment still supplies the node id from stage one`() {
        Assertions.assertEquals(1, context.getBean(NodeId::class.java).value)
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `mvn -o -pl chat-deploy-memory -am test -Dtest=MemoryClaimAbsenceTests`

Expected: PASS, 3 tests. Nothing on this classpath names redis or cassandra, so the condition never matches.

If a claim store bean appears, the condition is wrong. Check `OnSharedBackendCondition`. Do not weaken the test.

- [ ] **Step 3: Commit**

```bash
git add chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MemoryClaimAbsenceTests.kt
git commit -m "pin that a memory deployment offers no claim and no false safety"
```

---

### Task 12: The operator document and the build gate

**Files:**
- Create: `docs/NODEID-CLAIM.md`
- Modify: `forward-register.md`

**Interfaces:**
- Consumes: everything.
- Produces: the upgrade statement referenced by `CassandraNodeIdClaimStore.missingTable`.

- [ ] **Step 1: Write the operator document**

Create `docs/NODEID-CLAIM.md`.

````markdown
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

## Scope of the claim

Uniqueness is per key type per store.

Cassandra separates key types by keyspace. Redis carries the key type in the
claim key, `chat:nodeclaim:<keyType>:<nodeId>`.

A `long` deployment and a `uuid` deployment on one redis may both hold node
id 7. Their value spaces do not intersect.

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

The process closes its context `ttl` minus `safety-margin` after the last
successful renew. With the defaults that is 25 seconds.

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

## Testing note

Every container-backed test that activates a claim store uses its own
`app.nodeid`. Spring caches contexts, so two open contexts against one store
would collide. That collision is correct behaviour, and it appears as a test
failure.
````

- [ ] **Step 2: Run the default build gate**

Run: `./shell-scripts/build-health.sh`

Expected: exit status 0, and no NEW module reported.

- [ ] **Step 3: Run the integration build gate**

Run: `./shell-scripts/build-health.sh --integration`

Expected: exit status 0, and no NEW module reported. A Docker daemon must be running.

If a module reports NEW, fix the code. Do not add the module to
`KNOWN_FAILING_INTEGRATION`.

- [ ] **Step 4: Record the outcome in the forward register**

Add a section to `forward-register.md` under the existing structure. State four things: the claim seam is either core selector, uniqueness is per key type per store, `node_claim` is absent from the truncate scripts on purpose, and every container-backed claim test owns a distinct `app.nodeid`.

Record the lifecycle observation from the boot tests: whether the server port bound before the claim failed. Record it as an observation, not as a requirement.

- [ ] **Step 5: Commit**

```bash
git add docs/NODEID-CLAIM.md forward-register.md
git commit -m "document the node id claim lease and record the build gate"
```

---

## Self-review

**Spec coverage.** Every spec section maps to a task. D1 to Task 6. D2 to Tasks 4 and 5. D3 to Task 1 and Task 12. D4 to Tasks 4, 9, and 10. D5 to Tasks 7 and 8. D6 to Task 11. The contract to Task 2. The guard to Tasks 4 and 5. The owner id and scheduler to Task 3. Redis to Task 7. Cassandra to Task 8. The message to Task 2. The Cassandra UUID note to Task 12. Every test in the spec test tables appears in a task. R1 appears as the allocation table in Global Constraints. R2 appears in Task 1 comments and Task 8. R3 appears in the properties. R4 appears in Tasks 3 and 5. Unverified claim 1 is Task 1. Unverified claim 2 is Task 9 Step 2 and Task 10 Step 1.

**One gap to accept.** Task 11 leaves the memory deployment classes and properties to be copied from `MemoryDeploymentTests` in the same package. The step names the file and the fields. Every other step carries its content, including the launch property lists in Tasks 9 and 10.

**Red steps.** Tasks 1 to 8 each write a failing test first. Tasks 9, 10, and 11 do not, and they say so. By Task 9 the redis guard is already wired by Task 7, and by Task 10 the cassandra guard is already wired by Task 8. A boot test written after its guard cannot fail first. Those tasks instead state what a clean start means: the seam is broken, and the step lists what to check. The red step for the denial behaviour itself is Task 4.

**Build order.** Every `mvn -pl` command outside `chat-core` carries `-am`. Task 1 needs it because `shared-resources-cassandra` changes. Tasks 7 to 11 need it because `chat-core` gains new types. Without `-am` those modules compile against the stale installed artifact.

**Type consistency.** `NodeIdClaimException` takes `(nodeId, scope, holder, ttl)` in Task 2 and is constructed with those four arguments in Tasks 4 and 5. `NodeIdClaimStore` declares `backendName`, `scope`, `claim`, `renew`, `release` in Task 2 and is implemented with the same names in Tasks 7 and 8. `ClaimScheduler` declares five methods in Task 3, and `ManualClaimScheduler` in Task 4 implements all five. `NodeIdClaimProperties` exposes `ttl`, `renewInterval`, `safetyMargin`, `operationTimeout`, `closeDeadline` in Task 2 and is read with those names in Task 4.

**One deviation from the spec, already applied to the spec.** `NodeIdClaimException` drops the `backendName` field. The scope phrase carries the backend name, and one template then serves both backends. `backendName` stays on the store, where it orders the claims.

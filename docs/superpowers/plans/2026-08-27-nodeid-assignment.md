# nodeId Assignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:executing-plans to implement this plan task-by-task. Subagent-driven development is forbidden by AGENTS.md. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `app.nodeid` an explicit and validated deployment input, and delete every code path that derives a node id or supplies one by default.

**Architecture:** One `NodeId` value type in `chat-core` owns the range rule and the error text. A `@Configuration` class reads the raw property from `Environment` and supplies one validated bean. The three `KeyGenConfiguration` classes import that class rather than each declaring `@Value`. The deriving constructor and the two `0 -> derive` sentinels are deleted. The launcher gains a required `--node-id` argument.

**Tech Stack:** Kotlin 1.8, Spring Boot 3.3, JUnit 5, Maven, Python 3 for `shell-scripts/chat-build`.

**Spec:** `docs/superpowers/specs/2026-08-27-nodeid-assignment-design.md`

## Global Constraints

- Kotlin is the primary language.
- Commit messages are lowercase imperative with no prefix. Match `drop dead json wrapper from the seven e2ee types`.
- Run tests as `mvn -o -pl chat-core test`. Never run a dependent module alone. See the forward register, "Things worth not relearning".
- Run `mvn -o -pl chat-core clean test` after a branch switch. A stale compiled test class outlives its source.
- `app.nodeid` accepts an integer from 0 to 1023 inclusive. 0 is legal and explicit.
- There is no default and no derivation, in the runtime or in the launcher.
- Controlled English applies to comments, commit messages, and error text. Use plain words and active voice. Do not use semicolons.

## Verified before planning

- `ChatApp.kt` in `chat-deploy` and in `chat-authorization-server` sets `scanBasePackages = ["com.demo.chat.config"]`. That package is component-scanned in every deployment.
- No component scan covers `com.demo.chat.domain`. The scans in use are `com.demo.chat.config`, `com.demo.chat.shell`, `com.demo.chat.controller.webflux`, and `com.demo.chat.config.deploy.cassandra.dse`.
- `shell-scripts/chat-build` builds arguments in `add_common_args` at line 852, sets fields in `BuildContext.__init__` at line 472, where `BuildContext` is declared at line 469, and emits flags in `main_flags` at line 591.
- `shell-scripts/test-flags.sh` holds a `CASES` array of `name|arguments` entries at line 46.
- `app.nodeid` is set in no configuration file anywhere in the repository.

## File Structure

| File | Responsibility |
|---|---|
| `chat-core/src/main/kotlin/com/demo/chat/domain/NodeId.kt` | The value type. Owns the range rule, the parse function, and the error text. |
| `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdConfiguration.kt` | Supplies one validated `NodeId` bean from `Environment`. Not component-scanned. |
| `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdTests.kt` | Unit tests for `NodeId.parse`. No Spring context. |
| `chat-core/src/main/kotlin/com/demo/chat/domain/SnowflakeGenerator.kt` | Loses the no-argument constructor and `createNodeId`. |
| `chat-core/src/main/kotlin/com/demo/chat/service/LongKeyGenerator.kt` | Loses the `0 -> derive` sentinel. |
| `chat-core/src/main/kotlin/com/demo/chat/service/UUIDKeyGenerator.kt` | Loses the `0 -> derive` sentinel. |
| Three `KeyGenConfiguration.kt` files | Inject `NodeId` instead of reading `@Value`. |
| `shell-scripts/chat-build` | Gains a required `--node-id` argument and emits `-Dapp.nodeid`. |
| `shell-scripts/test-flags.sh` | Passes `--node-id 0` in every case. |
| `shell-scripts/golden/*.flags` | Regenerated, 15 files. |

**Why `NodeIdConfiguration` lives in `com.demo.chat.domain`.** `com.demo.chat.config` is
component-scanned by every deployment. A `@Configuration` class placed there would supply the
bean in every process, including processes that generate no keys, and would fail their startup
when `app.nodeid` is absent. `com.demo.chat.domain` is scanned by nothing, so the bean appears
only where a `KeyGenConfiguration` imports it.

---

### Task 1: The NodeId value type and its bean

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/NodeId.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdConfiguration.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdTests.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdConfigurationTests.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `com.demo.chat.domain.NodeId` with `val value: Int`, `NodeId.parse(raw: String?): NodeId`, `NodeId.message(raw: String?): String`, `NodeId.MIN = 0`, `NodeId.MAX = 1023`. Also `com.demo.chat.domain.NodeIdConfiguration`, a `@Configuration` exposing a `NodeId` bean.

- [ ] **Step 1: Write the failing tests**

Create `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdTests.kt`:

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.NodeId
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class NodeIdTests {

    @Test
    fun `parse accepts zero as an explicit value`() {
        Assertions.assertEquals(0, NodeId.parse("0").value)
    }

    @Test
    fun `parse accepts the maximum value`() {
        Assertions.assertEquals(1023, NodeId.parse("1023").value)
    }

    @Test
    fun `parse trims surrounding whitespace`() {
        Assertions.assertEquals(7, NodeId.parse("  7  ").value)
    }

    @Test
    fun `parse rejects an unset value`() {
        val thrown = Assertions.assertThrows(IllegalArgumentException::class.java) {
            NodeId.parse(null)
        }
        Assertions.assertTrue(thrown.message!!.contains("app.nodeid is required"))
        Assertions.assertTrue(thrown.message!!.contains("unset"))
    }

    @Test
    fun `parse rejects an empty value`() {
        val thrown = Assertions.assertThrows(IllegalArgumentException::class.java) {
            NodeId.parse("")
        }
        Assertions.assertTrue(thrown.message!!.contains("app.nodeid is required"))
    }

    @Test
    fun `parse rejects a whitespace only value`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            NodeId.parse("   ")
        }
    }

    @Test
    fun `parse rejects a non numeric value`() {
        val thrown = Assertions.assertThrows(IllegalArgumentException::class.java) {
            NodeId.parse("abc")
        }
        Assertions.assertTrue(thrown.message!!.contains("abc"))
    }

    @Test
    fun `parse rejects a negative value`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            NodeId.parse("-1")
        }
    }

    @Test
    fun `parse rejects a value above the maximum`() {
        val thrown = Assertions.assertThrows(IllegalArgumentException::class.java) {
            NodeId.parse("1024")
        }
        Assertions.assertTrue(thrown.message!!.contains("0..1023"))
    }

    @Test
    fun `the message names the property and the range`() {
        val text = NodeId.message("99999")
        Assertions.assertTrue(text.contains("app.nodeid"))
        Assertions.assertTrue(text.contains("0..1023"))
        Assertions.assertTrue(text.contains("no default"))
        Assertions.assertTrue(text.contains("99999"))
    }

    @Test
    fun `the message calls a null value unset`() {
        val text = NodeId.message(null)
        Assertions.assertTrue(text.contains("unset"))
    }

    @Test
    fun `the message shows a blank value in quotes rather than as unset`() {
        val text = NodeId.message("   ")
        Assertions.assertTrue(text.contains("'   '"), text)
        Assertions.assertFalse(text.contains("unset"), text)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -o -pl chat-core test -Dtest=NodeIdTests`
Expected: FAIL. Compilation fails because `com.demo.chat.domain.NodeId` does not exist.

- [ ] **Step 3: Create the value type**

Create `chat-core/src/main/kotlin/com/demo/chat/domain/NodeId.kt`:

```kotlin
package com.demo.chat.domain

/**
 * A validated node id. Ten node bits in [SnowflakeGenerator] give 1024 values.
 *
 * There is no default and no derivation. A deployment states its node id, and
 * two deployments that write to one store must not state the same one.
 */
class NodeId(val value: Int) {

    init {
        require(value in MIN..MAX) { message(value.toString()) }
    }

    override fun equals(other: Any?): Boolean = other is NodeId && other.value == value

    override fun hashCode(): Int = value

    override fun toString(): String = "NodeId($value)"

    companion object {
        const val MIN = 0
        const val MAX = 1023

        fun parse(raw: String?): NodeId {
            val trimmed = raw?.trim()
            require(!trimmed.isNullOrEmpty()) { message(raw) }
            val parsed = trimmed.toIntOrNull()
            require(parsed != null) { message(raw) }
            return NodeId(parsed)
        }

        fun message(raw: String?): String =
            "app.nodeid is required and has no default. " +
                "Set it to an integer in $MIN..$MAX, unique across every deployment " +
                "that writes to this store. Got: ${describe(raw)}"

        // Only a null property is unset. An empty or blank value was supplied, so
        // show it in quotes rather than hide it behind the word unset.
        private fun describe(raw: String?): String = if (raw == null) "unset" else "'" + raw + "'"
    }
}
```

- [ ] **Step 4: Create the configuration class**

Create `chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdConfiguration.kt`:

```kotlin
package com.demo.chat.domain

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

/**
 * Supplies the validated [NodeId].
 *
 * This class sits in `com.demo.chat.domain` on purpose. `com.demo.chat.config` is
 * component-scanned by every deployment, so a copy placed there would supply the
 * bean in processes that generate no keys and would fail their startup.
 *
 * Read the raw value from [Environment]. Do not use `@Value("\${app.nodeid}")`.
 * Spring resolves a placeholder before it injects a field, so an unset property
 * fails placeholder resolution and [NodeId.parse] never reports the missing value.
 */
@Configuration
open class NodeIdConfiguration {

    @Bean
    open fun nodeId(environment: Environment): NodeId =
        NodeId.parse(environment.getProperty("app.nodeid"))
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -o -pl chat-core test -Dtest=NodeIdTests`
Expected: PASS. Twelve tests, zero failures.

- [ ] **Step 6: Write the configuration test**

This test guards the `Environment.getProperty` choice. A change back to
`@Value("\${app.nodeid}")` breaks it, because the failure text becomes a Spring
placeholder error instead of the message from `NodeId`.

Create `chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdConfigurationTests.kt`:

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdConfiguration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.mock.env.MockEnvironment

class NodeIdConfigurationTests {

    private fun contextWith(nodeId: String?): AnnotationConfigApplicationContext {
        val context = AnnotationConfigApplicationContext()
        val environment = MockEnvironment()
        if (nodeId != null) {
            environment.setProperty("app.nodeid", nodeId)
        }
        context.environment = environment
        context.register(NodeIdConfiguration::class.java)
        context.refresh()
        return context
    }

    private fun allMessages(thrown: Throwable): String =
        generateSequence(thrown) { it.cause }.mapNotNull { it.message }.joinToString(" | ")

    @Test
    fun `a context without app nodeid fails and names the property`() {
        val thrown = Assertions.assertThrows(Exception::class.java) { contextWith(null) }
        val text = allMessages(thrown)
        Assertions.assertTrue(text.contains("app.nodeid is required"), text)
        Assertions.assertTrue(text.contains("unset"), text)
    }

    @Test
    fun `a context with a value out of range fails and names the range`() {
        val thrown = Assertions.assertThrows(Exception::class.java) { contextWith("1024") }
        Assertions.assertTrue(allMessages(thrown).contains("0..1023"))
    }

    @Test
    fun `a context with a valid app nodeid supplies the bean`() {
        contextWith("5").use { context ->
            Assertions.assertEquals(5, context.getBean(NodeId::class.java).value)
        }
    }

    @Test
    fun `a context accepts zero as an explicit value`() {
        contextWith("0").use { context ->
            Assertions.assertEquals(0, context.getBean(NodeId::class.java).value)
        }
    }
}
```

- [ ] **Step 7: Run the configuration test**

Run: `mvn -o -pl chat-core test -Dtest=NodeIdConfigurationTests`
Expected: PASS. Four tests, zero failures.

If `MockEnvironment` does not resolve, `spring-test` is missing from `chat-core` test scope.
Add it rather than weakening the test. `chat-core` already uses `spring-test` in
`com.demo.chat.test.codec.ConversionTests`.

- [ ] **Step 8: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/NodeId.kt \
        chat-core/src/main/kotlin/com/demo/chat/domain/NodeIdConfiguration.kt \
        chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdTests.kt \
        chat-core/src/test/kotlin/com/demo/chat/test/domain/NodeIdConfigurationTests.kt
git commit -m "add a validated nodeid type and its bean"
```

---

### Task 2: Remove the derivation and require the node id

Tasks 2, 3, and 4 land together. The tree is releasable at the end of Task 4, not between these tasks. After Task 2 a generated launch fails until Task 3 emits the flag.

**Files:**
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/SnowflakeGenerator.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/service/LongKeyGenerator.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/service/UUIDKeyGenerator.kt`
- Modify: `chat-core/src/test/kotlin/com/demo/chat/test/domain/SnowflakeGeneratorTests.kt`
- Modify: `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/config/persistence/cassandra/KeyGenConfiguration.kt`
- Modify: `chat-persistence-redis/src/main/kotlin/com/demo/chat/config/persistence/redis/KeyGenConfiguration.kt`
- Modify: `chat-persistence-memory/src/main/kotlin/com/demo/chat/config/persistence/memory/KeyGenConfiguration.kt`

**Interfaces:**
- Consumes: `NodeId` and `NodeIdConfiguration` from Task 1.
- Produces: `SnowflakeGenerator(nodeId: Int)` as the only constructor. `LongKeyGenerator(nodeId: Int)` and `UUIDKeyGenerator(nodeId: Int)` keep their signatures and never derive.

The generator change and the configuration change are one semantic change. Split them and the
tree holds a worse state than today, because every deployment would resolve to node 0.

- [ ] **Step 1: Delete the deriving constructor**

In `chat-core/src/main/kotlin/com/demo/chat/domain/SnowflakeGenerator.kt`, delete this
constructor:

```kotlin
    // Let SequenceGenerator generate a nodeId
    constructor() {
        nodeId = createNodeId()

    }
```

Delete the whole `createNodeId()` function. Delete these two imports:

```kotlin
import java.net.NetworkInterface
import java.security.SecureRandom
```

Keep the `require` inside `constructor(nodeId: Int)`. It states the invariant of the class.

- [ ] **Step 2: Delete the sentinel in LongKeyGenerator**

Replace the body of `chat-core/src/main/kotlin/com/demo/chat/service/LongKeyGenerator.kt`:

```kotlin
package com.demo.chat.service

import com.demo.chat.domain.SnowflakeGenerator
import com.demo.chat.service.core.IKeyGenerator

class LongKeyGenerator(nodeId: Int) : IKeyGenerator<Long> {
    private val idGenerator: IKeyGenerator<Long> = SnowflakeGenerator(nodeId)

    override fun nextId(): Long = idGenerator.nextId()
}
```

- [ ] **Step 3: Delete the sentinel in UUIDKeyGenerator**

Replace the body of `chat-core/src/main/kotlin/com/demo/chat/service/UUIDKeyGenerator.kt`:

```kotlin
package com.demo.chat.service

import com.demo.chat.domain.SnowflakeGenerator
import com.demo.chat.service.core.IKeyGenerator
import java.util.*

class UUIDKeyGenerator(nodeId: Int) : IKeyGenerator<UUID> {
    private val idGenerator: IKeyGenerator<Long> = SnowflakeGenerator(nodeId)

    override fun nextId(): UUID =
        UUID.nameUUIDFromBytes(idGenerator.nextId().toString().encodeToByteArray())
}
```

- [ ] **Step 4: Update the cassandra configuration**

Replace `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/config/persistence/cassandra/KeyGenConfiguration.kt`:

```kotlin
package com.demo.chat.config.persistence.cassandra

import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdConfiguration
import com.demo.chat.persistence.cassandra.domain.keygen.CassandraUUIDKeyGenerator
import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.core.IKeyGenerator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.util.*

// One key generator per deployment. The selector that picks the key
// backend picks its generator too, so exactly one of these registers.
// Ungated they all registered, and two of them share this simple class
// name - a classpath carrying both failed to start on a conflicting bean
// definition rather than on anything meaningful. Cassandra is the one that differs: its uuid generator is CassandraUUIDKeyGenerator.
@Configuration("cassandraKeyGenConfiguration")
@ConditionalOnProperty(prefix = "app.service.core", name = ["key"], havingValue = "cassandra")
@Import(NodeIdConfiguration::class)
class KeyGenConfiguration {

    @Bean("KeyGenerator")
    @ConditionalOnProperty("app.key.type", havingValue = "long")
    fun longKeyGen(nodeId: NodeId): IKeyGenerator<Long> = LongKeyGenerator(nodeId.value)

    // CassandraUUIDKeyGenerator takes no node id. The NodeId parameter is here on
    // purpose, so that app.nodeid is validated on this path too and one contract
    // covers all three backends.
    @Bean("KeyGenerator")
    @ConditionalOnProperty("app.key.type", havingValue = "uuid")
    fun uuidKeyGen(nodeId: NodeId): IKeyGenerator<UUID> = CassandraUUIDKeyGenerator()
}
```

- [ ] **Step 5: Update the redis configuration**

Replace `chat-persistence-redis/src/main/kotlin/com/demo/chat/config/persistence/redis/KeyGenConfiguration.kt`:

```kotlin
package com.demo.chat.config.persistence.redis

import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdConfiguration
import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.UUIDKeyGenerator
import com.demo.chat.service.core.IKeyGenerator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.util.*

/**
 * Provides the [IKeyGenerator] bean for Redis deployments, mirroring the
 * memory/cassandra modules. Selected by `app.key.type` (uuid | long).
 */
// One key generator per deployment. The selector that picks the key
// backend picks its generator too, so exactly one of these registers.
// Ungated they all registered, and two of them share this simple class
// name - a classpath carrying both failed to start on a conflicting bean
// definition rather than on anything meaningful. Redis is selected explicitly or not at all.
@Configuration("redisKeyGenConfiguration")
@ConditionalOnProperty(prefix = "app.service.core", name = ["key"], havingValue = "redis")
@Import(NodeIdConfiguration::class)
class KeyGenConfiguration {

    @ConditionalOnProperty("app.key.type", havingValue = "uuid")
    @Bean("KeyGenerator")
    fun uuidGenerator(nodeId: NodeId): IKeyGenerator<UUID> = UUIDKeyGenerator(nodeId.value)

    @ConditionalOnProperty("app.key.type", havingValue = "long")
    @Bean("KeyGenerator")
    fun longGenerator(nodeId: NodeId): IKeyGenerator<Long> = LongKeyGenerator(nodeId.value)
}
```

- [ ] **Step 6: Update the memory configuration**

Replace `chat-persistence-memory/src/main/kotlin/com/demo/chat/config/persistence/memory/KeyGenConfiguration.kt`:

```kotlin
package com.demo.chat.config.persistence.memory

import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdConfiguration
import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.UUIDKeyGenerator
import com.demo.chat.service.core.IKeyGenerator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.util.*

// One key generator per deployment. The selector that picks the key
// backend picks its generator too, so exactly one of these registers.
// Ungated they all registered, and two of them share this simple class
// name - a classpath carrying both failed to start on a conflicting bean
// definition rather than on anything meaningful. Memory keeps matchIfMissing so an unset selector still boots.
@Configuration("memoryKeyGenConfiguration")
@ConditionalOnProperty(prefix = "app.service.core", name = ["key"], havingValue = "memory", matchIfMissing = true)
@Import(NodeIdConfiguration::class)
class KeyGenConfiguration {

    @ConditionalOnProperty("app.key.type", havingValue = "uuid")
    @Bean("KeyGenerator")
    fun uuidGenerator(nodeId: NodeId): IKeyGenerator<UUID> = UUIDKeyGenerator(nodeId.value)

    @ConditionalOnProperty("app.key.type", havingValue = "long")
    @Bean("KeyGenerator")
    fun longGenerator(nodeId: NodeId): IKeyGenerator<Long> = LongKeyGenerator(nodeId.value)
}
```

- [ ] **Step 7: Fix the test that calls the deleted constructor**

`chat-core/src/test/kotlin/com/demo/chat/test/domain/SnowflakeGeneratorTests.kt` calls the
no-argument constructor. Change this line:

```kotlin
        val generator = SnowflakeGenerator()
```

to:

```kotlin
        val generator = SnowflakeGenerator(1)
```

- [ ] **Step 8: Build chat-core and read the failures**

Run: `mvn -o -pl chat-core clean test`
Expected: PASS. Any other test that builds a generator directly fails to compile. Fix each by
passing an explicit node id, for example `LongKeyGenerator(1)`. Do not add a default anywhere.

- [ ] **Step 9: Compile the three backend modules**

This task changes three persistence modules. A `chat-core` run does not compile them, so a
broken `KeyGenConfiguration` stays hidden until much later.

Run: `mvn -o -pl chat-persistence-memory,chat-persistence-redis,chat-persistence-cassandra -am test-compile`
Expected: BUILD SUCCESS. A failure here means a `KeyGenConfiguration` still reads `@Value`, or
still passes a raw `String` where an `Int` is now required.

- [ ] **Step 10: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/SnowflakeGenerator.kt \
        chat-core/src/main/kotlin/com/demo/chat/service/LongKeyGenerator.kt \
        chat-core/src/main/kotlin/com/demo/chat/service/UUIDKeyGenerator.kt \
        chat-core/src/test/kotlin/com/demo/chat/test/domain/SnowflakeGeneratorTests.kt \
        chat-persistence-cassandra/src/main/kotlin/com/demo/chat/config/persistence/cassandra/KeyGenConfiguration.kt \
        chat-persistence-redis/src/main/kotlin/com/demo/chat/config/persistence/redis/KeyGenConfiguration.kt \
        chat-persistence-memory/src/main/kotlin/com/demo/chat/config/persistence/memory/KeyGenConfiguration.kt
git commit -m "require an explicit node id and delete the mac derivation"
```

---

### Task 3: The launch surface

**Files:**
- Modify: `shell-scripts/chat-build`
- Modify: `shell-scripts/test-flags.sh`
- Modify: `shell-scripts/golden/*.flags`, 15 files, by regeneration only

**Interfaces:**
- Consumes: the runtime contract from Task 2.
- Produces: a required `--node-id ID` argument on `chat-build`, and a `-Dapp.nodeid=ID` flag in every generated launch.

- [ ] **Step 1: Add the argument**

In `shell-scripts/chat-build`, inside `add_common_args` near line 852, add:

```python
    parser.add_argument("--node-id", metavar="ID", required=True, type=node_id_value,
                        help="node id for key generation, an integer in 0..1023. "
                             "It must be unique across every deployment that writes to one store.")
```

Add this helper above `add_common_args`:

```python
def node_id_value(raw: str) -> int:
    """Parse --node-id. There is no default and no derivation."""
    try:
        value = int(raw)
    except ValueError:
        raise argparse.ArgumentTypeError(
            f"node id must be an integer in 0..1023, got {raw!r}")
    if value < 0 or value > 1023:
        raise argparse.ArgumentTypeError(
            f"node id must be an integer in 0..1023, got {value}")
    return value
```

- [ ] **Step 2: Carry the value onto the launch plan**

In `BuildContext.__init__` near line 477, directly below the `self.key_type` line, add:

```python
        self.node_id = args.node_id
```

- [ ] **Step 3: Emit the flag**

In `main_flags` near line 591, add the new flag directly below the key type flag:

```python
            f"-Dapp.key.type={self.key_type}",
            f"-Dapp.nodeid={self.node_id}",
```

- [ ] **Step 4: Pass the argument in every golden case**

In `shell-scripts/test-flags.sh`, append ` --node-id 0` to the arguments of all 15 entries in
`CASES`. Use 0, which is a legal explicit value, so golden output stays the same on every
machine. The result reads:

```bash
CASES=(
  # core backends
  "core-memory-init|core --memory --run --notls --long --init users,rootkeys --node-id 0"          # [parity]
  "core-memory-consul|core --memory --consul --run --notls --long --init users,rootkeys --node-id 0" # [parity]
  "core-memory-tls|core --memory --run --tls /etc/keys --long --init users,rootkeys --node-id 0"   # [parity]
  "core-cassandra|core --cassandra --run --notls --long --init users,rootkeys --node-id 0"
  "core-kafka|core --kafka --run --notls --long --node-id 0"
  "core-redis|core --redis --run --notls --long --node-id 0"
  "core-e2ee|core --memory --e2ee --run --notls --long --node-id 0"
  # core variants
  "core-uuid|core --memory --run --notls --uuid --node-id 0"
  "core-websocket|core --memory --websocket --run --notls --long --node-id 0"
  "core-debug|core --memory --debug --run --notls --long --node-id 0"
  "core-build-image|core --memory --build --notls --long --node-id 0"
  # other services
  "rest-client|rest --run --notls --long --node-id 0"
  "gateway-client|gateway --run --notls --long --node-id 0"
  "authserv-client|authserv --run --notls --long --node-id 0"
  "shell-client|shell --run --notls --long --node-id 0"                                            # [parity]
)
```

- [ ] **Step 5: Confirm the goldens fail before regenerating**

Run: `./shell-scripts/test-flags.sh`
Expected: FAIL. Every case reports one added line, `-Dapp.nodeid=0`. Read the diff and confirm
that this is the only change. A case that differs in any other flag is a bug in Steps 1 to 3.

- [ ] **Step 6: Regenerate the goldens**

Run: `./shell-scripts/test-flags.sh --update`
Then run: `./shell-scripts/test-flags.sh`
Expected: PASS, 15 cases.

- [ ] **Step 7: Commit, and explain the regeneration**

`shell-scripts/README-chat-build.md:243` says a diff from `--update` is a bug until you can say
why it is not. The commit message must give that reason and must name the four cases that carry
authority from the removed `test-parity.sh`.

```bash
git add shell-scripts/chat-build shell-scripts/test-flags.sh shell-scripts/golden
git commit -m "emit app.nodeid from chat-build and regenerate the goldens" -m "chat-build gains a required --node-id argument and emits -Dapp.nodeid.
All 15 goldens gain exactly one line, -Dapp.nodeid=0. No other flag moved.

Four goldens carry authority from the removed test-parity.sh: core-memory-init,
core-memory-consul, core-memory-tls, and shell-client. The case table marks them
[parity]. Their regeneration is expected here, because every launch gains the
same single flag."
```

---

### Task 4: Sweep the remaining deployments and tests

**Files:**
- Modify: test sources and resources named by the build, across modules
- Modify: `application*.yml` files for deployments that activate a key generator

**Interfaces:**
- Consumes: everything from Tasks 1 to 3.
- Produces: a green build.

- [ ] **Step 1: Run the whole build and collect the failures**

Run: `mvn -o clean install -DskipTests`
Then run: `./shell-scripts/build-health.sh`

The set of tests that need `app.nodeid` cannot be read from the source, because
`matchIfMissing = true` on the memory configuration makes it a classpath question. Let the
build name them.

- [ ] **Step 2: Add the property to each failing test**

A failing test reports the message from `NodeId`. It contains `app.nodeid is required`.

For a test that already declares properties, add the entry. For example, in
`chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/repository/LongKeyspaceAppTests.kt`:

```kotlin
@TestPropertySource(properties = ["app.service.core.key=cassandra", "app.key.type=long", "app.nodeid=1"])
```

For a test that declares properties inside `@SpringBootTest`, add the entry to that list. For
example, in `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MemoryDeploymentTests.kt`:

```kotlin
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
```

Use `app.nodeid=1` in tests. Do not use 0, so that a test never passes by accident when a
default creeps back.

- [ ] **Step 3: Add the property to deployment configuration**

For each `application*.yml` that belongs to a deployment activating a key generator, add:

```yaml
app:
  nodeid: 1
```

Merge into the existing `app:` block when one is present. A deployment that runs more than one
instance against one store must override this per instance. Record that in the commit message.

- [ ] **Step 4: Run the whole build again**

Run: `./shell-scripts/build-health.sh`
Expected: green. Repeat Steps 2 and 3 until nothing reports `app.nodeid is required`.

- [ ] **Step 5: Review what changed, then stage only those files**

Run: `git status --short`

Read that list and stage each file by name. Do not use `git add -A`. This worktree can hold
scratch files, and files from other work, and a broad stage sweeps them into the commit.

The file set comes from the build in Steps 1 to 4, so it is known by the time you reach this
step. Stage the test sources the build named, and the `application*.yml` files you edited, and
nothing else.

```bash
git status --short
git add <each file named above>
git commit -m "set an explicit node id across tests and deployment configuration"
```

---

### Task 5: Record the change

**Files:**
- Modify: `forward-register.md`

- [ ] **Step 1: Add the entry**

Add to "Things worth not relearning":

```markdown
- **`app.nodeid` is required and has no default.** It takes an integer in 0..1023, and
  0 is a legal explicit value. The MAC derivation is gone, because it collided for
  certain under containers that share an IP. `chat-build` requires `--node-id`.
  Uniqueness across deployments is not enforced yet. That is `CHAT-wyssrokr`.
```

- [ ] **Step 2: Commit**

```bash
git add forward-register.md
git commit -m "record the nodeid contract in the forward register"
```

## Self-review notes

Spec coverage checked section by section. The contract, the components, the launch surface, the
failure mode, the testing list, and the migration all map to a task. The "Verified facts" and
"Correction carried into stage 2" sections carry no implementation work.

Two spec items deserve care during execution:

1. The context tests named in the spec are Task 1, Steps 6 and 7. They run against
   `NodeIdConfiguration` directly with a `MockEnvironment`, so they need no deployment and no
   scan configuration. Task 4 then proves the same contract from real deployments.
2. The spec requires that `NodeIdConfiguration` never becomes global. Task 1 places it outside
   every scanned package, and the comment in the file records why. If a later change moves it
   into `com.demo.chat.config`, key-less processes start failing on a missing `app.nodeid`.

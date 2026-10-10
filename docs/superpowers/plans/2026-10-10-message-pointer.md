# Message Pointer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline in one session. `AGENTS.md` forbids subagent-driven development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `PointerMessage`, a message that names one target message or topic, with a `FORWARD` permission, a room-based `messageById` rule, storage in every persistence backend, and readers in the shell and MCP.

**Architecture:** A pointer is a `Message<T, String>` with empty `data` and a `target: Key<T>`. It enters through a new `pointer` operation on `ChatMessageService`, passes the command bus as `RECORD_POINTER` with no vector obligation, and each store tells it apart by type. A new port, `PointerTargetAccess`, lets the composite check the caller's right to read the target without a security dependency.

**Tech Stack:** Kotlin 2.4, Spring Boot 4, Reactor, Jackson 2 (Redis) and Jackson 3 (wire), Spring Security reactive method security, Cassandra, Lucene, JUnit 5, AssertJ, StepVerifier.

**Spec:** `docs/superpowers/specs/2026-10-10-message-pointer-design.md`

**Issue:** `CHAT-erkoxybm`. Branch: `chat-erkoxybm-message-pointer`.

## Global Constraints

- The message value type `V` stays `String`. No service signature, bean type, or injection point changes its type arguments.
- `PointerMessage.data` is always `""`. Code tells a pointer apart by type, never by `data`.
- A target root is the `Message` root or the `MessageTopic` root. No other root is legal.
- `FORWARD` is a permission name. It is not a domain and has no root key.
- A missing target and an unreadable target give the same error text: `no such message` or `no such topic`.
- A pointer command has the obligations `P`, `I`, `U`. It never has `V`.
- No reader fetches a target.
- Tests cover the persistence stores, the creation checks, the `messageById` rule, and one MCP decoder test for a history that holds a pointer. Do not add tests for routes, indexes, or the shell beyond the existing suites.
- `git add` names exact files. Never stage a directory.
- Push and pull request steps need the owner's approval at that time. No step pushes on its own.
- Kotlin is the language. Match the comment density and naming of the file you edit.
- Agent-authored prose (comments, docs, commits) follows ASD-STE100 strict mode from `AGENTS.md`.
- One Maven build at a time in this checkout. A concurrent `clean` deletes the other build's output.
- Write Maven output to a log file in the scratchpad. Read the exit code and the summary lines only.
- A scoped `-pl` run resolves upstream modules from `~/.m2`. Always add `-am` when a test needs changed upstream code.
- Container tests need `-Pintegration` and a running Docker.
- Every commit message names `CHAT-erkoxybm` and ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Log each finished task with `fp comment CHAT-erkoxybm "..."`.
- Restore every mutation by absolute path, and prove the restore with `git status --short`.

Set this once per shell before running Maven:

```bash
LOG=/private/tmp/claude-501/-Users-darkbit1001-workspace-demo-chat/27bc1f11-c75e-46a3-bee3-4cf8f91baee2/scratchpad/mvn.log
```

## Review Focus

These inputs are not exercised by any task's tests. Each one is likely to hurt a user. Each line says what a user should expect.

1. **A message target that a store marks as removed.** Cassandra `rem` sets `visible` to false and keeps the row. Expect such a message to be refused as missing. Task 8 refuses a target with `record == false`, and no test covers it, because the memory store deletes the row.
2. **A pointer read back through Redis pub/sub or Kafka.** The codecs use Jackson 2 polymorphic reads. Expect a `PointerMessage`, not a decode error. Task 2 registers the deserializer that those codecs use.
3. **A target id sent as a JSON number that fits in an `Int`.** Jackson reads it as `Integer` when `T` is erased. Expect the composite to convert it exactly to `Long`. Task 8 converts every target id with `TypeUtil.exactFrom`.
4. **A text message read back from Cassandra after this change.** Expect a text message with its text, not a pointer. Task 3 tests a text row with a null `kind`.
5. **A `listen` in the shell that receives a pointer.** Expect a pointer line, not an empty message. Task 10 changes the listen output.

---

## File Map

| File | Task | Change |
|---|---|---|
| `chat-core/src/main/kotlin/com/demo/chat/domain/Message.kt` | 1 | `PointerMessage`, subtype entry |
| `chat-core/src/main/kotlin/com/demo/chat/domain/RequestResponse.kt` | 1 | `MessagePointerRequest` |
| `chat-core/src/test/kotlin/com/demo/chat/test/persistence/PointerStoreTestBase.kt` | 1 | New shared round-trip test |
| `chat-persistence-memory/src/test/kotlin/com/demo/chat/test/persistence/MemoryStoreDomainTests.kt` | 1 | Memory pointer test class |
| `chat-core/src/main/kotlin/com/demo/chat/domain/serializers/ChatDeserializers.kt` | 2 | Jackson 2 pointer deserializer |
| `chat-core/src/main/kotlin/com/demo/chat/domain/serializers/JacksonModules.kt` | 2 | Register it |
| `chat-core/src/main/kotlin/com/demo/chat/domain/serializers/ChatJackson3Deserializers.kt` | 2 | Jackson 3 pointer deserializer |
| `chat-core/src/main/kotlin/com/demo/chat/config/ChatJackson3Modules.kt` | 2 | Register it |
| `chat-persistence-redis/src/test/kotlin/com/demo/chat/test/persistence/redis/RedisPointerStoreTests.kt` | 2 | New Redis test |
| `shared-resources-cassandra/src/main/resources/keyspace-long.cql`, `keyspace-uuid.cql` | 3 | `kind text` |
| `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/domain/Message.kt` | 3 | `kind` field |
| `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/MessagePersistenceCassandra.kt` | 3 | Write and read pointers |
| `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/CassandraPersistenceServices.kt` | 3 | Pass `TypeUtil` |
| `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/config/persistence/cassandra/CorePersistenceServices.kt` | 3 | Inject `TypeUtil` |
| `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraStoreShapeCheck.kt` | 3 | Require `kind` |
| `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/CassandraStoreDomainTests.kt` | 3 | New args, pointer tests |
| `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/CassandraStoreShapeCheckTests.kt` | 3 | `kind` refusal case |
| `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/mock/TextMessageServiceTests.kt` | 3 | New constructor arg |
| `chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/domain/IndexEntryEncoder.kt` | 4 | Omit `data` for a pointer |
| `chat-index-cassandra/src/main/kotlin/com/demo/chat/index/cassandra/domain/Message.kt` | 4 | Nullable `data` |
| `chat-index-cassandra/src/main/kotlin/com/demo/chat/index/cassandra/impl/MessageIndex.kt` | 4 | Null text for a pointer |
| `chat-core/src/main/kotlin/com/demo/chat/domain/command/CommandTypes.kt` | 5 | `RECORD_POINTER`, `target` |
| `chat-core/src/main/kotlin/com/demo/chat/service/command/CommandFingerprint.kt` | 5 | Version 3 with target |
| `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/MemoryDomainCommandBus.kt` | 5 | Build pointer, drop `V` |
| `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageReindexServiceImpl.kt` | 5 | Skip pointers |
| `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/handler/MessageCommandHandlers.kt` | 5 | Declare `RECORD_POINTER` on `P`, `I`, `U` |
| `chat-core/src/main/kotlin/com/demo/chat/service/security/policy/ShippedGrantPolicies.kt` | 6 | `FORWARD` |
| `chat-security/src/test/kotlin/com/demo/chat/test/MembershipGrantTests.kt`, `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/RoomMemberGrantWiringTests.kt` | 6 | Row sets gain `FORWARD` |
| `chat-security/src/main/kotlin/com/demo/chat/security/access/composite/MessageServiceAccess.kt` | 7, 8 | `@PostAuthorize`, `pointer` check |
| `chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/composite/mapping/ChatMessageServiceRestMapping.kt` | 7, 9 | `@PostAuthorize`, REST route |
| `shared-deploy-configuration/src/main/config/userinit.yml` | 7 | Remove `{Anon, Message, GET}` |
| `chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt` | 7 | Matrix row |
| `chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraAuthorizationMatrixTests.kt` | 7 | Matrix row |
| `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageRestAccessTests.kt` | 7 | Drop `/message/id` from the deny-all test |
| `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageByIdRestRuleTests.kt` | 7 | New REST rule test |
| `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/DeploymentCallers.kt` | 7 | New shared test helper |
| `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MessageByIdRoomRuleTests.kt` | 7 | New RSocket rule test |
| `docs/ANONYMOUS-AUTHORIZATION.md` | 7, 8 | Rule, matrix rows |
| `chat-core/src/main/kotlin/com/demo/chat/domain/PointerExceptions.kt` | 8 | New exceptions |
| `chat-core/src/main/kotlin/com/demo/chat/service/command/PointerTargetAccess.kt` | 8 | New port |
| `chat-core/src/main/kotlin/com/demo/chat/service/composite/ChatMessageService.kt` | 8 | `pointer` |
| `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessagingServiceImpl.kt` | 8 | `pointer` |
| `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/access/MessagingServiceAccess.kt` | 8 | `pointer` delegate |
| `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/CompositeServiceBeansConfiguration.kt` | 8 | Wire the port |
| `chat-security/src/main/kotlin/com/demo/chat/security/access/ContextPointerTargetAccess.kt` | 8 | New port implementation |
| `chat-security/src/main/kotlin/com/demo/chat/config/auth/PointerTargetAccessConfiguration.kt` | 8 | New configuration |
| `chat-service-controller/src/main/kotlin/com/demo/chat/controller/composite/mapping/MessageServiceControllerMapping.kt` | 8 | RSocket route |
| `chat-client-rsocket/src/main/kotlin/com/demo/chat/client/rsocket/clients/composite/MessagingClient.kt` | 8 | Client method |
| `chat-service-controller/src/test/kotlin/com/demo/chat/test/resolve/RSocketRouteCatalog.kt` | 8 | Catalog entry |
| `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MessagePointerCreationTests.kt` | 8 | New creation test |
| `chat-webflux/src/main/kotlin/com/demo/chat/config/WebFluxKeyVerifierConfiguration.kt` | 9 | Error handlers |
| `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/route/RestRouteCatalog.kt` | 9 | Catalog entry |
| `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommands.kt` | 10 | Pointer lines |
| `chat-mcp/src/main/kotlin/com/demo/chat/mcp/client/MessageEnvelope.kt` | 10 | Decode pointers |
| `chat-mcp/src/main/kotlin/com/demo/chat/mcp/tool/MessagingSchemas.kt` | 10 | Pointer item shape |
| `chat-mcp/src/test/kotlin/com/demo/chat/mcp/client/MessageEnvelopeTests.kt` | 10 | History with a pointer |
| `docs/MESSAGE-COMMANDS.md`, `docs/MCP-ADAPTER.md`, `forward-register.md` | 10, 11 | Docs |

---

### Task 1: The domain type and the memory round trip

**Files:**
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/Message.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/RequestResponse.kt`
- Create: `chat-core/src/test/kotlin/com/demo/chat/test/persistence/PointerStoreTestBase.kt`
- Modify: `chat-persistence-memory/src/test/kotlin/com/demo/chat/test/persistence/MemoryStoreDomainTests.kt`

**Interfaces:**
- Produces: `PointerMessage<T>(key: MessageKey<T>, target: Key<T>) : Message<T, String>`, `MessagePointerRequest<T>(dest: T, targetId: T, targetDomain: String, requestId: String)`, `abstract class PointerStoreTestBase<T : Any>(store: MessagePersistence<T, String>, keys: IKeyService<T>, rootKeys: RootKeys<T>)`.

- [ ] **Step 1: Write the shared round-trip test base**

Create `chat-core/src/test/kotlin/com/demo/chat/test/persistence/PointerStoreTestBase.kt`:

```kotlin
package com.demo.chat.test.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.PointerMessage
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.MessagePersistence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * A message store keeps a pointer and reads it back as a pointer. See
 * `CHAT-erkoxybm`.
 *
 * A backend subclasses this once. [keys] mints under [rootKeys], and [store]
 * reads the same roots.
 */
@Disabled
abstract class PointerStoreTestBase<T : Any>(
    val store: MessagePersistence<T, String>,
    val keys: IKeyService<T>,
    val rootKeys: RootKeys<T>,
) {
    @Test
    fun `a pointer to a message survives a write and a read`() = roundTrip(ChatDomain.MESSAGE)

    @Test
    fun `a pointer to a topic survives a write and a read`() = roundTrip(ChatDomain.MESSAGE_TOPIC)

    /** The pointer is the only stored form of a pointer. Its type carries the meaning. */
    private fun roundTrip(domain: ChatDomain) {
        val written = pointerTo(domain)
        store.add(written).block()

        val read = store.get(Key.of(written.key.id, written.key.root)).block()

        assertThat(read).describedAs("the stored pointer").isInstanceOf(PointerMessage::class.java)
        @Suppress("UNCHECKED_CAST")
        val pointer = read as PointerMessage<T>
        assertThat(pointer.target).describedAs("the target key").isEqualTo(written.target)
        assertThat(pointer.target.root).describedAs("the target root").isEqualTo(rootKeys.of(domain).id)
        assertThat(pointer.key.from).describedAs("the creator").isEqualTo(written.key.from)
        assertThat(pointer.key.dest).describedAs("the host room").isEqualTo(written.key.dest)
        assertThat(pointer.key.timestamp).describedAs("the time").isEqualTo(written.key.timestamp)
        assertThat(pointer.data).describedAs("the pointer data").isEmpty()
        assertThat(pointer.record).describedAs("the record flag").isTrue()
    }

    private fun pointerTo(domain: ChatDomain): PointerMessage<T> {
        val own = keys.key(ChatDomain.MESSAGE).block()!!
        val creator = keys.key(ChatDomain.USER).block()!!
        val host = keys.key(ChatDomain.MESSAGE_TOPIC).block()!!
        val target = keys.key(domain).block()!!
        // Cassandra keeps milliseconds, so the time carries no finer part.
        val at = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        return PointerMessage(SimpleMessageKey(own.id, own.root, creator.id, host.id, at), Key.of(target.id, target.root))
    }
}
```

- [ ] **Step 2: Add the memory subclass**

Append to `chat-persistence-memory/src/test/kotlin/com/demo/chat/test/persistence/MemoryStoreDomainTests.kt`:

```kotlin
/** The memory store holds the object, so a pointer reads back unchanged. See `CHAT-erkoxybm`. */
class MemoryPointerStoreTests : PointerStoreTestBase<Long>(
    MessagePersistenceInMemory(KEYS, ROOTS) { it.key }, KEYS, ROOTS,
)
```

`KEYS` and `ROOTS` are private to that file, so the class goes in that file.

- [ ] **Step 3: Run the test to verify it fails**

```bash
mvn -o -B -pl chat-core,chat-persistence-memory -am test -Dtest=MemoryPointerStoreTests -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "ERROR.*Unresolved|BUILD" $LOG | head
```

Expected: exit 1, with `Unresolved reference 'PointerMessage'`.

- [ ] **Step 4: Add `PointerMessage`**

In `chat-core/src/main/kotlin/com/demo/chat/domain/Message.kt`, replace the `@JsonSubTypes` block on `Message` with:

```kotlin
@JsonSubTypes(
    JsonSubTypes.Type(value = JoinAlert::class, name = "JoinAlert"),
    JsonSubTypes.Type(value = LeaveAlert::class, name = "LeaveAlert"),
    JsonSubTypes.Type(value = PointerMessage::class, name = "PointerMessage"),
)
```

Append to the same file:

```kotlin
/**
 * A message that names one target message or topic. See `CHAT-erkoxybm`.
 *
 * `key.from` is the creator and `key.dest` is the host room. [data] is always
 * empty. A reader tells a pointer apart by its type, never by its data. The
 * target root is the `Message` root or the `MessageTopic` root.
 */
@JsonTypeName("PointerMessage")
data class PointerMessage<T>(override val key: MessageKey<T>, val target: Key<T>) : Message<T, String> {
    override val data: String get() = ""
    override val record: Boolean get() = true
}
```

- [ ] **Step 5: Add `MessagePointerRequest`**

In `chat-core/src/main/kotlin/com/demo/chat/domain/RequestResponse.kt`, after `MessageSubmitRequest`, add:

```kotlin
/**
 * A request to post a pointer into room [dest]. See `CHAT-erkoxybm`.
 *
 * [targetDomain] is `Message` or `MessageTopic`. The request carries no
 * creator. The server binds the creator to the authenticated user.
 */
@JsonTypeName("MessagePointerRequest")
data class MessagePointerRequest<T>(
    val dest: T,
    val targetId: T,
    val targetDomain: String,
    val requestId: String,
) : RequestResponse<T>()
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
mvn -o -B -pl chat-core,chat-persistence-memory -am test -Dtest=MemoryPointerStoreTests -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|BUILD" $LOG | tail -3
```

Expected: exit 0, `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 7: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/Message.kt chat-core/src/main/kotlin/com/demo/chat/domain/RequestResponse.kt chat-core/src/test/kotlin/com/demo/chat/test/persistence/PointerStoreTestBase.kt chat-persistence-memory/src/test/kotlin/com/demo/chat/test/persistence/MemoryStoreDomainTests.kt
git commit -m "Add the pointer message type (CHAT-erkoxybm)

A pointer is a Message<T, String> with empty data and a target key. The
memory store keeps it and reads it back unchanged.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
fp comment CHAT-erkoxybm "Task 1 done: PointerMessage, MessagePointerRequest, memory round trip."
```

---

### Task 2: Jackson reads a pointer, and the Redis round trip

**Files:**
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/serializers/ChatDeserializers.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/serializers/JacksonModules.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/serializers/ChatJackson3Deserializers.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/config/ChatJackson3Modules.kt`
- Create: `chat-persistence-redis/src/test/kotlin/com/demo/chat/test/persistence/redis/RedisPointerStoreTests.kt`

**Interfaces:**
- Consumes: `PointerMessage`, `PointerStoreTestBase` from Task 1.
- Produces: `PointerMessageDeserializer<T>` (Jackson 2) and `Jackson3PointerMessageDeserializer<T : Any>` (Jackson 3).

A pointer serializes as `{"PointerMessage": {"key": {"key": {...}}, "data": "", "record": true, "target": {"key": {"id": ..., "root": ..., "empty": false}}}}`. Each mapper resolves the type id `PointerMessage` to the class `PointerMessage`, and then looks up a deserializer for that exact class. So each module registers one.

- [ ] **Step 1: Write the Redis test**

Create `chat-persistence-redis/src/test/kotlin/com/demo/chat/test/persistence/redis/RedisPointerStoreTests.kt`:

```kotlin
package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.persistence.redis.impl.KeyServiceRedis
import com.demo.chat.persistence.redis.impl.MessagePersistenceRedis
import com.demo.chat.test.persistence.PointerStoreTestBase
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.UUID

/** The Redis store writes a pointer as JSON and reads it back through Jackson. See `CHAT-erkoxybm`. */
@ExtendWith(SpringExtension::class)
@Import(RedisPersistenceTestContext::class, RedisPersistenceTestBeans::class)
@Tag("integration")
class RedisPointerStoreTests @Autowired constructor(
    store: MessagePersistenceRedis<UUID, String>,
    keys: KeyServiceRedis<UUID>,
    rootKeys: RootKeys<UUID>,
) : PointerStoreTestBase<UUID>(store, keys, rootKeys) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
mvn -B -Pintegration -pl chat-persistence-redis -am test -Dtest=RedisPointerStoreTests -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|FAIL|Exception" $LOG | head -8
```

Expected: exit 1. Both tests fail on the read. The message names a Jackson problem with `PointerMessage`, for example a missing creator or an unknown property. If a test passes instead, stop and report it. That would mean the plan misread the Jackson setup.

- [ ] **Step 3: Add the Jackson 2 deserializer**

Append to `chat-core/src/main/kotlin/com/demo/chat/domain/serializers/ChatDeserializers.kt`:

```kotlin
/**
 * A pointer. The message key and the target key each sit inside their own
 * wrapper. See `CHAT-erkoxybm`.
 */
class PointerMessageDeserializer<T>(keyCodec: Converter<JsonNode, T>) : JsonDeserializer<PointerMessage<T>>() {
    private val kd = KeyDeserializer(keyCodec)

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): PointerMessage<T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = readNode(jp, oc)

        val key = kd.deserialize(node.get("key").first().traverse(oc), ctxt)
        val target = kd.deserialize(node.get("target").first().traverse(oc), ctxt)

        return when (key) {
            is MessageKey<T> -> PointerMessage(key, target)
            else -> throw ChatException("Invalid Message Key")
        }
    }
}
```

In `JacksonModules.messageModule()`, add a second line:

```kotlin
    @Bean
    open fun messageModule() = SimpleModule("MessageModule", Version.unknownVersion()).apply {
        addDeserializer(Message::class.java, MessageDeserializer(keyConverter, dataConverter))
        addDeserializer(PointerMessage::class.java, PointerMessageDeserializer(keyConverter))
    }
```

Add `import com.demo.chat.domain.PointerMessage` if the file imports domain types one by one.

- [ ] **Step 4: Add the Jackson 3 deserializer**

Append to `chat-core/src/main/kotlin/com/demo/chat/domain/serializers/ChatJackson3Deserializers.kt`:

```kotlin
/** A pointer. See `PointerMessageDeserializer` and `CHAT-erkoxybm`. */
class Jackson3PointerMessageDeserializer<T : Any> : ValueDeserializer<PointerMessage<T>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PointerMessage<T> {
        val node: JsonNode = readTimestampTree(p, ctxt)

        val key = Jackson3NodeToAny.key<T>(node.get("key").values().first(), ctxt)
        val target = Jackson3NodeToAny.key<T>(node.get("target").values().first(), ctxt)

        return when (key) {
            is MessageKey<T> -> PointerMessage(key, target)
            else -> throw ChatException("Invalid Message Key")
        }
    }
}
```

Add `import com.demo.chat.domain.PointerMessage` at the top of that file.

In `ChatJackson3Modules.chatJackson3Module()`, after the `Message` line, add:

```kotlin
        module.addDeserializer(PointerMessage::class.java, Jackson3PointerMessageDeserializer<Any>())
```

Add `import com.demo.chat.domain.PointerMessage` and `import com.demo.chat.domain.serializers.Jackson3PointerMessageDeserializer`.

- [ ] **Step 5: Run the test to verify it passes**

```bash
mvn -B -Pintegration -pl chat-persistence-redis -am test -Dtest=RedisPointerStoreTests,RedisMessagePersistenceTests -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|BUILD" $LOG | tail -4
```

Expected: exit 0. `RedisPointerStoreTests` runs 2 tests, and `RedisMessagePersistenceTests` still passes.

- [ ] **Step 6: Run the chat-core suite**

```bash
mvn -o -B -pl chat-core test > $LOG 2>&1; echo exit=$?; grep -E "Tests run:.*Fail|BUILD" $LOG | tail -2
```

Expected: exit 0. `DomainWireShapeTests` and the deserializer tests still pass.

- [ ] **Step 7: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/serializers/ChatDeserializers.kt \
  chat-core/src/main/kotlin/com/demo/chat/domain/serializers/JacksonModules.kt \
  chat-core/src/main/kotlin/com/demo/chat/domain/serializers/ChatJackson3Deserializers.kt \
  chat-core/src/main/kotlin/com/demo/chat/config/ChatJackson3Modules.kt \
  chat-persistence-redis/src/test/kotlin/com/demo/chat/test/persistence/redis/RedisPointerStoreTests.kt
git commit -m "Read a pointer with Jackson 2 and Jackson 3 (CHAT-erkoxybm)

Each mapper resolves the PointerMessage type id to its own deserializer.
The Redis store reads a pointer back as a pointer.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
fp comment CHAT-erkoxybm "Task 2 done: Jackson 2 and 3 pointer deserializers, Redis round trip."
```

---

### Task 3: The Cassandra `kind` column

**Files:**
- Modify: `shared-resources-cassandra/src/main/resources/keyspace-long.cql`
- Modify: `shared-resources-cassandra/src/main/resources/keyspace-uuid.cql`
- Modify: `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/domain/Message.kt`
- Modify: `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/MessagePersistenceCassandra.kt`
- Modify: `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/CassandraPersistenceServices.kt`
- Modify: `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/config/persistence/cassandra/CorePersistenceServices.kt`
- Modify: `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraStoreShapeCheck.kt`
- Modify: `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/CassandraStoreDomainTests.kt`
- Modify: `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/CassandraStoreShapeCheckTests.kt`
- Modify: `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/mock/TextMessageServiceTests.kt`

**Interfaces:**
- Consumes: `PointerMessage`, `PointerStoreTestBase`.
- Produces: `MessagePersistenceCassandra<T : Any>(keyService, rootKeys, messageRepo, typeUtil: TypeUtil<T>)`. `CassandraPersistenceServices` gains the parameter `typeUtil: TypeUtil<T>` after `keyValueRepo`.

- [ ] **Step 1: Write the Cassandra tests**

In `CassandraStoreDomainTests.kt`, add the imports `com.demo.chat.domain.PointerMessage`, `com.demo.chat.domain.TypeUtil`, `com.demo.chat.domain.UUIDUtil`, `com.demo.chat.test.TestLongKeyGenerator`, `com.demo.chat.test.persistence.PointerStoreTestBase`, and `org.assertj.core.api.Assertions.assertThatThrownBy`. Change the two existing `MessagePersistenceCassandra(...)` calls to pass `UUIDUtil()` as the fourth argument. Then append:

```kotlin
private val LONG_ROOTS = FakeKeyServices.longRoots()

private fun longKeys(template: ReactiveCassandraTemplate) = KeyServiceCassandra(template, TestLongKeyGenerator(), LONG_ROOTS)

/** A pointer round trip on a uuid keyspace. See `CHAT-erkoxybm`. */
@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [RepositoryTestConfiguration::class])
@TestPropertySource(properties = ["app.key.type=uuid"])
@Tag("integration")
class CassandraUuidPointerStoreTests @Autowired constructor(template: ReactiveCassandraTemplate, repo: ChatMessageRepository<UUID>) :
    PointerStoreTestBase<UUID>(MessagePersistenceCassandra(keys(template), ROOTS, repo, UUIDUtil()), keys(template), ROOTS)

/** A pointer round trip on a long keyspace. See `CHAT-erkoxybm`. */
@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [RepositoryTestConfiguration::class])
@TestPropertySource(properties = ["app.key.type=long"])
@Tag("integration")
class CassandraLongPointerStoreTests @Autowired constructor(
    private val template: ReactiveCassandraTemplate,
    private val repo: ChatMessageRepository<Long>,
) : PointerStoreTestBase<Long>(MessagePersistenceCassandra(longKeys(template), LONG_ROOTS, repo, TypeUtil.LongUtil), longKeys(template), LONG_ROOTS) {

    /** A text row carries a null kind and reads back as text. */
    @Test
    fun `a text message reads back as text`() {
        val own = keys.key(ChatDomain.MESSAGE).block()!!
        val text = Message.create(MessageKey.of(own.id, own.root, 1L, 2L), "plain text", true)
        store.add(text).block()

        val read = store.get(Key.of(own.id, own.root)).block()!!

        assertThat(read).isNotInstanceOf(PointerMessage::class.java)
        assertThat(read.data).isEqualTo("plain text")
    }

    /** A kind that names no target domain fails the read. It never becomes a text message. */
    @Test
    fun `a row with an unknown kind fails the read`() {
        val own = keys.key(ChatDomain.MESSAGE).block()!!
        template.reactiveCqlOperations.execute(
            "INSERT INTO chat_message_id (msg_id, msg_time, user_id, topic_id, text, visible, kind) VALUES (?, ?, ?, ?, ?, ?, ?)",
            own.id, Instant.now().truncatedTo(ChronoUnit.MILLIS), 1L, 2L, "77", true, "User",
        ).block()

        assertThatThrownBy { store.get(Key.of(own.id, own.root)).block() }
            .hasMessageContaining("unknown kind 'User'")
    }
}
```

`keys`, `store` and `rootKeys` are `val` properties of `PointerStoreTestBase`, so the subclass reads them.

In `CassandraStoreShapeCheckTests.kt`, add `"chat_message_id.kind"` to the `@ValueSource` list of `a store without a required element fails at start`.

In `TextMessageServiceTests.kt` line 70, add the key type util as the fourth argument. Read the type of `keyService` in that file: pass `UUIDUtil()` for a `UUID` key service, or `TypeUtil.LongUtil` for a `Long` key service.

- [ ] **Step 2: Run the tests to verify they fail**

```bash
mvn -o -B -pl chat-persistence-cassandra -am test-compile > $LOG 2>&1; echo exit=$?; grep -E "ERROR.*kt" $LOG | head -4
```

Expected: exit 1. The compiler reports too many arguments for `MessagePersistenceCassandra`.

- [ ] **Step 3: Add the column to both keyspace scripts**

In both `keyspace-long.cql` and `keyspace-uuid.cql`, in `CREATE TABLE ... chat_message_id`, add `kind text,` after the `visible Boolean,` line. For the long script the table reads:

```sql
CREATE TABLE chat_long.chat_message_id (
    msg_id BIGINT,
    user_id BIGINT,
    topic_id BIGINT,
    text    varchar,
    msg_time TIMESTAMP,
    visible Boolean,
    kind text,
    PRIMARY KEY (msg_id, msg_time))
WITH CLUSTERING ORDER BY (msg_time DESC);
```

- [ ] **Step 4: Add the field to the row**

In `chat-persistence-cassandra/.../domain/Message.kt`, change `ChatMessageById` to:

```kotlin
@Table("chat_message_id")
data class ChatMessageById<T>(
    @PrimaryKey val key: ChatMessageByIdKey<T>,
    @field:Column("text") val data: String,
    @field:Column("visible") val record: Boolean,
    /** Null for a text message. A target domain name for a pointer. See `CHAT-erkoxybm`. */
    @field:Column("kind") val kind: String? = null,
)
```

- [ ] **Step 5: Write and read pointers in the store**

In `MessagePersistenceCassandra.kt`, add the constructor parameter `private val typeUtil: TypeUtil<T>` after `messageRepo`. Add imports `com.demo.chat.domain.ChatException`, `com.demo.chat.domain.PointerMessage`, `com.demo.chat.domain.TypeUtil`. Replace `write` and `message` with:

```kotlin
    /**
     * The row keeps the supplied id, sender, destination and timestamp. The
     * write mints nothing. See `CHAT-avduuqwp`, E20.
     *
     * A pointer stores its target id in `text` and its target domain in
     * `kind`. A text message leaves `kind` null. See `CHAT-erkoxybm`.
     */
    private fun write(ent: Message<T, String>): Mono<Void> =
        messageRepo.add(
            ChatMessageById(
                ChatMessageByIdKey(ent.key.id, ent.key.from, ent.key.dest, ent.key.timestamp),
                if (ent is PointerMessage<T>) typeUtil.toString(ent.target.id) else ent.data,
                ent.record,
                if (ent is PointerMessage<T>) targetDomain(ent.target).wireName else null,
            )
        )

    private fun message(row: ChatMessageById<T>): Message<T, String> {
        val key = SimpleMessageKey(row.key.id, rootKeys.of(ChatDomain.MESSAGE).id, row.key.from, row.key.dest, row.key.timestamp)
        val kind = row.kind ?: return Message.create(key, row.data, row.record)
        val domain = ChatDomain.parse(kind)?.takeIf { it in POINTER_TARGETS }
            ?: throw ChatException("Message ${row.key.id} has the unknown kind '$kind'.")
        return PointerMessage(key, Key.of(typeUtil.exactFrom(row.data), rootKeys.of(domain).id))
    }

    private fun targetDomain(target: Key<T>): ChatDomain =
        rootKeys.domainOfRoot(target.root)?.takeIf { it in POINTER_TARGETS }
            ?: throw ChatException("A pointer target must carry the Message root or the MessageTopic root. The root is ${target.root}.")

    private companion object {
        val POINTER_TARGETS = setOf(ChatDomain.MESSAGE, ChatDomain.MESSAGE_TOPIC)
    }
```

`write` runs inside the `Mono.defer` of `add`, so a refused root becomes an error signal. `message` runs inside `map`, so an unknown kind becomes an error signal.

- [ ] **Step 6: Pass the type util through the wiring**

In `CassandraPersistenceServices.kt`, add the parameter `private val typeUtil: TypeUtil<T>,` after `keyValueRepo`, before `mapper`. Import `com.demo.chat.domain.TypeUtil`. Change the message store line to:

```kotlin
    override fun messagePersistence(): MessagePersistence<T, String> =
        MessagePersistenceCassandra(keyService, rootKeys, messageRepo, typeUtil)
```

In `CorePersistenceServices.kt`, add the constructor parameter `typeUtil: TypeUtil<T>,` after `keyValueRepo`, and pass it in the super call:

```kotlin
) : CassandraPersistenceServices<T>(keyService, rootKeys, userRepo, topicRepo,
    messageRepo, membershipRepo, authmetaRepo, keyValueRepo, typeUtil, mapper) {
```

- [ ] **Step 7: Require the column in the shape check**

In `CassandraStoreShapeCheck.kt`, change `PERSISTENCE_TABLES` to:

```kotlin
        val PERSISTENCE_TABLES: Map<String, Set<String>> = mapOf(
            "auth_metadata" to setOf("principal_root", "target_root"),
            // A pointer row marks its target domain here. See CHAT-erkoxybm.
            "chat_message_id" to setOf("kind"),
        )
```

Do not add `kind` to `persistenceTypes`. The type check of `msg_id` stays the only type check.

- [ ] **Step 8: Run the tests to verify they pass**

```bash
mvn -B -Pintegration -pl chat-persistence-cassandra -am test -Dtest='Cassandra*PointerStoreTests,CassandraStoreShapeCheckTests,CassandraMessageStoreDomainTests,CassandraMessageIdentityTests,TextMessageServiceTests' -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|BUILD" $LOG | tail -8
```

Expected: exit 0. `CassandraUuidPointerStoreTests` runs 2. `CassandraLongPointerStoreTests` runs 4. `CassandraStoreShapeCheckTests` runs one more parameter case than before.

- [ ] **Step 9: Mutation: a lost kind**

Change the fourth argument of `ChatMessageById` in `write` to `null`. Run the Step 8 command. Expected: the four pointer round-trip tests fail on `the stored pointer`, and the other tests pass.

Restore the argument by hand. Do not use `git checkout` on this file, because the Step 5 change is not committed yet and a checkout removes it. Then run `git diff` on the file, and confirm that `write` again passes `targetDomain(ent.target).wireName`.

- [ ] **Step 10: Commit**

```bash
git add shared-resources-cassandra/src/main/resources/keyspace-long.cql \
  shared-resources-cassandra/src/main/resources/keyspace-uuid.cql \
  chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/domain/Message.kt \
  chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/MessagePersistenceCassandra.kt \
  chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/CassandraPersistenceServices.kt \
  chat-persistence-cassandra/src/main/kotlin/com/demo/chat/config/persistence/cassandra/CorePersistenceServices.kt \
  chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraStoreShapeCheck.kt \
  chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/CassandraStoreDomainTests.kt \
  chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/CassandraStoreShapeCheckTests.kt \
  chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/mock/TextMessageServiceTests.kt
git commit -m "Store pointers in Cassandra with a kind column (CHAT-erkoxybm)

A pointer row holds its target id in text and its target domain in kind.
A text row leaves kind null. An unknown kind fails the read. The shape
check requires the column.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
fp comment CHAT-erkoxybm "Task 3 done: Cassandra kind column, long and uuid pointer round trips, shape check."
```

---

### Task 4: The indexes carry no pointer text

**Files:**
- Modify: `chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/domain/IndexEntryEncoder.kt`
- Modify: `chat-index-cassandra/src/main/kotlin/com/demo/chat/index/cassandra/domain/Message.kt`
- Modify: `chat-index-cassandra/src/main/kotlin/com/demo/chat/index/cassandra/impl/MessageIndex.kt`

**Interfaces:**
- Consumes: `PointerMessage`.
- Produces: no new signature.

The owner excluded index tests. The existing index suites are the regression proof.

- [ ] **Step 1: Omit the Lucene data field for a pointer**

In `IndexEntryEncoder.kt`, replace `ofMessage` with:

```kotlin
        /** A pointer carries no text, so it writes no data field. See `CHAT-erkoxybm`. */
        fun <T> ofMessage(): IndexEntryEncoder<Message<T, String>> =
            IndexEntryEncoder { t ->
                listOfNotNull(
                    Pair(MessageIndexService.ID, t.key.id.toString()),
                    Pair(MessageIndexService.DATA, t.data).takeUnless { t is PointerMessage<*> },
                    Pair(MessageIndexService.TOPIC, t.key.dest.toString()),
                    Pair(MessageIndexService.USER, t.key.from.toString()),
                )
            }
```

Import `com.demo.chat.domain.PointerMessage`.

- [ ] **Step 2: Make the Cassandra index text nullable**

In `chat-index-cassandra/.../domain/Message.kt`, change `@field:Column("text") val data: String` to `@field:Column("text") val data: String?` in both `ChatMessageByUser` and `ChatMessageByTopic`.

In `MessageIndex.add`, before `return`, add:

```kotlin
        // A pointer carries no text, so its index rows hold none. See CHAT-erkoxybm.
        val text = entity.data.takeUnless { entity is PointerMessage<*> }
```

Then pass `text` in place of `entity.data` in both the `ChatMessageByUser` and the `ChatMessageByTopic` constructor. Import `com.demo.chat.domain.PointerMessage`.

- [ ] **Step 3: Compile every reader of the index text**

```bash
mvn -o -B -pl chat-index-cassandra,chat-index-lucene -am test-compile > $LOG 2>&1; echo exit=$?; grep -E "ERROR.*kt" $LOG | head
```

Expected: exit 0. If a reader of `ChatMessageByUser.data` or `ChatMessageByTopic.data` fails to compile on the nullable type, give it `?: ""` at that read and list the site in the commit message.

- [ ] **Step 4: Run the index suites**

```bash
mvn -o -B -pl chat-index-lucene -am test > $LOG 2>&1; echo exit=$?; grep -E "Tests run:.*Fail|BUILD" $LOG | tail -2
mvn -B -Pintegration -pl chat-index-cassandra -am test > $LOG 2>&1; echo exit=$?; grep -E "Tests run:.*Fail|BUILD" $LOG | tail -2
```

Expected: exit 0 for both.

- [ ] **Step 5: Commit**

```bash
git add chat-index-lucene/src/main/kotlin/com/demo/chat/index/lucene/domain/IndexEntryEncoder.kt \
  chat-index-cassandra/src/main/kotlin/com/demo/chat/index/cassandra/domain/Message.kt \
  chat-index-cassandra/src/main/kotlin/com/demo/chat/index/cassandra/impl/MessageIndex.kt
# Add each reader that Step 3 changed, by its path.
git commit -m "Index no text for a pointer (CHAT-erkoxybm)

The Lucene encoder omits the data field for a pointer. The Cassandra index
rows store a null text. History by room still finds a pointer.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
fp comment CHAT-erkoxybm "Task 4 done: Lucene omits data, Cassandra index stores null text for a pointer."
```

---

### Task 5: The command bus records a pointer

**Files:**
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/command/CommandTypes.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/service/command/CommandFingerprint.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/MemoryDomainCommandBus.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageReindexServiceImpl.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/handler/MessageCommandHandlers.kt`

**Interfaces:**
- Consumes: `PointerMessage`.
- Produces: `CommandOperation.RECORD_POINTER`. `CommandSubmission` gains the last field `val target: Key<T>? = null`. `CommandFingerprint.of(..., target: String? = null)`.

Task 8 proves this path end to end in its control case. The existing bus and fingerprint suites are the regression proof here.

- [ ] **Step 1: Add the operation and the target field**

In `CommandTypes.kt`:

```kotlin
enum class CommandOperation { RECORD_MESSAGE, IMPORT_MESSAGE, RECORD_POINTER }
```

Change the `CommandSubmission` comment and add the field:

```kotlin
/**
 * The input of admission. Normal sends use `RECORD_MESSAGE`. Imports use
 * `IMPORT_MESSAGE`. Pointers use `RECORD_POINTER`, and only a pointer names a
 * [target]. See `CHAT-erkoxybm`.
 */
data class CommandSubmission<T, V>(
    val owner: T,
    val requestId: String,
    val sender: T,
    val dest: T,
    val content: V,
    val operation: CommandOperation = CommandOperation.RECORD_MESSAGE,
    val timestamp: Instant? = null,
    val publish: Boolean? = null,
    val target: Key<T>? = null,
)
```

Import `com.demo.chat.domain.Key`.

- [ ] **Step 2: Add the version 3 fingerprint**

In `CommandFingerprint.kt`, add the sentence `Pointer fingerprints use version 3 and include the target.` to the class comment. Add the parameter `target: String? = null` after `publish`. Replace the version line and the field list with:

```kotlin
            out.writeInt(
                when {
                    target != null -> VERSION + 2
                    timestamp == null && publish == null -> VERSION
                    else -> VERSION + 1
                }
            )
            val fields = when {
                target != null -> listOf(operation.name, senderId, destinationId, text, target)
                timestamp == null && publish == null -> listOf(operation.name, senderId, destinationId, text)
                else -> listOf(operation.name, senderId, destinationId, text, timestamp.toString(), publish.toString())
            }
```

Versions 1 and 2 produce the same bytes as before.

- [ ] **Step 3: Admit a pointer in the bus**

In `MemoryDomainCommandBus.admit`, before `RequestIds.requireValid`, add:

```kotlin
        // Only a pointer names a target, and a pointer always names one. CHAT-erkoxybm.
        if ((submission.operation == CommandOperation.RECORD_POINTER) != (submission.target != null)) {
            throw DefinitiveRefusalException("A RECORD_POINTER command needs a target, and no other operation takes one.")
        }
```

Pass the target to the fingerprint as its last argument:

```kotlin
            submission.publish,
            submission.target?.let { "${typeUtil.toString(it.root)}:${typeUtil.toString(it.id)}" },
        )
```

In `stageAndCommit`, replace the `message` and `obligations` arguments of `AcceptedCommand` with:

```kotlin
            message = submission.target?.let {
                // A pointer is a Message<T, String>. Every composition binds V to String.
                @Suppress("UNCHECKED_CAST")
                PointerMessage(messageKey, it) as Message<T, V>
            } ?: Message.create(messageKey, submission.content, true),
            obligations = when {
                // A pointer has no text to embed. CHAT-erkoxybm.
                submission.operation == CommandOperation.RECORD_POINTER -> obligations - BackendId.VECTOR
                submission.publish == false -> obligations - BackendId.PUBSUB
                else -> obligations
            },
```

Import `com.demo.chat.domain.DefinitiveRefusalException`, `com.demo.chat.domain.PointerMessage`, and `com.demo.chat.domain.command.CommandOperation` if they are not imported.

- [ ] **Step 4: Skip pointers in the reindex**

In `MessageReindexServiceImpl.scanWith`, after the existing exclusion filter, add:

```kotlin
            // A pointer has no text to embed. The filter runs before every counter. CHAT-erkoxybm.
            .filter { message -> message !is PointerMessage<*> }
```

Import `com.demo.chat.domain.PointerMessage`.

- [ ] **Step 4a: Declare the pointer operation on the handlers**

In `MessageCommandHandlers.kt`, replace `descriptorOf` with:

```kotlin
private val MESSAGE_OPERATIONS = setOf(CommandOperation.RECORD_MESSAGE, CommandOperation.IMPORT_MESSAGE)

/**
 * `V` has no pointer obligation, so only `P`, `I` and `U` declare
 * `RECORD_POINTER`. See `CHAT-erkoxybm`.
 */
private fun descriptorOf(backend: BackendId) = HandlerDescriptor(
    backend, setOf(ChatDomain.MESSAGE),
    if (backend == BackendId.VECTOR) MESSAGE_OPERATIONS else MESSAGE_OPERATIONS + CommandOperation.RECORD_POINTER,
    SafeRepeatContracts.SUPPORTED.getValue(backend),
)
```

No main code reads `HandlerDescriptor.operations` on 2026-10-10. The set is metadata, and it must still name what each handler receives.

- [ ] **Step 5: Run the bus, fingerprint and reindex suites**

```bash
mvn -o -B -pl chat-core,chat-service-composite -am test > $LOG 2>&1; echo exit=$?; grep -E "Tests run:.*Fail|BUILD" $LOG | tail -3
```

Expected: exit 0. `CommandFingerprintTests` passes unchanged, which proves versions 1 and 2 did not move.

- [ ] **Step 6: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/command/CommandTypes.kt \
  chat-core/src/main/kotlin/com/demo/chat/service/command/CommandFingerprint.kt \
  chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/MemoryDomainCommandBus.kt \
  chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageReindexServiceImpl.kt \
  chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/handler/MessageCommandHandlers.kt
git commit -m "Record a pointer command with no vector obligation (CHAT-erkoxybm)

RECORD_POINTER builds a PointerMessage and drops V from its obligations.
The fingerprint gains version 3 with the target. The reindex skips
pointers. The P, I and U handlers declare the operation.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
fp comment CHAT-erkoxybm "Task 5 done: RECORD_POINTER, fingerprint v3, no V obligation, reindex skip."
```

---

### Task 6: A join grants `FORWARD`

**Files:**
- Modify: `chat-core/src/main/kotlin/com/demo/chat/service/security/policy/ShippedGrantPolicies.kt`
- Modify: `chat-security/src/test/kotlin/com/demo/chat/test/MembershipGrantTests.kt`
- Modify: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/RoomMemberGrantWiringTests.kt`

**Interfaces:**
- Produces: `ShippedGrantPolicies.FORWARD = "FORWARD"`.

`GrantPolicy` holds no allow-list of permission names, so the typed policy schema of `CHAT-zcxgrtqc` accepts `FORWARD` with no change.

- [ ] **Step 1: Change the expected rows first**

In `MembershipGrantTests.kt`:
- Lines 60 and 120: `containsExactlyInAnyOrder("SEND", "SUBSCRIBE")` becomes `containsExactlyInAnyOrder("SEND", "SUBSCRIBE", "FORWARD")`.
- Lines 96 and 107: `hasSize(2)` becomes `hasSize(3)`.
- Line 152: `listOf("SEND", "SUBSCRIBE")` becomes `listOf("SEND", "SUBSCRIBE", "FORWARD")`.
- Line 30, the class comment: `SEND` and `SUBSCRIBE` becomes `SEND`, `SUBSCRIBE` and `FORWARD`.

In `RoomMemberGrantWiringTests.kt` line 145: `listOf("SEND", "SUBSCRIBE")` becomes `listOf("SEND", "SUBSCRIBE", "FORWARD")`.

- [ ] **Step 2: Run the tests to verify they fail**

```bash
mvn -o -B -pl chat-core,chat-security -am test -Dtest=MembershipGrantTests -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|FORWARD" $LOG | head -5
```

Expected: exit 1. The failures name the missing `FORWARD` row.

- [ ] **Step 3: Add the rule to the policies**

In `ShippedGrantPolicies.kt`, add `const val FORWARD = "FORWARD"` after `SUBSCRIBE`. Add one rule to each policy, and update both comments:

```kotlin
    /**
     * The member of a join gets `SEND`, `SUBSCRIBE` and `FORWARD` on the room,
     * with no expiry. `Anon` and the `User` root get no row. See
     * `CHAT-mfveaecc`, `CHAT-lfaajjcj` and `CHAT-erkoxybm`.
     */
    val ROOM_JOIN = GrantPolicy(
        name = "roomjoin",
        trigger = ActionTrigger.ROOM_JOINED,
        rules = listOf(
            GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, SEND, GrantExpiry.NONE),
            GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, SUBSCRIBE, GrantExpiry.NONE),
            GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, FORWARD, GrantExpiry.NONE),
        ),
        noGrant = setOf(NoGrantPrincipal.ANON, NoGrantPrincipal.USER_ROOT),
    )

    /**
     * The `SEND`, `SUBSCRIBE` and `FORWARD` grants of the member of a leave
     * expire at the time of the leave. `Anon` and the `User` root get no row.
     */
    val ROOM_LEAVE = GrantPolicy(
        name = "roomleave",
        trigger = ActionTrigger.ROOM_LEFT,
        rules = listOf(
            GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, SEND, GrantExpiry.NOW),
            GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, SUBSCRIBE, GrantExpiry.NOW),
            GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, FORWARD, GrantExpiry.NOW),
        ),
        noGrant = setOf(NoGrantPrincipal.ANON, NoGrantPrincipal.USER_ROOT),
    )
```

- [ ] **Step 4: Run every grant suite**

```bash
mvn -o -B -pl chat-core,chat-security,chat-service-composite,chat-deploy-memory -am test -Dtest='MembershipGrantTests,ShippedGrantPolicyTests,PolicyGrantWriterTests,GrantPolicyTests,TopicServiceMemberGrantTests,RoomMemberGrantWiringTests,StandardUserJoinSendTests' -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|FAIL" $LOG | tail -10
```

Expected: exit 0. If another test fails because it lists the exact rows of a join or a leave, add `FORWARD` to that list only. Change no other expectation, and name each changed file in the commit message.

- [ ] **Step 5: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/service/security/policy/ShippedGrantPolicies.kt \
  chat-security/src/test/kotlin/com/demo/chat/test/MembershipGrantTests.kt \
  chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/RoomMemberGrantWiringTests.kt
# Add each other test file that Step 4 changed, by its path.
git commit -m "Grant FORWARD on a join and expire it on a leave (CHAT-erkoxybm)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
fp comment CHAT-erkoxybm "Task 6 done: roomjoin grants FORWARD, roomleave expires it."
```

---

### Task 7: `messageById` checks the room of the message

**Files:**
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/access/composite/MessageServiceAccess.kt`
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/composite/mapping/ChatMessageServiceRestMapping.kt`
- Modify: `shared-deploy-configuration/src/main/config/userinit.yml`
- Modify: `chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt`
- Modify: `chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraAuthorizationMatrixTests.kt`
- Modify: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageRestAccessTests.kt`
- Create: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageByIdRestRuleTests.kt`
- Create: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/DeploymentCallers.kt`
- Create: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MessageByIdRoomRuleTests.kt`
- Modify: `docs/ANONYMOUS-AUTHORIZATION.md`

**Interfaces:**
- Produces: `class DeploymentCallers(strategies: RSocketStrategies, port: Int, composite: CompositeServiceBeans<Long, String>, authentication: AuthenticationService<Long>)` with `connect(username: String?, password: String?): RSocketRequester`, `standardUser(handle: String, password: String): Key<Long>`, `addRoom(name: String): Key<Long>`, `join(requester, member, room)`, `send(requester, member, room, text): Key<Long>`. Task 8 uses it.

The two `GET` sites and the `GET` row change in one commit. The spec forbids a split.

- [ ] **Step 1: Audit every main-source `GET` check on a message**

```bash
git grep -n "'GET'" -- '*/src/main/*.kt'
```

Expected: four lines. Two name a message: `MessageServiceAccess.kt` (`messageById`) and `ChatMessageServiceRestMapping.kt` (`restMessageById`). Two name a topic: `TopicServiceAccess.kt` and `ChatTopicServiceRestMapping.kt`. If any other line names a message, stop and report it. The spec allows only these two sites.

- [ ] **Step 2: Write the shared deployment helper**

Create `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/DeploymentCallers.kt`:

```kotlin
package com.demo.chat.test.deploy.memory

import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.service.security.AuthenticationService
import io.rsocket.metadata.WellKnownMimeType
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.util.MimeTypeUtils
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/**
 * The callers of a running memory deployment, over RSocket. The pointer
 * tests and the message read tests share them. See `CHAT-erkoxybm`.
 */
class DeploymentCallers(
    private val strategies: RSocketStrategies,
    private val port: Int,
    private val composite: CompositeServiceBeans<Long, String>,
    private val authentication: AuthenticationService<Long>,
) {
    val timeout: Duration = Duration.ofSeconds(10)

    private val mapper = JsonMapper.builder()
        .addModule(ChatJackson3Modules().chatJackson3Module())
        .build()

    /** A room with no owner row. It is created with no credential. */
    fun addRoom(name: String): Key<Long> =
        composite.topicService().addRoom(ByStringRequest(name)).block(timeout)!!

    /** The service stores the value as given, so the password carries the encoder id. */
    fun standardUser(handle: String, password: String): Key<Long> {
        val key = composite.userService()
            .addUser(UserCreateRequest("name-$handle", handle, "http://u")).block(timeout)!!
        authentication.setAuthentication(key, "{noop}$password").block(timeout)
        return key
    }

    /** A fresh builder per connection, because the injected builder is one mutable object. A null user connects anonymously. */
    fun connect(username: String?, password: String?): RSocketRequester {
        val builder = RSocketRequester.builder()
            .rsocketStrategies(
                strategies.mutate()
                    .decoders { it.add(0, JacksonJsonDecoder(mapper)) }
                    .encoders { it.add(0, JacksonJsonEncoder(mapper)) }
                    .encoder(SimpleAuthenticationEncoder())
                    .build()
            )
        if (username != null && password != null) {
            builder.setupMetadata(
                UsernamePasswordMetadata(username, password),
                MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string),
            )
        }
        return builder.connectTcp("localhost", port).block(timeout)!!
    }

    fun join(requester: RSocketRequester, member: Key<Long>, room: Key<Long>) {
        requester.route("topic.topic-join").data(MembershipRequest(member.id, room.id))
            .retrieveMono(Void::class.java).block(timeout)
    }

    @Suppress("UNCHECKED_CAST")
    fun send(requester: RSocketRequester, member: Key<Long>, room: Key<Long>, text: String): Key<Long> =
        requester.route("message.message-send").data(MessageSendRequest(text, member.id, room.id))
            .retrieveMono(Key::class.java).block(timeout)!! as Key<Long>
}
```

- [ ] **Step 3: Write the RSocket rule test**

Create `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MessageByIdRoomRuleTests.kt`. Copy the class annotations of `StandardUserJoinSendTests` exactly, and change `spring.application.name` to `test-deployment-message-by-id-room-rule`.

```kotlin
/**
 * `messageById` reads the message, and then checks `SUBSCRIBE` on its room.
 * See `CHAT-erkoxybm`. Before this rule, the shipped `{Anon, Message, GET}`
 * row let every caller read every message by id.
 */
// (annotations copied from StandardUserJoinSendTests)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageByIdRoomRuleTests {

    @Autowired lateinit var strategies: RSocketStrategies
    @Autowired lateinit var composite: CompositeServiceBeans<Long, String>
    @Autowired lateinit var authentication: AuthenticationService<Long>

    @Value("\${local.rsocket.server.port}")
    var port: Int = 0

    private val callers by lazy { DeploymentCallers(strategies, port, composite, authentication) }

    @Test
    fun `a member reads a message by id and a caller that is not a member is refused`() {
        val room = callers.addRoom("byidruleroom")
        val member = callers.standardUser("byidmember", "byidmembersecret")
        val outsider = callers.standardUser("byidoutsider", "byidoutsidersecret")
        val memberRequester = callers.connect("byidmember", "byidmembersecret")
        callers.join(memberRequester, member, room)
        val message = callers.send(memberRequester, member, room, "read me by id")

        val read = memberRequester.route("message.message-by-id").data(ByIdRequest(message.id))
            .retrieveMono(Map::class.java).block(callers.timeout)
        assertThat(read).describedAs("the member read").isNotNull

        assertThatThrownBy {
            callers.connect("byidoutsider", "byidoutsidersecret").route("message.message-by-id")
                .data(ByIdRequest(message.id)).retrieveMono(Map::class.java).block(callers.timeout)
        }.describedAs("the outsider read").hasMessageContaining("Access Denied")
    }
}
```

Imports: `com.demo.chat.ChatApp`, `com.demo.chat.config.CompositeServiceBeans`, `com.demo.chat.domain.ByIdRequest`, `com.demo.chat.service.security.AuthenticationService`, AssertJ `assertThat` and `assertThatThrownBy`, `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.TestInstance`, `org.springframework.beans.factory.annotation.Autowired`, `org.springframework.beans.factory.annotation.Value`, `org.springframework.messaging.rsocket.RSocketStrategies`, and the annotation imports of `StandardUserJoinSendTests`.

- [ ] **Step 4: Write the REST rule test**

Create `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageByIdRestRuleTests.kt`. Copy the imports of `MessageRestAccessTests.kt`, and add `com.demo.chat.domain.Message`, `com.demo.chat.domain.SimpleMessageKey`, `org.mockito.BDDMockito.willAnswer`, and `org.mockito.invocation.InvocationOnMock`.

```kotlin
/**
 * `GET /message/id/{id}` reads the message, and then checks `SUBSCRIBE` on
 * its room. See `CHAT-erkoxybm`. The broker allows `SUBSCRIBE` on
 * [OPEN_ROOM] alone.
 */
@WebFluxTest
@ContextConfiguration(
    classes = [
        TestLongCompositeServiceBeans::class,
        WebFluxTestConfiguration::class,
        LongTypeUtilConfiguration::class,
        ChatMessageServiceController::class,
        MessageByIdRestRuleConfiguration::class,
        KeyRefusalAdvice::class,
    ]
)
@TestPropertySource(properties = ["app.controller.message"])
class MessageByIdRestRuleTests {
    @Autowired lateinit var client: WebTestClient
    @Autowired lateinit var beans: CompositeServiceBeans<Long, String>
    @Autowired lateinit var registry: TestGeneratorKeyService<Long>

    @BeforeEach
    fun `register the ids and stub the reads`() {
        registry.register(OPEN_ROOM, ChatDomain.MESSAGE_TOPIC)
        registry.register(CLOSED_ROOM, ChatDomain.MESSAGE_TOPIC)
        registry.register(OPEN_MESSAGE, ChatDomain.MESSAGE)
        registry.register(CLOSED_MESSAGE, ChatDomain.MESSAGE)
        given(beans.messageService().messageById(ByIdRequest(OPEN_MESSAGE))).willReturn(Mono.just(messageIn(OPEN_MESSAGE, OPEN_ROOM)))
        given(beans.messageService().messageById(ByIdRequest(CLOSED_MESSAGE))).willReturn(Mono.just(messageIn(CLOSED_MESSAGE, CLOSED_ROOM)))
    }

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = ["TEST"])
    fun `a caller with SUBSCRIBE on the room reads the message`() {
        client.get().uri("/message/id/$OPEN_MESSAGE").exchange().expectStatus().isOk
    }

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = ["TEST"])
    fun `a caller without SUBSCRIBE on the room is refused`() {
        client.get().uri("/message/id/$CLOSED_MESSAGE").exchange().expectStatus().isForbidden
    }

    private fun messageIn(id: Long, room: Long): Message<Long, String> =
        Message.create(SimpleMessageKey(id, 1L, 1L, room), "text", true)

    companion object {
        const val OPEN_ROOM = 45601L
        const val CLOSED_ROOM = 45602L
        const val OPEN_MESSAGE = 45611L
        const val CLOSED_MESSAGE = 45612L
    }
}

@TestConfiguration
@EnableReactiveMethodSecurity
class MessageByIdRestRuleConfiguration {

    /** The broker allows SUBSCRIBE on the open room and denies every other check. */
    @Bean
    @Primary
    @Suppress("UNCHECKED_CAST")
    fun roomBroker(): AccessBroker<Long> {
        val broker = mock(AccessBroker::class.java) as AccessBroker<Long>
        willAnswer { call: InvocationOnMock ->
            val target = call.getArgument<Key<Long>>(1)
            val permission = call.getArgument<String>(2)
            Mono.just(target.id == MessageByIdRestRuleTests.OPEN_ROOM && permission == "SUBSCRIBE")
        }.given(broker).hasAccessByPrincipal(anyObject(), anyObject(), anyObject())
        return broker
    }

    /** **The name matters.** Every expression resolves the bean `chatAccess`. */
    @Bean
    fun chatAccess(broker: AccessBroker<Long>, rootKeys: RootKeys<Long>, verifier: KeyVerifier<Long>): SpringSecurityAccessBrokerService<Long> =
        SpringSecurityAccessBrokerService(broker, rootKeys, verifier)
}
```

`ByIdRequest` is a data class, so the stub matches by value.

In `MessageRestAccessTests.kt`, remove the line `client.get().uri("/message/id/$MESSAGE")...isForbidden` and the line `verify(beans.messageService(), never()).messageById(anyObject())`. A post-read check calls the service before it refuses, so that assertion no longer holds. `MessageByIdRestRuleTests` covers the route.

- [ ] **Step 5: Run the new tests to verify they fail**

```bash
mvn -o -B -pl chat-webflux,chat-deploy-memory -am test -Dtest='MessageByIdRestRuleTests,MessageByIdRoomRuleTests' -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|expected|Expecting" $LOG | head -8
```

Expected: exit 1. The REST allow case answers 403, because the `GET` check denies. The RSocket outsider read succeeds, because `{Anon, Message, GET}` allows it.

- [ ] **Step 6: Change both sites**

In `MessageServiceAccess.kt`, import `org.springframework.security.access.prepost.PostAuthorize`, and replace the `messageById` lines with:

```kotlin
    /**
     * **A read checks the room of the message, after the read.** A raw id
     * names no room, so the check needs the stored message. `dest` is a raw
     * id, so the expression uses `hasAccessToId`. See `CHAT-erkoxybm`.
     */
    @PostAuthorize("@chatAccess.hasAccessToId(returnObject.key.dest, 'SUBSCRIBE')")
    override fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>>
```

In `ChatMessageServiceRestMapping.kt`, import `org.springframework.security.access.prepost.PostAuthorize`, and replace the annotations of `restMessageById` with:

```kotlin
    @GetMapping("/id/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.OK)
    @PostAuthorize("@chatAccess.hasAccessToId(returnObject.key.dest, 'SUBSCRIBE')")
```

- [ ] **Step 7: Remove the `GET` row**

In `shared-deploy-configuration/src/main/config/userinit.yml`, delete the line:

```yaml
        - { user: Anon, target: Message, role: GET }
```

- [ ] **Step 8: Move the matrix rows**

In `AnonymousAuthorizationMatrixTests.kt`, in `operations()`, replace the row:

```kotlin
            "messageById GET" to { s -> s.hasAccessTo(MESSAGE_KEY, "GET") },
```

with:

```kotlin
            "messageById room SUBSCRIBE" to { s -> s.hasAccessTo(ROOM_KEY, "SUBSCRIBE") },
```

Then:
- In the anonymous matrix, `"messageById GET" to true` becomes `"messageById room SUBSCRIBE" to false`.
- In the agent matrix, `"messageById GET" to true` becomes `"messageById room SUBSCRIBE" to false`.
- In the administrator-minus-agent set, add `"messageById room SUBSCRIBE"` beside `"send room SEND"`.
- In the class comment and the anonymous test comment, replace the claims about `Message:GET` with: `messageById checks SUBSCRIBE on the room of the message since CHAT-erkoxybm. No shipped row grants SUBSCRIBE to a caller that is not a member.` Rename the test `an anonymous caller may read a user, read a message and add a room` to `an anonymous caller may read a user and add a room`.

The test `a message read allows through a domain root row` builds its own row, so it stays.

In `CassandraAuthorizationMatrixTests.kt`, replace the expression row:

```kotlin
        "messageById GET" to answer({ it.hasAccessTo(message, "GET") }, access, context),
```

with:

```kotlin
        "messageById room SUBSCRIBE" to answer({ it.hasAccessTo(room, "SUBSCRIBE") }, access, context),
```

and the expected row `"messageById GET" to true` with `"messageById room SUBSCRIBE" to false`. In the comment near line 94, remove `messageById` from the list of reads that allow, and remove `{Anon, Message, GET}`.

- [ ] **Step 9: Run the tests to verify they pass**

```bash
mvn -o -B -pl chat-security,chat-webflux,chat-deploy-memory -am test -Dtest='MessageByIdRestRuleTests,MessageByIdRoomRuleTests,MessageRestAccessTests,AnonymousAuthorizationMatrixTests,SendCheckExpressionTests,UserInitConfigBindingTests' -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|FAIL" $LOG | tail -10
mvn -B -Pintegration -pl chat-deploy-cassandra -am test -Dtest=CassandraAuthorizationMatrixTests -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|BUILD" $LOG | tail -3
```

Expected: exit 0 for both. `SendCheckExpressionTests` reads the annotation text of `messageById`. If it fails because it expects a `@PreAuthorize`, change that one entry to read the `@PostAuthorize` text, and name the change in the commit message.

- [ ] **Step 10: Mutation: no post-read check**

Change both `@PostAuthorize` expressions to `"permitAll()"`. Run the first Step 9 command. Expected: the REST deny case and the RSocket outsider case fail, and nothing else. Restore both expressions by hand, then confirm with `git diff` that each file shows the `hasAccessToId` expression.

- [ ] **Step 11: Record the rule in the document**

In `docs/ANONYMOUS-AUTHORIZATION.md`:
- Change the `messageById` matrix row to deny for an anonymous caller, an authenticated caller that is not a member, and the agent. It allows for a member and for `Admin`.
- Add a short section `messageById checks the room (2026-10-10)`: `messageById reads the message, and then checks SUBSCRIBE on its room. The row {Anon, Message, GET} left userinit.yml. A denied read answers 403. See CHAT-erkoxybm.`

Then run the drift discipline of `AGENTS.md`:

```bash
drift refs docs/ANONYMOUS-AUTHORIZATION.md; drift check
```

If `drift check` reports the document STALE, read every bound passage. Fix any passage that the change made false. Then refresh with `drift link` for that document, as `drift refs` lists it, and run `drift check` again. Expected: `ok`.

- [ ] **Step 12: Commit**

```bash
git add chat-security/src/main/kotlin/com/demo/chat/security/access/composite/MessageServiceAccess.kt \
  chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/composite/mapping/ChatMessageServiceRestMapping.kt \
  shared-deploy-configuration/src/main/config/userinit.yml \
  chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt \
  chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraAuthorizationMatrixTests.kt \
  chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageRestAccessTests.kt \
  chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageByIdRestRuleTests.kt \
  chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/DeploymentCallers.kt \
  chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MessageByIdRoomRuleTests.kt \
  docs/ANONYMOUS-AUTHORIZATION.md
# Add drift.lock only if Step 11 refreshed it. Add SendCheckExpressionTests.kt only if Step 9 changed it.
git commit -m "Check SUBSCRIBE on the room of a message read by id (CHAT-erkoxybm)

messageById and GET /message/id/{id} read the message, and then check
SUBSCRIBE on its room. The {Anon, Message, GET} row leaves userinit.yml
in the same commit, because a GET check that stays would refuse every
caller.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
fp comment CHAT-erkoxybm "Task 7 done: messageById post-read room check on RSocket and REST, GET row removed, matrices and doc updated."
```

---

### Task 8: The composite creates a pointer, over RSocket

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/PointerExceptions.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/command/PointerTargetAccess.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/service/composite/ChatMessageService.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessagingServiceImpl.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/access/MessagingServiceAccess.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/CompositeServiceBeansConfiguration.kt`
- Create: `chat-security/src/main/kotlin/com/demo/chat/security/access/ContextPointerTargetAccess.kt`
- Create: `chat-security/src/main/kotlin/com/demo/chat/config/auth/PointerTargetAccessConfiguration.kt`
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/access/composite/MessageServiceAccess.kt`
- Modify: `chat-service-controller/src/main/kotlin/com/demo/chat/controller/composite/mapping/MessageServiceControllerMapping.kt`
- Modify: `chat-client-rsocket/src/main/kotlin/com/demo/chat/client/rsocket/clients/composite/MessagingClient.kt`
- Modify: `chat-service-controller/src/test/kotlin/com/demo/chat/test/resolve/RSocketRouteCatalog.kt`
- Create: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MessagePointerCreationTests.kt`
- Modify: `docs/ANONYMOUS-AUTHORIZATION.md`

**Interfaces:**
- Consumes: `PointerMessage`, `MessagePointerRequest` (Task 1), `CommandSubmission.target` and `RECORD_POINTER` (Task 5), `FORWARD` (Task 6), `DeploymentCallers` (Task 7).
- Produces: `ChatMessageService.pointer(req: MessagePointerRequest<T>): Mono<out MessageSendResult<T>>`, `interface PointerTargetAccess<T> { fun canSubscribe(roomId: T): Mono<Boolean> }`, `NoSuchPointerTargetException(domain: ChatDomain)`, `PointerToPointerException`, RSocket route `message.message-pointer`.

- [ ] **Step 1: Write the creation test**

Create `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MessagePointerCreationTests.kt`. Copy the class annotations of `StandardUserJoinSendTests` exactly, and change `spring.application.name` to `test-deployment-message-pointer-creation`.

```kotlin
/**
 * Each refusal of pointer creation, and the control. See `CHAT-erkoxybm`.
 *
 * Every call crosses the RSocket seam and the composite access checks of a
 * running composition root. After each refusal, the host room holds no new
 * pointer.
 */
// (annotations copied from StandardUserJoinSendTests)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessagePointerCreationTests {

    @Autowired lateinit var strategies: RSocketStrategies
    @Autowired lateinit var stores: PersistenceServiceBeans<Long, String>
    @Autowired lateinit var composite: CompositeServiceBeans<Long, String>
    @Autowired lateinit var authentication: AuthenticationService<Long>

    @Value("\${local.rsocket.server.port}")
    var port: Int = 0

    private val callers by lazy { DeploymentCallers(strategies, port, composite, authentication) }
    private val tags = AtomicInteger()

    /** A member of [host] and [source]. [message] sits in [source], so the member can read it. */
    private inner class Scene {
        val tag = "s${tags.incrementAndGet()}"
        val host = callers.addRoom("ptrhost$tag")
        val source = callers.addRoom("ptrsource$tag")
        val member = callers.standardUser("ptrmember$tag", "ptrmembersecret$tag")
        val requester = callers.connect("ptrmember$tag", "ptrmembersecret$tag")

        init {
            callers.join(requester, member, host)
            callers.join(requester, member, source)
        }

        val message = callers.send(requester, member, source, "pointer target $tag")
    }

    @Test
    fun `an anonymous caller is refused`() {
        val scene = Scene()
        assertRefused(callers.connect(null, null), request(scene.host, scene.message.id, "Message"), "Access Denied", scene.host)
    }

    @Test
    fun `a caller without FORWARD on the host room is refused`() {
        val scene = Scene()
        val outsider = callers.standardUser("ptroutsider${scene.tag}", "ptroutsidersecret")
        val outsiderRequester = callers.connect("ptroutsider${scene.tag}", "ptroutsidersecret")
        // The outsider can read the target, so only FORWARD on the host room is missing.
        callers.join(outsiderRequester, outsider, scene.source)
        assertRefused(outsiderRequester, request(scene.host, scene.message.id, "Message"), "Access Denied", scene.host)
    }

    @Test
    fun `a target domain other than Message or MessageTopic is refused`() {
        val scene = Scene()
        assertRefused(scene.requester, request(scene.host, scene.message.id, "User"), "Message or MessageTopic", scene.host)
    }

    @Test
    fun `a target message that does not exist is refused`() {
        val scene = Scene()
        assertRefused(scene.requester, request(scene.host, UNKNOWN_ID, "Message"), "no such message", scene.host)
    }

    @Test
    fun `a target message in a room the caller cannot read is refused as missing`() {
        val scene = Scene()
        val secretRoom = callers.addRoom("ptrsecret${scene.tag}")
        val other = callers.standardUser("ptrother${scene.tag}", "ptrothersecret")
        val otherRequester = callers.connect("ptrother${scene.tag}", "ptrothersecret")
        callers.join(otherRequester, other, secretRoom)
        val secret = callers.send(otherRequester, other, secretRoom, "not for the member")
        assertRefused(scene.requester, request(scene.host, secret.id, "Message"), "no such message", scene.host)
    }

    @Test
    fun `a target topic that does not exist is refused`() {
        val scene = Scene()
        assertRefused(scene.requester, request(scene.host, UNKNOWN_ID, "MessageTopic"), "no such topic", scene.host)
    }

    @Test
    fun `a target topic the caller cannot read is refused as missing`() {
        val scene = Scene()
        val closed = callers.addRoom("ptrclosed${scene.tag}")
        assertRefused(scene.requester, request(scene.host, closed.id, "MessageTopic"), "no such topic", scene.host)
    }

    /**
     * `deleteRoom` removes the store row and the index row. The key registry
     * keeps the key, so a registry check alone passes. The member still holds
     * its `SUBSCRIBE` row. This test removes the store row the same way.
     */
    @Test
    fun `a target topic whose store row is gone is refused as missing`() {
        val scene = Scene()
        val gone = callers.addRoom("ptrgone${scene.tag}")
        callers.join(scene.requester, scene.member, gone)
        stores.topicPersistence().rem(gone).block(callers.timeout)
        assertRefused(scene.requester, request(scene.host, gone.id, "MessageTopic"), "no such topic", scene.host)
    }

    @Test
    fun `a pointer to a pointer is refused`() {
        val scene = Scene()
        create(scene.requester, request(scene.host, scene.message.id, "Message"))
        val first = pointersIn(scene.host).single()
        assertRefused(scene.requester, request(scene.host, first.key.id, "Message"), "a pointer cannot target a pointer", scene.host, before = 1)
    }

    @Test
    fun `a member with FORWARD and a readable target stores one pointer per target`() {
        val scene = Scene()
        create(scene.requester, request(scene.host, scene.message.id, "Message"))
        create(scene.requester, request(scene.host, scene.source.id, "MessageTopic"))

        val stored = pointersIn(scene.host)
        assertThat(stored.map { it.target.id }).containsExactlyInAnyOrder(scene.message.id, scene.source.id)
        assertThat(stored.map { it.key.from }).containsOnly(scene.member.id)
    }

    private fun request(host: Key<Long>, targetId: Long, domain: String) =
        MessagePointerRequest(host.id, targetId, domain, "ptr-${tags.incrementAndGet()}")

    private fun create(requester: RSocketRequester, req: MessagePointerRequest<Long>) {
        requester.route("message.message-pointer").data(req).retrieveMono(Map::class.java).block(callers.timeout)
    }

    private fun assertRefused(requester: RSocketRequester, req: MessagePointerRequest<Long>, text: String, host: Key<Long>, before: Int = 0) {
        assertThatThrownBy { create(requester, req) }.describedAs("the refusal").hasMessageContaining(text)
        assertThat(pointersIn(host)).describedAs("the pointers in the host room after the refusal").hasSize(before)
    }

    @Suppress("UNCHECKED_CAST")
    private fun pointersIn(host: Key<Long>): List<PointerMessage<Long>> =
        stores.messagePersistence().all().collectList().block(callers.timeout)!!
            .filter { it is PointerMessage<*> && it.key.dest == host.id }
            .map { it as PointerMessage<Long> }

    companion object {
        /** An id that no registry holds. */
        const val UNKNOWN_ID = 424242L
    }
}
```

Imports: the annotation imports of `StandardUserJoinSendTests`, plus `com.demo.chat.config.CompositeServiceBeans`, `com.demo.chat.config.PersistenceServiceBeans`, `com.demo.chat.domain.Key`, `com.demo.chat.domain.MessagePointerRequest`, `com.demo.chat.domain.PointerMessage`, `com.demo.chat.service.security.AuthenticationService`, AssertJ `assertThat` and `assertThatThrownBy`, `org.springframework.messaging.rsocket.RSocketRequester`, `org.springframework.messaging.rsocket.RSocketStrategies`, and `java.util.concurrent.atomic.AtomicInteger`.

- [ ] **Step 2: Run the test to verify it fails**

```bash
mvn -o -B -pl chat-deploy-memory -am test -Dtest=MessagePointerCreationTests -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|ERROR.*kt|No handler" $LOG | head -6
```

Expected: exit 1. The test class does not compile, because `MessagePointerRequest` has no route and `pointer` does not exist yet. If it compiles, every test fails, because no route `message.message-pointer` exists.

- [ ] **Step 3: Add the exceptions and the port**

Create `chat-core/src/main/kotlin/com/demo/chat/domain/PointerExceptions.kt`:

```kotlin
package com.demo.chat.domain

import com.demo.chat.domain.knownkey.ChatDomain

/**
 * A pointer target that is missing, or that the caller cannot read. Both give
 * the same text, so an id leaks nothing. See `CHAT-erkoxybm`.
 */
class NoSuchPointerTargetException(val domain: ChatDomain) :
    ChatException(if (domain == ChatDomain.MESSAGE) "no such message" else "no such topic"), NoEffectRefusal

/** A pointer to a pointer. No stored chain can exist. See `CHAT-erkoxybm`. */
class PointerToPointerException : ChatException("a pointer cannot target a pointer"), NoEffectRefusal
```

Create `chat-core/src/main/kotlin/com/demo/chat/service/command/PointerTargetAccess.kt`:

```kotlin
package com.demo.chat.service.command

import reactor.core.publisher.Mono

/**
 * The read right of the current caller on a pointer target. See
 * `CHAT-erkoxybm`.
 *
 * The composite reads no security context. A call inside the composite
 * crosses no security proxy, so the composite asks this port. An empty or
 * false answer denies.
 */
interface PointerTargetAccess<T> {
    fun canSubscribe(roomId: T): Mono<Boolean>
}
```

- [ ] **Step 4: Add `pointer` to the service interface**

In `ChatMessageService.kt`, import `com.demo.chat.domain.MessagePointerRequest`, and add after `submit`:

```kotlin
    /** Admits a pointer to a message or a topic into a room. See `CHAT-erkoxybm`. */
    fun pointer(req: MessagePointerRequest<T>): Mono<out MessageSendResult<T>>
```

- [ ] **Step 5: Implement `pointer` in the composite**

In `MessagingServiceImpl.kt`, add the last two constructor parameters:

```kotlin
    private val pointerTargets: PointerTargetAccess<T>? = null,
    private val topicPersistence: TopicPersistence<T>? = null,
```

Import `com.demo.chat.service.core.TopicPersistence`. A missing store answers every topic target as missing.

Change `admitAndWait` to take an operation and a target:

```kotlin
    private fun admitAndWait(
        owner: Key<T>,
        requestId: String,
        dest: T,
        content: V,
        operation: CommandOperation = CommandOperation.RECORD_MESSAGE,
        target: Key<T>? = null,
    ): Mono<MessageSendResult<T>> {
        // A pointer has no V obligation, so the caller does not wait for V. CHAT-erkoxybm.
        val waitFor = if (operation == CommandOperation.RECORD_POINTER)
            requirement.copy(backends = requirement.backends - BackendId.VECTOR) else requirement
        return verifier.resolve(owner.id, ChatDomain.USER)
            .then(verifier.resolve(dest, ChatDomain.MESSAGE_TOPIC))
            .then(Mono.defer {
                commandBus.submit(CommandSubmission(owner.id, requestId, owner.id, dest, content, operation, target = target))
            })
            .flatMap { receipt ->
                if (waitFor.backends.isEmpty()) Mono.just(MessageSendResult(receipt, CallerOutcome.ACCEPTED, emptyMap()))
                else completions.await(receipt.commandId, waitFor, timeout)
            }
    }
```

Add the operation and its target check:

```kotlin
    /**
     * The checks of the spec, steps 1 and 3 to 7. Step 2, `FORWARD` on the
     * host room, is the `@PreAuthorize` of `MessageServiceAccess`. See
     * `CHAT-erkoxybm`.
     */
    override fun pointer(req: MessagePointerRequest<T>): Mono<out MessageSendResult<T>> =
        owner().flatMap { owner ->
            pointerTarget(req).flatMap { target ->
                // A pointer carries no content. Every composition binds V to String.
                @Suppress("UNCHECKED_CAST")
                admitAndWait(owner, req.requestId, req.dest, "" as V, CommandOperation.RECORD_POINTER, target)
            }
        }

    /**
     * The target key, when the caller can read it. A missing target and an
     * unreadable target give the same error. The id converts exactly, because
     * a JSON body cannot carry the key type.
     */
    private fun pointerTarget(req: MessagePointerRequest<T>): Mono<Key<T>> = Mono.defer {
        val domain = ChatDomain.parse(req.targetDomain)?.takeIf { it in POINTER_TARGETS }
            ?: return@defer Mono.error<Key<T>>(
                KeyInputException("A pointer target domain must be Message or MessageTopic. The value is ${req.targetDomain}.")
            )
        val access = pointerTargets ?: return@defer Mono.error<Key<T>>(AccessDeniedException)
        val targetId = typeUtil.exactFrom(req.targetId as Any)
        val missing = NoSuchPointerTargetException(domain)
        verifier.resolve(targetId, domain)
            .onErrorMap(KeyVerificationException::class.java) { missing }
            .flatMap<Key<T>> { verified ->
                if (domain == ChatDomain.MESSAGE_TOPIC) {
                    // The registry keeps the key of a deleted room, so the store row decides.
                    val topic = topicPersistence?.get(verified.key) ?: Mono.empty()
                    topic.flatMap { access.canSubscribe(verified.key.id) }.filter { it }.map { verified.key }
                } else {
                    // A Cassandra removal keeps the row and sets record to false.
                    messagePersistence.get(verified.key).filter { it.record }.flatMap { target ->
                        access.canSubscribe(target.key.dest).filter { it }.flatMap {
                            if (target is PointerMessage<*>) Mono.error<Key<T>>(PointerToPointerException())
                            else Mono.just(verified.key)
                        }
                    }
                }
            }
            .switchIfEmpty(Mono.error(missing))
    }

    private companion object {
        val POINTER_TARGETS = setOf(ChatDomain.MESSAGE, ChatDomain.MESSAGE_TOPIC)
    }
```

`typeUtil.exactFrom` throws `KeyInputException` inside `Mono.defer`, so a bad id becomes an error signal. Add the imports `com.demo.chat.domain.KeyInputException`, `com.demo.chat.domain.KeyVerificationException`, `com.demo.chat.domain.MessagePointerRequest`, `com.demo.chat.domain.NoSuchPointerTargetException`, `com.demo.chat.domain.PointerMessage`, `com.demo.chat.domain.PointerToPointerException`, and `com.demo.chat.service.command.PointerTargetAccess`. If the class already has a companion object, put `POINTER_TARGETS` in it.

- [ ] **Step 6: Wire the port**

Create `chat-security/src/main/kotlin/com/demo/chat/security/access/ContextPointerTargetAccess.kt`:

```kotlin
package com.demo.chat.security.access

import com.demo.chat.service.command.PointerTargetAccess
import reactor.core.publisher.Mono

/** `SUBSCRIBE` on a room, through the same check as the access expressions. See `CHAT-erkoxybm`. */
class ContextPointerTargetAccess<T>(private val access: SpringSecurityAccessBrokerService<T>) : PointerTargetAccess<T> {
    override fun canSubscribe(roomId: T): Mono<Boolean> = access.hasAccessToId(roomId, "SUBSCRIBE")
}
```

Create `chat-security/src/main/kotlin/com/demo/chat/config/auth/PointerTargetAccessConfiguration.kt`:

```kotlin
package com.demo.chat.config.auth

import com.demo.chat.security.access.ContextPointerTargetAccess
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.service.command.PointerTargetAccess
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"], havingValue = "true")
open class PointerTargetAccessConfiguration<T> {
    @Bean
    open fun pointerTargetAccess(chatAccess: SpringSecurityAccessBrokerService<T>): PointerTargetAccess<T> =
        ContextPointerTargetAccess(chatAccess)
}
```

Find how `SubmitterIdentityConfiguration` is registered:

```bash
git grep -n "SubmitterIdentityConfiguration" -- '*/src/main/*'
```

Register `PointerTargetAccessConfiguration` at every site that list names, in the same way.

In `CompositeServiceBeansConfiguration.kt`, add the constructor parameter `private val pointerTargets: ObjectProvider<PointerTargetAccess<T>>,` after `roomMemberGrants`. In `messageService()`, pass:

```kotlin
        pointerTargets = pointerTargets.ifAvailable,
        topicPersistence = persistenceBeans.topicPersistence(),
```

Import `com.demo.chat.service.command.PointerTargetAccess`.

- [ ] **Step 7: Add the checks and the route**

In `MessageServiceAccess.kt`, import `com.demo.chat.domain.MessagePointerRequest`, and add:

```kotlin
    /** `FORWARD` on the host room. The composite checks the target. See `CHAT-erkoxybm`. */
    @PreAuthorize("@chatAccess.hasAccessToId(#req.dest, 'FORWARD')")
    override fun pointer(req: MessagePointerRequest<T>): Mono<out MessageSendResult<T>>
```

In `MessagingServiceAccess.kt`, add the delegate in the pattern of `submit`:

```kotlin
    override fun pointer(req: MessagePointerRequest<T>): Mono<out MessageSendResult<T>> = verifier.resolve(req.dest, ChatDomain.MESSAGE_TOPIC)
        .flatMap { authMetadataAccessBroker.hasAccessByPrincipal(Mono.from(principalPublisher()), it, "FORWARD") }
        .then(that.pointer(req))
```

This class is the latent programmatic wrapper of `CHAT-ruapxetl`. It follows its own pattern, defect included.

In `MessageServiceControllerMapping.kt`, add:

```kotlin
    @MessageMapping("message-pointer")
    override fun pointer(req: MessagePointerRequest<T>): Mono<out MessageSendResult<T>>
```

In `MessagingClient.kt` (`chat-client-rsocket`), add:

```kotlin
    override fun pointer(req: MessagePointerRequest<T>): Mono<out MessageSendResult<T>> =
            requester
                    .route("${prefix}message-pointer")
                    .data(req)
                    .retrieveMono()
```

In `RSocketRouteCatalog.kt`, after the `submit` entry, add:

```kotlin
        // message.message-pointer. The creator is the authenticated user. The composite resolves the target in its named domain.
        entry("MessageServiceController", "pointer", "MessagePointerRequest<T>",
            "req.dest" to service("MESSAGE_TOPIC"),
            "req.targetId" to service("any")),
```

- [ ] **Step 8: Compile the reactor**

```bash
mvn -o -B install -DskipTests > $LOG 2>&1; echo exit=$?; grep -E "ERROR.*kt|BUILD" $LOG | head -6
```

Expected: exit 0. A failure that names a class with no `pointer` member is an implementer this plan did not list. Add `pointer` there in the pattern of `submit`, and name it in the commit message.

- [ ] **Step 9: Run the tests to verify they pass**

```bash
mvn -o -B -pl chat-service-controller,chat-deploy-memory -am test -Dtest='MessagePointerCreationTests,RouteSignatureGuardTests,StandardUserJoinSendTests' -Dsurefire.failIfNoSpecifiedTests=false > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|FAIL" $LOG | tail -8
```

Expected: exit 0. `MessagePointerCreationTests` runs 10 tests. If `RouteSignatureGuardTests` reports a field of `MessagePointerRequest` that the entry does not name, add that field with the classification its message asks for.

- [ ] **Step 10: Four mutations, each run alone**

Run the Step 9 command after each change, and restore by hand before the next. Confirm each restore with `git diff`.

1. In `MessageServiceAccess.pointer`, change the expression to `"permitAll()"`. Expected: only `a caller without FORWARD on the host room is refused` fails.
2. In `pointerTarget`, change both `access.canSubscribe(...)` calls to `Mono.just(true)`. Expected: only the two `cannot read` cases fail.
3. In `pointerTarget`, change `if (target is PointerMessage<*>)` to `if (false)`. Expected: only `a pointer to a pointer is refused` fails.
4. In `pointerTarget`, change `val topic = topicPersistence?.get(verified.key) ?: Mono.empty()` to `val topic = Mono.just(true)`. Expected: only `a target topic whose store row is gone is refused as missing` fails.

- [ ] **Step 11: Record the pointer row**

In `docs/ANONYMOUS-AUTHORIZATION.md`, add a `pointer` row to the matrix: anonymous deny, member allow, a caller that is not a member deny, the agent deny unless it joined, `Admin` allow. Run `drift check`, and follow the Task 7 Step 11 procedure if it reports STALE.

- [ ] **Step 12: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/PointerExceptions.kt \
  chat-core/src/main/kotlin/com/demo/chat/service/command/PointerTargetAccess.kt \
  chat-core/src/main/kotlin/com/demo/chat/service/composite/ChatMessageService.kt \
  chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessagingServiceImpl.kt \
  chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/access/MessagingServiceAccess.kt \
  chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/CompositeServiceBeansConfiguration.kt \
  chat-security/src/main/kotlin/com/demo/chat/security/access/ContextPointerTargetAccess.kt \
  chat-security/src/main/kotlin/com/demo/chat/config/auth/PointerTargetAccessConfiguration.kt \
  chat-security/src/main/kotlin/com/demo/chat/security/access/composite/MessageServiceAccess.kt \
  chat-service-controller/src/main/kotlin/com/demo/chat/controller/composite/mapping/MessageServiceControllerMapping.kt \
  chat-client-rsocket/src/main/kotlin/com/demo/chat/client/rsocket/clients/composite/MessagingClient.kt \
  chat-service-controller/src/test/kotlin/com/demo/chat/test/resolve/RSocketRouteCatalog.kt \
  chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MessagePointerCreationTests.kt \
  docs/ANONYMOUS-AUTHORIZATION.md
# Add each registration site of Step 6 and each implementer of Step 8, by its path.
# Add drift.lock only if Step 11 refreshed it.
git commit -m "Create a pointer over RSocket (CHAT-erkoxybm)

message.message-pointer checks FORWARD on the host room. The composite
checks that the caller can read the target, that the target store row
exists, and that the target is no pointer. It then admits one
RECORD_POINTER command. A missing target and an unreadable target give
the same error.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
fp comment CHAT-erkoxybm "Task 8 done: pointer operation, PointerTargetAccess port, RSocket route, 10 creation tests, 4 mutations."
```

---

### Task 9: The REST pointer route

**Files:**
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/composite/mapping/ChatMessageServiceRestMapping.kt`
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/config/WebFluxKeyVerifierConfiguration.kt`
- Modify: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/route/RestRouteCatalog.kt`

**Interfaces:**
- Consumes: `pointer`, the two exceptions (Task 8).
- Produces: `POST /message/pointer/{id}` with body `RestPointerBody(targetDomain: String, targetId: String)` and header `Idempotency-Key`.

The owner excluded route tests. `RouteSignatureGuardTests` in `chat-webflux` is the regression proof.

- [ ] **Step 1: Add the route**

In `ChatMessageServiceRestMapping.kt`, after `restSubmit`, add:

```kotlin
    /**
     * A pointer into room [id]. The body carries the target id as a JSON
     * string, and the composite converts it exactly. The facade carries its
     * own check, because a facade call crosses no proxy. See `CHAT-erkoxybm`.
     */
    @PostMapping("/pointer/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("@chatAccess.hasAccessToId(#id.key.id, 'FORWARD')")
    fun restPointer(
        @Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>,
        @RequestBody body: RestPointerBody,
        @RequestHeader("Idempotency-Key") requestId: String,
    ): Mono<ResponseEntity<MessageSendResult<T>>> {
        // The composite converts the text id with TypeUtil.exactFrom.
        @Suppress("UNCHECKED_CAST")
        val targetId = body.targetId as T
        return pointer(MessagePointerRequest(id.key.id, targetId, body.targetDomain, requestId))
            .map { ResponseEntity.status(SubmitStatus.of(it.outcome)).body(it) }
    }
```

At the end of the file, add:

```kotlin
/** The body of `POST /message/pointer/{id}`. See `CHAT-erkoxybm`. */
data class RestPointerBody(val targetDomain: String, val targetId: String)
```

Import `com.demo.chat.domain.MessagePointerRequest`.

- [ ] **Step 2: Map the two refusals**

In `WebFluxKeyVerifierConfiguration.kt`, beside the `NotFoundException` handler, add:

```kotlin
    /** A missing or unreadable pointer target. See `CHAT-erkoxybm`. */
    @ExceptionHandler(NoSuchPointerTargetException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun noSuchPointerTarget(error: NoSuchPointerTargetException): String = error.message ?: "no such message"

    @ExceptionHandler(PointerToPointerException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun pointerToPointer(error: PointerToPointerException): String = error.message ?: "a pointer cannot target a pointer"
```

Import both exceptions from `com.demo.chat.domain`.

- [ ] **Step 3: Add the catalog entry**

In `RestRouteCatalog.kt`, after the `restSubmit` entry, add:

```kotlin
        // POST /message/pointer/{id}. The creator is the authenticated user. The body id is text, and the composite resolves it.
        entry("ChatMessageServiceController", "restPointer", "VerifiedKey<T>,RestPointerBody,String",
            "id" to resolver("MESSAGE_TOPIC")),
```

- [ ] **Step 4: Run the webflux suite**

```bash
mvn -o -B -pl chat-webflux -am test > $LOG 2>&1; echo exit=$?; grep -E "Tests run:.*Fail|FAIL|BUILD" $LOG | tail -4
```

Expected: exit 0. If the route guard reports a different signature or field for `restPointer`, copy the signature it prints into the entry.

- [ ] **Step 5: Commit**

```bash
git add chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/composite/mapping/ChatMessageServiceRestMapping.kt \
  chat-webflux/src/main/kotlin/com/demo/chat/config/WebFluxKeyVerifierConfiguration.kt \
  chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/route/RestRouteCatalog.kt
git commit -m "Add the REST pointer route (CHAT-erkoxybm)

POST /message/pointer/{id} checks FORWARD on the room. The body carries
the target id as text. A missing or unreadable target answers 404, and a
pointer to a pointer answers 400.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
fp comment CHAT-erkoxybm "Task 9 done: REST pointer route, 404 and 400 mappings."
```

---

### Task 10: The shell and MCP read pointers

**Files:**
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommands.kt`
- Modify: `chat-mcp/src/main/kotlin/com/demo/chat/mcp/client/MessageEnvelope.kt`
- Modify: `chat-mcp/src/main/kotlin/com/demo/chat/mcp/tool/MessagingSchemas.kt`
- Modify: `docs/MCP-ADAPTER.md`

**Interfaces:**
- Consumes: `PointerMessage`.
- Produces: shell lines `→ message <id>` and `→ topic <id>`. An MCP pointer item `{kind: "pointer", messageKey, senderId, topicId, timestamp, targetId, targetRoot}`.

The owner excluded shell tests. The shell suite is the regression proof there. One MCP decoder test proves that a history with a pointer still decodes, because one pointer broke the whole MCP history before this task.

- [ ] **Step 0: Write the MCP history test**

Append to `chat-mcp/src/test/kotlin/com/demo/chat/mcp/client/MessageEnvelopeTests.kt`, inside the class:

```kotlin
    /** A history that holds a pointer returns the text item and the pointer item. See `CHAT-erkoxybm`. */
    @Test
    fun `a history with a pointer returns the text item and the pointer item`() {
        for (kind in KeyType.entries) {
            val text = Json.parseToJsonElement(messagingFixture(kind, "message.json")).toString()
            val inner = Json.parseToJsonElement(text).jsonObject.getValue("message").jsonObject
            val messageKey = inner.getValue("key").jsonObject.getValue("key").jsonObject
            val pointer = buildJsonObject {
                put("PointerMessage", buildJsonObject {
                    put("key", inner.getValue("key"))
                    put("data", "")
                    put("record", true)
                    put("target", buildJsonObject {
                        put("key", buildJsonObject {
                            put("id", messageKey.getValue("id"))
                            put("root", messageKey.getValue("root"))
                            put("empty", false)
                        })
                    })
                })
            }.toString()

            val items = decodeHistory("$text\n$pointer\n", kind)

            assertEquals(2, items.size)
            assertEquals(decodeMessage(text, kind), items[0])
            val item = items[1]
            assertEquals(setOf("kind", "messageKey", "senderId", "topicId", "timestamp", "targetId", "targetRoot"), item.keys)
            assertEquals("pointer", item.getValue("kind").jsonPrimitive.content)
            assertEquals(items[0].getValue("messageKey").jsonObject.getValue("id"), item.getValue("targetId"))
            assertEquals(items[0].getValue("messageKey").jsonObject.getValue("root"), item.getValue("targetRoot"))
        }
    }
```

Run it:

```bash
mvn -o -B -pl chat-mcp test -Dtest=MessageEnvelopeTests > $LOG 2>&1; echo exit=$?; grep -E "Tests run:|ClientException" $LOG | head -4
```

Expected: exit 1. The new test fails with `ClientException`, because the decoder refuses the `PointerMessage` wrapper.

- [ ] **Step 1: Print a pointer in the shell**

In `PubSubCommands.kt`, change the constructor parameter `rootKeys: RootKeys<T>,` to `private val roots: RootKeys<T>,`, and pass `roots` to `CommandsUtil<T>(typeUtil, roots)`. Add:

```kotlin
    /** A pointer shows its target and no text. No read of the target runs. See `CHAT-erkoxybm`. */
    // The static type Message<T, String> fixes T, so `is PointerMessage<T>` compiles.
    // Measured with kotlinc, K1 and K2, on 2026-10-10. A subject of type Any fails.
    private fun body(message: Message<T, String>): String =
        if (message is PointerMessage<T>) {
            val domain = when (roots.domainOfRoot(message.target.root)) {
                ChatDomain.MESSAGE_TOPIC -> "topic"
                else -> "message"
            }
            "→ $domain ${typeUtil.toString(message.target.id)}"
        } else message.data
```

In `messages`, replace `message.data.lines()` with `body(message).lines()`. In `listen`, replace `${message.data}` with `${body(message)}`. Import `com.demo.chat.domain.Message`, `com.demo.chat.domain.PointerMessage`, and `com.demo.chat.domain.knownkey.ChatDomain`.

- [ ] **Step 2: Decode a pointer in MCP**

In `MessageEnvelope.kt`, change the first two lines of `decodeMessage` to:

```kotlin
fun decodeMessage(body: String, keyType: KeyType): JsonObject {
    val document = wireDocument(body)
    // A pointer has its own wrapper. Before CHAT-erkoxybm, one pointer refused the whole history.
    document["PointerMessage"]?.let { return decodePointer(wireObject(it), keyType) }
    val message = wireObject(wireField(document, "message"))
```

Add:

```kotlin
/**
 * A pointer item. It carries the target id and the target root, and no text.
 * The adapter holds no root keys, so it cannot name the target domain. See
 * `CHAT-erkoxybm`.
 */
private fun decodePointer(message: JsonObject, keyType: KeyType): JsonObject {
    val key = wireMessageKey(wireField(message, "key"), keyType)
    val target = wireObject(wireField(wireObject(wireField(message, "target")), "key"))
    return buildJsonObject {
        put("kind", "pointer")
        put("messageKey", buildJsonObject {
            put("id", key.getValue("id"))
            put("root", key.getValue("root"))
        })
        put("senderId", key.getValue("from"))
        put("topicId", key.getValue("dest"))
        put("timestamp", key.getValue("timestamp"))
        put("targetId", wireId(wireField(target, "id"), keyType))
        put("targetRoot", wireId(wireField(target, "root"), keyType))
    }
}
```

- [ ] **Step 3: Allow the pointer item in the output schema**

In `MessagingSchemas.kt`, rename `messageShape` to `textMessageShape`, and add:

```kotlin
private fun pointerMessageShape(): JsonObject = objectShape(buildJsonObject {
    put("kind", enumShape(listOf("pointer")))
    put("messageKey", keyShape())
    put("senderId", stringShape())
    put("topicId", stringShape())
    put("timestamp", timeShape())
    put("targetId", stringShape())
    put("targetRoot", stringShape())
})

/** A text item or a pointer item. See `CHAT-erkoxybm`. */
private fun messageShape(): JsonObject = buildJsonObject {
    put("anyOf", JsonArray(listOf(textMessageShape(), pointerMessageShape())))
}
```

The two existing callers of `messageShape()` stay unchanged.

- [ ] **Step 4: Run the shell and MCP suites**

```bash
mvn -o -B -pl chat-shell,chat-mcp -am test > $LOG 2>&1; echo exit=$?; grep -E "Tests run:.*Fail|FAIL|BUILD" $LOG | tail -4
```

Expected: exit 0, and `a history with a pointer returns the text item and the pointer item` passes. If a `chat-mcp` test pins the old message schema text, update that test to the `anyOf` shape and name it in the commit message.

Mutation: in `decodeMessage`, remove the `document["PointerMessage"]` line. Run `MessageEnvelopeTests` again. Expected: only the new test fails. Restore the line by hand, and confirm it with `git diff`.

- [ ] **Step 5: Describe the item**

In `docs/MCP-ADAPTER.md`, in the section that describes a message item, add: `A pointer item carries kind "pointer", targetId and targetRoot, and no text. The adapter holds no root keys, so it does not name the target domain. A history can hold text items and pointer items. See CHAT-erkoxybm.` Run `drift check`, and follow the Task 7 Step 11 procedure if it reports STALE.

- [ ] **Step 6: Commit**

```bash
git add chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommands.kt \
  chat-mcp/src/main/kotlin/com/demo/chat/mcp/client/MessageEnvelope.kt \
  chat-mcp/src/main/kotlin/com/demo/chat/mcp/tool/MessagingSchemas.kt \
  chat-mcp/src/test/kotlin/com/demo/chat/mcp/client/MessageEnvelopeTests.kt \
  docs/MCP-ADAPTER.md
# Add drift.lock only if Step 5 refreshed it. Add each MCP test that Step 4 changed, by its path.
git commit -m "Show pointers in the shell and MCP (CHAT-erkoxybm)

The shell prints the target domain and id. MCP decodes a PointerMessage
item with the target id and root. One pointer no longer refuses a whole
MCP history.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
fp comment CHAT-erkoxybm "Task 10 done: shell pointer lines, MCP pointer item and schema."
```

---

### Task 11: Documents and gates

**Files:**
- Modify: `docs/MESSAGE-COMMANDS.md`
- Modify: `forward-register.md`

- [ ] **Step 1: Describe the pointer command**

In `docs/MESSAGE-COMMANDS.md`, after the `Submit` section, add:

```markdown
## Pointer

A pointer names one message or one topic. It carries no text. See `CHAT-erkoxybm`.

| Transport | Route | Request ID |
|---|---|---|
| REST | `POST /message/pointer/{roomId}`, JSON body `{"targetDomain": "Message", "targetId": "123"}` | Header `Idempotency-Key` |
| RSocket | `message.message-pointer` with `MessagePointerRequest` | Field `requestId` |

The caller needs `FORWARD` on the room. A join grants it, and a leave expires it.

The caller must be able to read the target. A message target needs `SUBSCRIBE` on its room. A topic target needs `SUBSCRIBE` on the topic.

A missing target and an unreadable target both answer 404, with `no such message` or `no such topic`. A pointer to a pointer answers 400.

A pointer command uses the backends `P`, `I`, and `U`. It has no `V` obligation, so its status lists no `V`. The caller waits for the configured requirement without `V`.

The outcomes and status codes are the same as for `submit`.
```

- [ ] **Step 2: Run the default gate**

```bash
shell-scripts/build-health.sh > $LOG 2>&1; echo exit=$?; tail -15 $LOG
```

Expected: exit 0 and no drift. The test count rises. The verifier does not compare counts, so read the totals and record them.

- [ ] **Step 3: Run the CI gate**

```bash
DOCKER_CONFIG=$(mktemp -d) shell-scripts/build-health.sh --ci > $LOG 2>&1; echo exit=$?; tail -20 $LOG
```

Expected: exit 0, no drift, and `agent http gate: ok`. Read `docker events --filter event=oom --since 2h` before you trust a red Cassandra result. Record the shell image id. It must change in this run.

- [ ] **Step 4: Record the work in the register**

Add a section `## Message pointers (2026-10-10)` to `forward-register.md`. Write it in the style of the sections before it. Include:
- the branch and the issue;
- the eleven owner decisions, in one line each;
- the `messageById` behavior change and the removed `{Anon, Message, GET}` row;
- the measured totals of both gates and the image id;
- the three mutations of Task 8 and the mutation of Task 7;
- the risks of the spec that stay open.

Do not move the `Checkout` row. See the rule under `Where things stand`.

- [ ] **Step 5: Check the drift and the whitespace**

```bash
drift check; git diff --check; echo exit=$?
```

Expected: `ok`, and exit 0.

- [ ] **Step 6: Commit**

```bash
git add docs/MESSAGE-COMMANDS.md forward-register.md
git commit -m "Record message pointers in the documents (CHAT-erkoxybm)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
fp comment CHAT-erkoxybm "Task 11 done: documents, register, both gates green. Totals are in the register."
```

- [ ] **Step 7: Hand off**

Report the gate totals to the owner, and stop. Ask the owner whether to push the branch and open a pull request. Do not push and do not open a pull request without that answer. If the owner says yes, use superpowers:finishing-a-development-branch.

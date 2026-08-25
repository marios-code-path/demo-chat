# Domain Serialization: Drop the Dead Wrapper — Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Project rule: no sub-agent driven development.

**Goal:** Remove the dead `@JsonTypeInfo(WRAPPER_OBJECT)` wrapper from `User`, `MessageTopic`, and `TopicMembership`. Delete the redis `rebind` workaround. Update the REST contract test.

**Architecture:** Drop the two annotations from the three no-subtype domain interfaces. The custom deserializers already read the flat shape, so they stay. Delete `rebind` in `KeyValuePersistenceRedis` and use plain `convertValue`. Update the REST test assertions and add wire-shape tests that pin the new format.

**Tech Stack:** Kotlin, Jackson, Spring Boot, JUnit 5, AssertJ, Maven, Testcontainers (redis).

**Spec:** `docs/superpowers/specs/2026-08-25-domain-serialization-design.md`

## Global Constraints

- Run `mvn -o -pl chat-core,<module> test`. Never `-pl <module>` alone.
- Keep the annotations on `Key`, `Message`, `KeyValuePair`. They have subtypes.
- Do not touch the six E2EE types in `EncryptedEnvelope.kt`.
- Do not touch `RequestResponse`. It uses `As.PROPERTY`, a different mechanism.
- Controlled English for new prose and comments.
- Expected counts: chat-core 43, chat-client-rsocket 57, chat-persistence-redis 46. chat-webflux has 2 pre-existing failures. No new failures.
- Drift discipline: check bindings before editing covered files. Update prose first, then `drift link`.

---

### Task 1: Drop the annotations from the three domain types

**Files:**
- Create: `chat-core/src/test/kotlin/com/demo/chat/test/serializers/DomainWireShapeTests.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/User.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/MessageTopic.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/TopicMembership.kt`

**Interfaces:**
- Consumes: `TestBase.mapper`, `DefaultChatJacksonModules().allModules()`, `User.create`, `MessageTopic.create`, `TopicMembership.create`, `Key.funKey`.
- Produces: flat wire shapes for the three types. Task 2 and Task 3 rely on the new shapes.

Observed post-change shapes (verified by spike):
- `User`: `{"name":"MOON","key":{"key":{"id":1,"empty":false}},"handle":"LUNA","timestamp":...,"imageUri":"http://"}`
- `MessageTopic`: `{"keyValue":{"key":{"key":{"id":1,"empty":false}},"data":"MOON"}}`
- `TopicMembership`: `{"key":1,"memberOf":3,"member":2}`

Note: `MessageTopic` extends `KeyValuePair`, which keeps its own `@JsonTypeInfo`. Once `MessageTopic` loses its annotation, Jackson annotation inheritance applies the `KeyValuePair` wrapper. So `MessageTopic` is NOT flat. It carries the `keyValue` wrapper. The test asserts this.

- [ ] **Step 1: Write the failing test**

Create `chat-core/src/test/kotlin/com/demo/chat/test/serializers/DomainWireShapeTests.kt`:

```kotlin
package com.demo.chat.test.serializers

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.test.TestBase
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DomainWireShapeTests : TestBase() {

    private fun shapeNode(obj: Any): JsonNode {
        mapper.apply { registerModules(DefaultChatJacksonModules().allModules()) }
        return mapper.readTree(mapper.writeValueAsString(obj))
    }

    @Test
    fun `User serialises flat without the user wrapper`() {
        val node = shapeNode(User.create(Key.funKey(1L), "MOON", "LUNA", "http://"))
        Assertions.assertFalse(node.has("user"), "User must not carry the user wrapper")
        Assertions.assertEquals("MOON", node.get("name").asText())
        Assertions.assertEquals("LUNA", node.get("handle").asText())
        Assertions.assertEquals("http://", node.get("imageUri").asText())
        Assertions.assertTrue(node.get("key").has("key"), "Key must stay wrapped")
    }

    @Test
    fun `TopicMembership serialises flat without the membership wrapper`() {
        val node = shapeNode(TopicMembership.create(1L, 2L, 3L))
        Assertions.assertFalse(node.has("membership"), "TopicMembership must not carry the membership wrapper")
        Assertions.assertEquals(1L, node.get("key").asLong())
        Assertions.assertEquals(2L, node.get("member").asLong())
        Assertions.assertEquals(3L, node.get("memberOf").asLong())
    }

    @Test
    fun `MessageTopic serialises with the inherited keyValue wrapper`() {
        val node = shapeNode(MessageTopic.create(Key.funKey(1L), "MOON"))
        Assertions.assertTrue(node.has("keyValue"), "MessageTopic inherits the keyValue wrapper")
        Assertions.assertFalse(node.has("topic"), "MessageTopic must not carry the topic wrapper")
        Assertions.assertEquals("MOON", node.get("keyValue").get("data").asText())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -o -pl chat-core test -Dtest=DomainWireShapeTests`
Expected: FAIL. The three tests fail because the current shapes carry the old wrappers (`user`, `topic`, `membership`).

- [ ] **Step 3: Remove the annotations from User.kt**

In `chat-core/src/main/kotlin/com/demo/chat/domain/User.kt`, remove the two imports and the two annotations.

Before:
```kotlin
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("user")
interface User<T> : KeyBearer<T> {
```

After:
```kotlin
import java.time.Instant
import java.util.*

interface User<T> : KeyBearer<T> {
```

- [ ] **Step 4: Remove the annotations from MessageTopic.kt**

In `chat-core/src/main/kotlin/com/demo/chat/domain/MessageTopic.kt`, remove the `JsonTypeInfo` import and the two annotations. KEEP the `JsonTypeName` import. The data classes `TopicMetaData`, `TopicMember`, `TopicMemberships` use it.

Before:
```kotlin
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.util.*

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("topic")
interface MessageTopic<T> : KeyValuePair<T, String> {
```

After:
```kotlin
import com.fasterxml.jackson.annotation.JsonTypeName
import java.util.*

interface MessageTopic<T> : KeyValuePair<T, String> {
```

- [ ] **Step 5: Remove the annotations from TopicMembership.kt**

In `chat-core/src/main/kotlin/com/demo/chat/domain/TopicMembership.kt`, remove the two imports and the two annotations.

Before:
```kotlin
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

// I'd like to make memberships cross relational in the chat domain, thus
// I've parameterized the types for Member, and Member-Of
// Database Key = DK
// Member Key = MK
// topic Key = TK
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("membership")
interface TopicMembership<T> {
```

After:
```kotlin
// I'd like to make memberships cross relational in the chat domain, thus
// I've parameterized the types for Member, and Member-Of
// Database Key = DK
// Member Key = MK
// topic Key = TK
interface TopicMembership<T> {
```

- [ ] **Step 6: Run the wire-shape tests to verify they pass**

Run: `mvn -o -pl chat-core test -Dtest=DomainWireShapeTests`
Expected: PASS. All three tests pass.

- [ ] **Step 7: Run the full chat-core suite**

Run: `mvn -o -pl chat-core test`
Expected: 43 tests pass. No new failures. The existing round-trip tests (`UserSerliazerTests`, `TopicSerializerTests`, `TopicMembershipSerializerTests`) still pass because the custom deserializers read the flat shape.

- [ ] **Step 8: Commit**

```bash
git add chat-core/src/test/kotlin/com/demo/chat/test/serializers/DomainWireShapeTests.kt \
        chat-core/src/main/kotlin/com/demo/chat/domain/User.kt \
        chat-core/src/main/kotlin/com/demo/chat/domain/MessageTopic.kt \
        chat-core/src/main/kotlin/com/demo/chat/domain/TopicMembership.kt
git commit -m "drop dead json wrapper from user, topic, membership"
```

---

### Task 2: Delete the redis rebind workaround

**Files:**
- Modify: `chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/KeyValuePersistenceRedis.kt`

**Interfaces:**
- Consumes: `objectMapper.convertValue` (already a field on the class).
- Produces: plain `convertValue` in `typedGet`, `typedAll`, `typedByIds`. No `rebind`.

- [ ] **Step 1: Replace the three call sites**

In `KeyValuePersistenceRedis.kt`, replace `rebind(...)` with `objectMapper.convertValue(...)` in the three typed accessors.

Before:
```kotlin
    override fun <E> typedGet(key: Key<T>, typeArgument: Class<E>): Mono<KeyValuePair<T, E>> =
        get(key).map { kv -> KeyValuePair.create(kv.key, rebind(kv.data, typeArgument)) }

    override fun <E> typedAll(typeArgument: Class<E>): Flux<KeyValuePair<T, E>> =
        all().map { kv -> KeyValuePair.create(kv.key, rebind(kv.data, typeArgument)) }

    override fun <E> typedByIds(ids: List<Key<T>>, typedArgument: Class<E>): Flux<KeyValuePair<T, E>> =
        byIds(ids).map { kv -> KeyValuePair.create(kv.key, rebind(kv.data, typedArgument)) }
```

After:
```kotlin
    override fun <E> typedGet(key: Key<T>, typeArgument: Class<E>): Mono<KeyValuePair<T, E>> =
        get(key).map { kv -> KeyValuePair.create(kv.key, objectMapper.convertValue(kv.data, typeArgument)) }

    override fun <E> typedAll(typeArgument: Class<E>): Flux<KeyValuePair<T, E>> =
        all().map { kv -> KeyValuePair.create(kv.key, objectMapper.convertValue(kv.data, typeArgument)) }

    override fun <E> typedByIds(ids: List<Key<T>>, typedArgument: Class<E>): Flux<KeyValuePair<T, E>> =
        byIds(ids).map { kv -> KeyValuePair.create(kv.key, objectMapper.convertValue(kv.data, typedArgument)) }
```

- [ ] **Step 2: Delete the rebind method and its KDoc**

Delete the `rebind` method and its KDoc block (the `/** ... */` comment and the `private fun <E> rebind(...)` function). The class body ends at the closing brace after `typedByIds`.

- [ ] **Step 3: Remove the now-unused imports**

Remove the two imports that only `rebind` used:
```kotlin
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
```

- [ ] **Step 4: Run the redis suite including the integration typed domain test**

Run: `mvn -o -pl chat-core,chat-persistence-redis test -Pintegration`
Expected: the redis suite passes. The `RedisKeyValueTypedDomainTests` (typed domain test) passes with plain `convertValue`. It needs Docker for the Testcontainers Redis.

- [ ] **Step 5: Commit**

```bash
git add chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/KeyValuePersistenceRedis.kt
git commit -m "delete redis rebind workaround"
```

---

### Task 3: Update the REST contract test

**Files:**
- Modify: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/UserRestTestBase.kt`

**Interfaces:**
- Consumes: the flat `User` wire shape from Task 1.
- Produces: REST test assertions that match the flat shape.

- [ ] **Step 1: Update the two jsonPath assertions**

In `UserRestTestBase.kt`, update the two assertions that pin the old wrapper.

In `should find user by name`, before:
```kotlin
            .jsonPath("$.[0].user.name").isNotEmpty
```
After:
```kotlin
            .jsonPath("$.[0].name").isNotEmpty
```

In `should find user by id`, before:
```kotlin
            .jsonPath("$.user.name").isNotEmpty
```
After:
```kotlin
            .jsonPath("$.name").isNotEmpty
```

- [ ] **Step 2: Run the webflux suite**

Run: `mvn -o -pl chat-core,chat-webflux test`
Expected: the 2 pre-existing failures stay. No new failures. `LongUserRestTests` passes with the updated assertions.

- [ ] **Step 3: Commit**

```bash
git add chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/UserRestTestBase.kt
git commit -m "update rest contract to flat user shape"
```

---

### Task 4: Update docs, create the E2EE follow-up, check drift

**Files:**
- Modify: `forward-register.md`
- Create: E2EE follow-up issue (via `fp`)

**Interfaces:**
- Consumes: the completed change from Tasks 1-3.
- Produces: updated forward register, E2EE follow-up issue, verified drift.

- [ ] **Step 1: Update forward-register.md**

Move `CHAT-gjggodpa` out of the deferred table. Record the E2EE follow-up issue. Note the `MessageTopic` inheritance outcome: it carries the `keyValue` wrapper, not flat.

- [ ] **Step 2: Create the E2EE follow-up issue**

Run:
```bash
fp issue create --title "Drop the dead json wrapper from the six e2ee types" --parent CHAT-gjggodpa
```

The six types: `DeviceRegistration`, `PreKeyBundle`, `EncryptedEnvelope`, `ConversationEpoch`, `FrankingTag`, `Presence` in `chat-core/src/main/kotlin/com/demo/chat/domain/EncryptedEnvelope.kt`.

- [ ] **Step 3: Check drift bindings on the edited files**

Run:
```bash
drift refs chat-core/src/main/kotlin/com/demo/chat/domain/User.kt
drift refs chat-core/src/main/kotlin/com/demo/chat/domain/MessageTopic.kt
drift refs chat-core/src/main/kotlin/com/demo/chat/domain/TopicMembership.kt
drift refs chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/KeyValuePersistenceRedis.kt
drift refs chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/UserRestTestBase.kt
```

If any file has a binding and the prose is stale, update the prose first. Then refresh provenance with `drift link`. Verify with `drift check`.

- [ ] **Step 4: Commit**

```bash
git add forward-register.md
git commit -m "record e2ee follow-up, update forward register"
```

---

## Final verification

Run the full build health:
```bash
./shell-scripts/build-health.sh --integration
```
Expected: no new failures beyond the 2 pre-existing chat-webflux failures.

Attach the commits to the issue and mark it done:
```bash
fp issue assign CHAT-gjggodpa --rev <commit-shas>
fp issue update --status done CHAT-gjggodpa
```
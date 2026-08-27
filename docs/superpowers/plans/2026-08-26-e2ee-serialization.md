# E2EE Serialization Wrapper Removal — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:executing-plans to implement this plan task-by-task. Subagent-driven development is forbidden by AGENTS.md. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drop the dead `@JsonTypeInfo(WRAPPER_OBJECT)` wrapper from the seven E2EE types so they serialize flat, matching the user/topic/membership fix in PR #48.

**Architecture:** The seven types in `EncryptedEnvelope.kt` carry `@JsonTypeInfo(WRAPPER_OBJECT, Id.NAME)` plus `@JsonTypeName(...)`. None has `@JsonSubTypes`. None has a custom (de)serializer. So the wrapper adds a redundant outer object on serialize and enables no polymorphic resolve. Removing the two annotations and the dead imports makes each type serialize flat. Wire-shape tests lock the new shape.

**Tech Stack:** Kotlin, Jackson (jackson-databind, jackson-module-kotlin), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-25-domain-serialization-design.md` (PR #48) and fp issue `CHAT-zbjzbcoy`.

## Global Constraints

- Kotlin is the primary language.
- Commit messages are lowercase imperative with no prefix. Match `drop dead json wrapper from user, topic, membership`.
- Run tests as `mvn -o -pl chat-core test`. Never run a dependent module alone. See the forward register, "Things worth not relearning".
- Do not edit `forward-register.md` on this branch. PR #48 owns that update. Editing here would conflict on merge.

## Verified before planning

- All seven types carry only `@JsonTypeInfo` and `@JsonTypeName`. None has `@JsonSubTypes`.
- `EncryptedEnvelope` has no subtypes and no custom mechanism. This clears the issue's "verify before dropping" note.
- No custom (de)serializer for the seven types exists in `JacksonModules` or `ChatDeserializers`. Nothing to keep.
- `Key<T>` serializes wrapped as `{"key": {"id":…, "empty":…}}`. It must stay wrapped.

## File Structure

- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/EncryptedEnvelope.kt`. Drop the two annotations from all seven types. Remove the three dead imports (`JsonSubTypes`, `JsonTypeInfo`, `JsonTypeName`).
- Create: `chat-core/src/test/kotlin/com/demo/chat/test/serializers/E2eeWireShapeTests.kt`. One wire-shape test per type.
- Create: `docs/superpowers/plans/2026-08-26-e2ee-serialization.md`. This plan.

## The seven types

| Type | Wrapper name | Extends | Representative field |
|------|-------------|---------|---------------------|
| `DeviceRegistration<T>` | `device` | `KeyBearer<T>` | `registrationId: Int` |
| `PreKeyBundle<T>` | `preKeyBundle` | `KeyBearer<T>` | `preKeyId: Int` |
| `EncryptedEnvelope<T>` | `encryptedEnvelope` | `KeyBearer<T>` | `seq: Long`, `messageKind: MessageKind` |
| `ConversationCursor<T>` | `conversationCursor` | — | `nextSeq: Long` |
| `ConversationEpoch<T>` | `conversationEpoch` | `KeyBearer<T>` | `epoch: Int` |
| `FrankingTag<T>` | `frankingTag` | `KeyBearer<T>` | `frankingKeyId: Int` |
| `Presence<T>` | `presence` | — | `state: PresenceState` |

---

### Task 1: Write the failing wire-shape tests

**Files:**
- Create: `chat-core/src/test/kotlin/com/demo/chat/test/serializers/E2eeWireShapeTests.kt`

**Interfaces:**
- Consumes: the seven `Factory.create(...)` methods in `EncryptedEnvelope.kt`; `TestBase.mapper`; `DefaultChatJacksonModules().allModules()`.
- Produces: `E2eeWireShapeTests` with seven `@Test` methods.

- [ ] **Step 1: Create the test file**

```kotlin
package com.demo.chat.test.serializers

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.domain.*
import com.demo.chat.test.TestBase
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class E2eeWireShapeTests : TestBase() {

    private fun shapeNode(obj: Any): JsonNode {
        mapper.apply { registerModules(DefaultChatJacksonModules().allModules()) }
        return mapper.readTree(mapper.writeValueAsString(obj))
    }

    @Test
    fun `DeviceRegistration serialises flat without the device wrapper`() {
        val node = shapeNode(
            DeviceRegistration.create(
                Key.funKey(1L), Key.funKey(2L), 42,
                byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), 7
            )
        )
        Assertions.assertFalse(node.has("device"), "DeviceRegistration must not carry the device wrapper")
        Assertions.assertEquals(42, node.get("registrationId").asInt())
        Assertions.assertTrue(node.get("userId").has("key"), "Key must stay wrapped")
    }

    @Test
    fun `PreKeyBundle serialises flat without the preKeyBundle wrapper`() {
        val node = shapeNode(
            PreKeyBundle.create(
                Key.funKey(1L), Key.funKey(2L), Key.funKey(3L), 9,
                byteArrayOf(1), 4, byteArrayOf(2), byteArrayOf(3), byteArrayOf(4)
            )
        )
        Assertions.assertFalse(node.has("preKeyBundle"), "PreKeyBundle must not carry the preKeyBundle wrapper")
        Assertions.assertEquals(9, node.get("preKeyId").asInt())
    }

    @Test
    fun `EncryptedEnvelope serialises flat without the encryptedEnvelope wrapper`() {
        val node = shapeNode(
            EncryptedEnvelope.create(
                Key.funKey(1L), Key.funKey(2L), Key.funKey(3L), Key.funKey(4L), Key.funKey(5L),
                5L, MessageKind.PAIRWISE, byteArrayOf(1)
            )
        )
        Assertions.assertFalse(node.has("encryptedEnvelope"), "EncryptedEnvelope must not carry the encryptedEnvelope wrapper")
        Assertions.assertEquals(5L, node.get("seq").asLong())
        Assertions.assertEquals("PAIRWISE", node.get("messageKind").asText())
    }

    @Test
    fun `ConversationCursor serialises flat without the conversationCursor wrapper`() {
        val node = shapeNode(ConversationCursor.create(Key.funKey(1L), 3L))
        Assertions.assertFalse(node.has("conversationCursor"), "ConversationCursor must not carry the conversationCursor wrapper")
        Assertions.assertEquals(3L, node.get("nextSeq").asLong())
    }

    @Test
    fun `ConversationEpoch serialises flat without the conversationEpoch wrapper`() {
        val node = shapeNode(ConversationEpoch.create(Key.funKey(1L), Key.funKey(2L), 2))
        Assertions.assertFalse(node.has("conversationEpoch"), "ConversationEpoch must not carry the conversationEpoch wrapper")
        Assertions.assertEquals(2, node.get("epoch").asInt())
    }

    @Test
    fun `FrankingTag serialises flat without the frankingTag wrapper`() {
        val node = shapeNode(
            FrankingTag.create(
                Key.funKey(1L), Key.funKey(2L), 6L, Key.funKey(3L),
                MessageKind.SENDER_KEY, byteArrayOf(1), 11
            )
        )
        Assertions.assertFalse(node.has("frankingTag"), "FrankingTag must not carry the frankingTag wrapper")
        Assertions.assertEquals(11, node.get("frankingKeyId").asInt())
    }

    @Test
    fun `Presence serialises flat without the presence wrapper`() {
        val node = shapeNode(Presence.create(Key.funKey(1L), Key.funKey(2L), PresenceState.ONLINE))
        Assertions.assertFalse(node.has("presence"), "Presence must not carry the presence wrapper")
        Assertions.assertEquals("ONLINE", node.get("state").asText())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -o -pl chat-core test -Dtest=E2eeWireShapeTests`
Expected: FAIL. Seven failures. Each `assertFalse(node.has("<wrapper>"))` fails because the wrapper is still present.

### Task 2: Drop the wrapper annotations

**Files:**
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/EncryptedEnvelope.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: the seven types serialize flat. `E2eeWireShapeTests` passes.

- [ ] **Step 1: Remove the two annotations from all seven types**

For each of the seven types, delete these two lines (they sit directly above the `interface` declaration):

```kotlin
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("<wrapper-name>")
```

The wrapper names, in file order: `device`, `preKeyBundle`, `encryptedEnvelope`, `conversationCursor`, `conversationEpoch`, `frankingTag`, `presence`.

- [ ] **Step 2: Remove the three dead imports**

Delete these lines near the top of the file (none is used after Step 1):

```kotlin
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
```

- [ ] **Step 3: Run the wire-shape tests to verify they pass**

Run: `mvn -o -pl chat-core test -Dtest=E2eeWireShapeTests`
Expected: PASS. Seven tests, zero failures.

- [ ] **Step 4: Run the full chat-core suite to check for regressions**

Run: `mvn -o -pl chat-core test`
Expected: green. No new failures.

- [ ] **Step 5: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/EncryptedEnvelope.kt \
        chat-core/src/test/kotlin/com/demo/chat/test/serializers/E2eeWireShapeTests.kt
git commit -m "drop dead json wrapper from the seven e2ee types"
```

## Deferred

- The forward register note marking the E2EE types as flat belongs on `master` after PR #48 and this PR both land. It is not done on this branch to avoid a merge conflict with PR #48's forward register update.
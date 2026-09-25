# Root Identity on Keys Implementation Plan

> **For agentic workers:** Execute this plan inline with
> superpowers:executing-plans. **Do not use subagent-driven development.**
> `AGENTS.md` forbids it. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every `Key<T>` carries a required, verified root, and the key service
owns stable root keys per domain and key type.

**Architecture:** A closed `ChatDomain` type replaces class names. A root key
store per backend creates one root per domain with a conditional write. `Key`
gains `root`, and three canonical classes carry one equality rule. Mint takes a
`ChatDomain`. Every inbound key or id is verified or resolved through the key
registry before authorization or persistence.

**Tech Stack:** Kotlin 2.4, Spring Boot 4.0.8, Reactor 3.8, Jackson 2 and 3,
Spring Data Cassandra, Spring Data Redis (Lettuce), JUnit 6, Testcontainers 2.

**Spec:** `docs/superpowers/specs/2026-09-24-key-root-identity-design.md`,
approved on 2026-09-25. Issue `CHAT-avduuqwp`, child `CHAT-bafkgkko`.

## Global Constraints

- `Key<T>` is `{ id: T, root: T }`. No key exists without a root at the end of
  the plan.
- No migration, no backfill, no reader for a payload without `root`, no class
  name fallback. Old development stores are recreated.
- `app.rootkeys.create` is removed.
- Equality compares `id`, `root` and `empty`, in every `Key` implementation.
  `hashCode` matches.
- An inbound key is verified, and an inbound id is resolved, before it reaches
  authorization, the self authority rule, or persistence.
- A mint for `KeyCredential` or the generic `Key` fails with
  `UnsupportedDomainException`. No compile failure and no accidental exception.
- Redis names carry the key type: `chat:keys:<keyType>`,
  `chat:rootkeys:<keyType>`.
- `root_keys` is absent from `truncate-long.cql` and `truncate-uuid.cql`.
- Prose follows `AGENTS.md` controlled English.
- Run `mvn -o -pl chat-core,<module> test`, never `-pl <module>` alone.
- One maven build at a time per worktree.
- The final gate is `shell-scripts/build-health.sh --ci`, because the wire
  shape changes.

## Branch rule

**The branch merges only after Task 12.** Tasks 2 to 10 keep a transitional
`Key.funKey(id)` that sets `root = id`, marked `@Deprecated`. Each task keeps
the reactor compiling and green. Task 10 deletes `funKey` and adds a guard test
that fails if it returns. Never merge a commit between Task 2 and Task 10.

---

## Inventory

Measured on 2026-09-25 at master `3527e7cb`. **Every entry has a root source
before any factory changes.** The sources:

| Code | Source | Trusted |
|---|---|---|
| **M** | mint, from the key service for a `ChatDomain` | yes |
| **S** | a typed store or index read, from the domain of that store | yes |
| **P** | a raw id whose domain its position fixes, when the id came from a trusted source | yes |
| **R** | a raw id or a key from a caller, resolved or verified through the registry with an expected domain | after the lookup |
| **X** | no domain contract: the call is replaced by an explicit refusal | refused |
| **D** | deleted by this plan | none |

### A. `Key` implementations

| # | Class | Module | Today | After | Task |
|---|---|---|---|---|---|
| A1 | `Key.funKey` anonymous object | chat-core | `id` equality | replaced by `SimpleKey` | 2, 10 |
| A2 | `Key.emptyKey` anonymous object | chat-core | class and `id` equality | replaced by `EmptyKey` | 2 |
| A3 | `MessageKey.create(id, from, dest)` anonymous | chat-core | `id` equality | replaced by `SimpleMessageKey` | 2 |
| A4 | `MessageKey.create(id, dest)` anonymous | chat-core | `id` equality | replaced by `SimpleMessageKey` | 2 |
| A5 | `ChatMessageKey` data class | chat-core `RequestResponse.kt` | data equality with `timestamp` | stops implementing `MessageKey`. Converts through `toKey(root)` | 2 |
| A6 | `Admin` data class | chat-core `knownkey` | implements `Key` | stops implementing `Key`. A typed identity accessor replaces it | 1 |
| A7 | `Anon` data class | chat-core `knownkey` | implements `Key` | same as A6 | 1 |
| A8 | `ChatUserKey` | chat-persistence-cassandra | data equality | stops implementing `Key`. The store maps it | 8 |
| A9 | `ChatTopicKey` | chat-persistence-cassandra | data equality | same | 8 |
| A10 | `ChatMessageByIdKey` | chat-persistence-cassandra | data equality | same | 8 |
| A11 | `AuthMetadataIdKey` | chat-persistence-cassandra | data equality | same | 8, 9 |
| A12 | `KVKey` | chat-persistence-cassandra | data equality | same | 8 |
| A13 | `CSKey` | chat-persistence-cassandra | `id`, `kind` | replaced by a `keys (id, root)` row class that does not implement `Key` | 4 |
| A14 | `CredKey` | chat-persistence-cassandra | `id`, `kind` | `kind` removed. Stops implementing `Key` | 8, 11 |
| A15 | `ChatUserHandleKey` | chat-index-cassandra | data equality | stops implementing `Key`. The index maps it | 8 |
| A16 | `ChatTopicNameKey` | chat-index-cassandra | data equality | same | 8 |
| A17 | `ChatMessageByUserKey` | chat-index-cassandra | data equality | same | 8 |
| A18 | `ChatMessageByTopicKey` | chat-index-cassandra | data equality | same | 8 |
| A19 | `ChatKeyValueIndexKey` | chat-index-cassandra | data equality | same | 8 |
| A20 | `ChatKeyValueIndexByIdKey` | chat-index-cassandra | data equality | same | 8 |
| A21 | Four test implementations | test source | various | each moves to `Key.of` or `SimpleKey` | 10 |

**Today equality is asymmetric.** `funKey(7) == ChatUserKey(7)` is true, and
`ChatUserKey(7) == funKey(7)` is false, because the data class requires its
own class. Task 2 closes this for the core classes. Task 8 closes it for
Cassandra, because those classes stop implementing `Key`.

### B. Deserializers and generated constructors

| # | Path | After | Task |
|---|---|---|---|
| B1 | `KeyDeserializer`, Jackson 2, `ChatDeserializers.kt:31` | reads `id` and `root`. Fails without `root` | 6 |
| B2 | `MessageKeyDeserializer`, Jackson 2, `ChatDeserializers.kt:24` | reads `root` too | 6 |
| B3 | `KeyAssembly.key`, Jackson 3, `NodeValueRules.kt:70` | takes `root` | 6 |
| B4 | `ChatJackson3Deserializers.kt:78` | reads `root` | 6 |
| B5 | Spring Data Cassandra constructs A8 to A20 | no longer a `Key`. The store maps rows | 8 |
| B6 | Redis JSON `readValue(json, User::class.java)` in each Redis store | goes through B1 and B2, so `root` is required in stored JSON | 6, 8 |
| B7 | Kafka and Redis pub/sub message payloads | decoded through B1 to B4. **Unmeasured:** Task 6 step 1 proves it with a test per provider | 6 |

### C. Factory call sites in main source

Seventy-seven calls. The "Src" column is the root source.

| # | Site | Src | After | Task |
|---|---|---|---|---|
| C1 | `authorization-server` `KeyValueStoreRegisteredClientRepository.kt:26` | P | `Key.of(id, roots.of(KEY_VALUE_PAIR))`. The id comes from a registered client that this server stored | 10 |
| C2 | `client-consul` `ConsulKVStore.kt:22` | P | `Key.of(key, roots.of(KEY_VALUE_PAIR))`. Consul kv keys are key-value entries | 10 |
| C3 | `core` `KeyService.kt:15` `IKeyGenerator.nextKey()` | D | removed. A generator makes ids, and mint makes keys | 4 |
| C4 | `core` `AccessBroker.kt:13` `hasAccessByKeyId(T, T)` | R | resolves both ids through the registry with no expected domain. An unknown id denies | 7 |
| C5 | `core` `ChatJackson3Deserializers.kt:78` | R | B4. The decoded key is a claim until verified | 6 |
| C6 | `core` `ChatDeserializers.kt:24` | R | B2 | 6 |
| C7 | `core` `ChatDeserializers.kt:43` | R | B1 | 6 |
| C8 | `core` `ChatDeserializers.kt:49` | R | B1 | 6 |
| C9 | `core` `NodeValueRules.kt:72` | R | B3 | 6 |
| C10 | `core` `NodeValueRules.kt:74` | R | B3 | 6 |
| C11 | `core` `GenerateRootKeyInitializer.kt:15` | D | removed with the class. Tests use `RootKeysFixture` | 3 |
| C12 | `crypto` `InMemoryCryptoServices.kt:149` epoch key | P | `Key.of(uuid, roots.of(KEY_VALUE_PAIR))`. Decision point: see Task 10 step 2 | 10 |
| C13 | `crypto` `InMemoryCryptoServices.kt:192` tag key | P | same as C12 | 10 |
| C14 | `deploy` `HttpRootKeyConsumeOnStart.kt:64` | R | the consumer reads `{domain: {id}}` and builds `Key.root(id)`. It verifies nothing, because a process with no store trusts its configured source. Recorded as a trust boundary | 3 |
| C15 | `deploy` `RootKeyConsumerHttp.kt:62` | D | a commented out duplicate of C14. Removed | 3 |
| C16 | `deploy` `InitialUsersService.kt:24` `emptyKey` | P | `Key.empty(placeholder, roots.of(AUTH_METADATA))`. It stands for a grant key before `authorize` mints one | 9 |
| C17 | `deploy` `RootKeyService.kt:25` | P | `Key.of(dataKey, roots.of(KEY_VALUE_PAIR))` | 3 |
| C18 | `deploy` `RootKeyService.kt:38` | R | same as C14 | 3 |
| C19 | `deploy` `RootKeyService.kt:47` | P | same as C17 | 3 |
| C20 | `index-cassandra` `KeyValueIndex.kt:84` | S | `Key.of(row.key.id, roots.of(KEY_VALUE_PAIR))` | 8 |
| C21 | `index-cassandra` `MembershipIndex.kt:59` | D | commented out. Removed | 8 |
| C22 | `index-cassandra` `MembershipIndex.kt:71` | S | `Key.of(it.key, roots.of(TOPIC_MEMBERSHIP))` | 8 |
| C23 | `index-cassandra` `AuthMetadata.kt:25` principal | S | `Key.of(principalId, principalRoot)` from the row | 9 |
| C24 | `index-cassandra` `AuthMetadata.kt:27` target | S | `Key.of(targetId, targetRoot)` from the row | 9 |
| C25 | `index-cassandra` `AuthMetadata.kt:29` key | S | `Key.of(keyId, roots.of(AUTH_METADATA))` | 9 |
| C26 | `index-cassandra` `AuthMetadata.kt:48` | S | same as C23 | 9 |
| C27 | `index-cassandra` `AuthMetadata.kt:50` | S | same as C24 | 9 |
| C28 | `index-cassandra` `AuthMetadata.kt:52` | S | same as C25 | 9 |
| C29 | `index-lucene` `LuceneIndexBeans.kt:27` `stringToKey` | S | one decoder per index, with the domain root of that index | 8 |
| C30 | `index-lucene` `LuceneIndexBeans.kt:43` membership | S | `Key.of(t.key, roots.of(TOPIC_MEMBERSHIP))` | 8 |
| C31 | `index-lucene` `KeyValueLuceneIndex.kt:25` | S | root of `KEY_VALUE_PAIR` | 8 |
| C32 | `index-lucene` `AuthMetaIndexLucene.kt:17` | S | root of `AUTH_METADATA` | 8 |
| C33 | `index-lucene` `IndexEntryEncoder.kt:63` | D | builds a key only to call `toString`. Uses the raw id | 8 |
| C34 | `persistence-cassandra` `AuthMetadata.kt:25` principal | S | from `principal_root` | 9 |
| C35 | `persistence-cassandra` `AuthMetadata.kt:28` target | S | from `target_root` | 9 |
| C36 | `persistence-memory` `MemoryPersistenceServices.kt:36` | S | root of `TOPIC_MEMBERSHIP` | 8 |
| C37 | `persistence-memory` `KeyServiceInMemory.kt:21` | M | mint | 4 |
| C38 | `persistence-redis` `KeyServiceRedis.kt:28` | M | mint | 4 |
| C39 | `service-composite` `MessagingServiceImpl.kt:38` `req.id` | R | `keyService.resolve(req.id, MESSAGE)` | 7 |
| C40 | `service-composite` `MessagingServiceImpl.kt:42` `MessageKey.create(it, …)` | M | `it` is a minted message key. Its root carries over | 10 |
| C41 | `service-composite` `UserServiceImpl.kt:33` | M | `key` is minted. Use it directly | 10 |
| C42 | `service-composite` `UserServiceImpl.kt:54` `req.id` | R | `resolve(req.id, USER)` | 7 |
| C43 | `service-composite` `UserServiceImpl.kt:60` | S | the ids come from the user index. Root of `USER` | 10 |
| C44 | `service-composite` `ComposedJobRecordWriter.kt:35` | P | `record.key` is a minted message key. Root of `MESSAGE` | 10 |
| C45 | `service-composite` `MessageRecallServiceImpl.kt:97` | S | ids come from vector metadata this server wrote. Root of `MESSAGE` | 10 |
| C46 | `service-composite` `TopicServiceImpl.kt:64` `req.id` | R | `resolve(req.id, MESSAGE_TOPIC)` | 7 |
| C47 | `service-composite` `TopicServiceImpl.kt:84` `req.id` | R | same | 7 |
| C48 | `service-composite` `TopicServiceImpl.kt:102` `req.roomId` | R | same | 7 |
| C49 | `service-composite` `TopicServiceImpl.kt:113` | M | `membership.key` comes from a mint. Root of `MESSAGE` for the alert key | 10 |
| C50 | `service-composite` `TopicServiceImpl.kt:132` | M | same | 10 |
| C51 | `service-composite` `TopicServiceImpl.kt:148` `membership.member` | S | the id comes from a stored membership. Root of `USER` | 10 |
| C52 | `service-composite` `MessagingServiceAccess.kt:20` | R | `resolve(req.id, MESSAGE_TOPIC)` before the check | 7 |
| C53 | `service-composite` `MessagingServiceAccess.kt:24` | R | same | 7 |
| C54 | `service-composite` `MessagingServiceAccess.kt:28` `req.dest` | R | same | 7 |
| C55 | `service-composite` `TopicServiceAccess.kt:30` | R | `resolve(…, MESSAGE_TOPIC)` | 7 |
| C56 | `service-composite` `TopicServiceAccess.kt:34` | R | same | 7 |
| C57 | `service-composite` `TopicServiceAccess.kt:38` | R | same | 7 |
| C58 | `service-composite` `TopicServiceAccess.kt:42` | R | same | 7 |
| C59 | `service-composite` `TopicServiceAccess.kt:46` | R | same | 7 |
| C60 | `service-composite` `UserServiceAccess.kt:26` | R | `resolve(req.id, USER)` | 7 |
| C61 | `shell` `TopicCommands.kt:43` `emptyKey` | P | `Key.empty(placeholder, root of MESSAGE)`. The client reads the root from its consumed root keys | 10 |
| C62 | `shell` `TopicCommands.kt:44` identity | P | the identity key the shell holds from login, with its root | 10 |
| C63 | `shell` `UserCommands.kt:46` | R | the shell sends an id. The server resolves it | 10 |
| C64 | `shell` `UserCommands.kt:110` | R | same | 10 |
| C65 | `shell` `UserCommands.kt:140` | R | same | 10 |
| C66 | `shell` `UserCommands.kt:141` | R | same | 10 |
| C67 | `webflux` `PersistenceControllers.kt:50` | M | `key` is minted | 10 |
| C68 | `webflux` `KeyValueStoreRestMapping.kt:31` `req.key` | R | `resolve(req.key, KEY_VALUE_PAIR)` | 7 |
| C69 | `webflux` `IndexRestMapping.kt:20` path id | R | `resolve(id, domainOf(index))` | 7 |
| C70 | `webflux` `PersistenceRestMapping.kt:24` path id | R | `resolve(id, domainOf(store))` | 7 |
| C71 | `webflux` `PersistenceRestMapping.kt:27` path id | R | same | 7 |
| C72 | `webflux` `SecretsRestMapping.kt:17` | X | credentials have no domain contract. `UnsupportedDomainException` | 4 |
| C73 | `webflux` `SecretsRestMapping.kt:22` | X | same | 4 |
| C74 | `webflux` `SecretsRestMapping.kt:38` | X | same | 4 |
| C75 | `webflux` `PubSubRestMapping.kt:53` | M | minted with `MESSAGE` | 4 |
| C76 | `webflux` `IKeyRestMapping.kt:27` path id | R | `resolve(id, null)`. `rem` then refuses a root | 7 |
| C77 | `webflux` `IKeyRestMapping.kt:31` path id | R | same | 7 |

Mint calls with a class today, and their `ChatDomain`:

| Site | Class today | After |
|---|---|---|
| `MembershipPersistenceCassandra.kt:17` | `TopicMembershipByKey` | `TOPIC_MEMBERSHIP` |
| `MembershipPersistenceRedis.kt:34` | `TopicMembership` | `TOPIC_MEMBERSHIP` |
| six `*PersistenceCassandra` and `*PersistenceRedis` stores | their entity class | the matching domain |
| `InMemoryPersistence.kt:20` | `entityClass` | a `ChatDomain` constructor argument |
| `PubSubRestMapping.kt:49` | `MessageKey` | `MESSAGE` |
| `UserCommands.kt:35`, `:136` | `KeyValuePair`, `AuthMetadata` | `KEY_VALUE_PAIR`, `AUTH_METADATA` |
| `UserCommands.kt:57` | `Key` | **X**, refused |
| `SecretsRestMapping.kt:28` | `KeyCredential` | **X**, refused |
| `RootKeysSupplier.kt:15` | each `KnownRootKeys` class | **D**, replaced by Task 3 |

### D. Inbound routes

| # | Surface | Count | After | Task |
|---|---|---|---|---|
| D1 | RSocket `@MessageMapping` methods in `chat-service-controller` that take a `Key` or a request with ids | 31 | a verifying decoder for `Key`. A resolver for request ids | 7 |
| D2 | REST `@PathVariable` ids in `chat-webflux` | 21 | resolved per C68 to C77 | 7 |
| D3 | `IKeyRestMapping.restKey` and `IKeyServiceMapping.key` | 2 | take a `ChatDomain` | 4 |
| D4 | `SpringSecurityAccessBrokerService.hasAccessTo(target)` and `hasAccessToEntity` | 2 | `hasAccessTo` verifies the target before the broker. An entity comes from a store and needs no read | 7 |
| D5 | `AuthMetadataAccessBroker.isSelf` | 1 | runs only on keys that the caller of the broker verified. The broker documents the precondition. Task 7 adds a test that a forged root never reaches it | 7 |

Task 7 step 1 writes a guard test that lists every `@MessageMapping` and
`@PathVariable` in main source and fails on one that has no entry in the
verification registry. So D1 and D2 are enforced by count, not by memory.

---

## Task 0: Commit the inventory

**Files:**
- Modify: `docs/superpowers/plans/2026-09-25-key-root-identity.md`

- [ ] **Step 1: Re-measure the counts at the branch head.**

Run:

```bash
grep -rn --include='*.kt' -E "Key\.funKey\(|MessageKey\.create\(|Key\.emptyKey\(" chat-*/src/main | grep -v /target/ | grep -vc "fun <T>"
```

Expected: `77`. A different count means the tree moved. Update section C
before any other task.

- [ ] **Step 2: Commit.**

```bash
git add docs/superpowers/plans/2026-09-25-key-root-identity.md
git commit -m "Plan: root identity on keys (CHAT-avduuqwp)"
```

---

## Task 1: Typed domains and typed identities

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/knownkey/ChatDomain.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/knownkey/RootKeys.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/knownkey/Admin.kt`, `Anon.kt`
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/access/SpringSecurityAccessBrokerService.kt`
- Modify: the 9 `Admin::class` and `Anon::class` call sites in main source
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/domain/ChatDomainTests.kt`

**Interfaces:**
- Produces: `enum class ChatDomain(val wireName: String)`,
  `ChatDomain.parse(name: String): ChatDomain?`,
  `RootKeys<T>.of(domain: ChatDomain): Key<T>`,
  `RootKeys<T>.admin(): Key<T>`, `RootKeys<T>.anon(): Key<T>`,
  `RootKeys<T>.domainOfRoot(id: T): ChatDomain?`.

- [ ] **Step 1: Write the failing test.**

```kotlin
package com.demo.chat.test.domain

import com.demo.chat.domain.knownkey.ChatDomain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ChatDomainTests {

    @Test
    fun `parse reads each wire name and nothing else`() {
        ChatDomain.entries.forEach { assertThat(ChatDomain.parse(it.wireName)).isEqualTo(it) }
        assertThat(ChatDomain.parse("KeyCredential")).isNull()
        assertThat(ChatDomain.parse("Key")).isNull()
        assertThat(ChatDomain.parse("user")).isNull()
    }

    @Test
    fun `the wire names are the names the access expressions use`() {
        assertThat(ChatDomain.entries.map { it.wireName }).containsExactly(
            "User", "Message", "MessageTopic", "TopicMembership", "AuthMetadata", "KeyValuePair"
        )
    }
}
```

- [ ] **Step 2: Run it and see it fail.**

Run: `mvn -o -q -B -pl chat-core test -Dtest=ChatDomainTests`
Expected: compile failure, `Unresolved reference 'ChatDomain'`.

- [ ] **Step 3: Write `ChatDomain`.**

```kotlin
package com.demo.chat.domain.knownkey

/**
 * Every domain that owns a root key. See `CHAT-avduuqwp`.
 *
 * **The list is closed.** A type with no entry has no root, and a mint for it
 * is refused. `KeyCredential` and the generic `Key` have no entry.
 *
 * [wireName] is the name that access expressions such as
 * `hasAccessToDomain('User', …)` carry. [parse] is the only way text becomes
 * a domain.
 */
enum class ChatDomain(val wireName: String) {
    USER("User"),
    MESSAGE("Message"),
    MESSAGE_TOPIC("MessageTopic"),
    TOPIC_MEMBERSHIP("TopicMembership"),
    AUTH_METADATA("AuthMetadata"),
    KEY_VALUE_PAIR("KeyValuePair");

    companion object {
        fun parse(name: String): ChatDomain? = entries.firstOrNull { it.wireName == name }
    }
}
```

- [ ] **Step 4: Rekey `RootKeys` by `ChatDomain`.** Replace the `String` map
with two typed maps. Remove every `Class` and `String` overload.

```kotlin
class RootKeys<T> {
    private val domains: MutableMap<ChatDomain, Key<T>> = ConcurrentHashMap()
    private var admin: Key<T>? = null
    private var anon: Key<T>? = null

    fun of(domain: ChatDomain): Key<T> = domains[domain] ?: throw ChatException(
        if (domains.isEmpty()) "No root key '${domain.wireName}': the root keys are not loaded."
        else "No root key '${domain.wireName}'. Loaded: ${domains.keys.sorted().joinToString { it.wireName }}"
    )

    fun admin(): Key<T> = admin ?: throw ChatException("The Admin identity is not loaded.")
    fun anon(): Key<T> = anon ?: throw ChatException("The Anon identity is not loaded.")

    fun domainOfRoot(id: T): ChatDomain? = domains.entries.firstOrNull { it.value.id == id }?.key

    fun loadDomains(roots: Map<ChatDomain, Key<T>>) {
        require(roots.keys == ChatDomain.entries.toSet()) {
            "A root key set must name every domain. Missing: ${ChatDomain.entries - roots.keys}"
        }
        domains.putAll(roots)
    }

    fun loadIdentities(admin: Key<T>, anon: Key<T>) {
        this.admin = admin
        this.anon = anon
    }

    fun domains(): Map<ChatDomain, Key<T>> = domains.toMap()
}
```

`loadDomains` refuses a partial set. That is the rule that a node never serves
with a partial set.

- [ ] **Step 5: Stop `Admin` and `Anon` implementing `Key`.** Each becomes a
marker class with no fields. Move the 9 call sites to `rootKeys.admin()` and
`rootKeys.anon()`. `ContextIdentity` reads `rootKeys.anon()`.

- [ ] **Step 6: Parse domain names in the access service.**

```kotlin
fun hasAccessToDomain(domain: String, perm: String): Mono<Boolean> =
    ChatDomain.parse(domain)
        ?.let { access.hasAccessByPrincipal(getSecurityContextPrincipal(), rootKeys.of(it), perm) }
        ?.onErrorReturn(false)
        ?.switchIfEmpty(Mono.just(false))
        ?: Mono.just(false)
```

`hasAccessToDomainByKind(kind: Class<S>, …)` is removed. `IKeyServiceAccess`
moves to `hasAccessToDomain(#domain.wireName, 'NEW')` in Task 4.

- [ ] **Step 7: Run the module tests.**

Run: `mvn -o -q -B -pl chat-core,chat-security test`
Expected: PASS. Fix every compile error by moving the caller to the typed API.

- [ ] **Step 8: Commit.**

```bash
git commit -am "Type the domains and the identities (CHAT-avduuqwp)"
```

---

## Task 2: The non-null key contract

**Files:**
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/KeyValuePair.kt` (the `Key` interface)
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/Message.kt` (`MessageKey`)
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/Keys.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/RequestResponse.kt` (`ChatMessageKey`)
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/domain/KeyEqualityTests.kt`

**Interfaces:**
- Produces: `Key.of(id, root)`, `Key.root(id)`, `Key.empty(placeholder, root)`,
  `MessageKey.of(id, root, from, dest)`, `SimpleKey`, `SimpleMessageKey`,
  `EmptyKey`, `KeyEquality.equals(a, b)`, `KeyEquality.hash(k)`.

- [ ] **Step 1: Write the equality test.**

```kotlin
class KeyEqualityTests {

    @Test
    fun `equality reads id and root`() {
        assertThat(Key.of(1L, 9L)).isEqualTo(Key.of(1L, 9L))
        assertThat(Key.of(1L, 9L)).isNotEqualTo(Key.of(1L, 8L))
        assertThat(Key.of(1L, 9L).hashCode()).isEqualTo(Key.of(1L, 9L).hashCode())
    }

    @Test
    fun `equality is symmetric across every implementation`() {
        val plain: Key<Long> = Key.of(1L, 9L)
        val message: Key<Long> = MessageKey.of(1L, 9L, 2L, 3L)

        assertThat(plain == message).isEqualTo(message == plain)
        assertThat(plain).isEqualTo(message)
        assertThat(plain.hashCode()).isEqualTo(message.hashCode())
    }

    @Test
    fun `a root key is its own root`() {
        assertThat(Key.root(9L).root).isEqualTo(9L)
    }

    @Test
    fun `an empty key equals only an empty key with the same id and root`() {
        assertThat(Key.empty(0L, 9L)).isEqualTo(Key.empty(0L, 9L))
        assertThat(Key.empty(0L, 9L)).isNotEqualTo(Key.of(0L, 9L))
        assertThat(Key.of(0L, 9L)).isNotEqualTo(Key.empty(0L, 9L))
        assertThat(Key.empty(0L, 9L)).isNotEqualTo(Key.empty(0L, 8L))
    }

    @Test
    fun `a chat message key is not a key`() {
        assertThat(ChatMessageKey(1L, 2L, 3L, Instant.EPOCH) as Any).isNotInstanceOf(Key::class.java)
        assertThat(ChatMessageKey(1L, 2L, 3L, Instant.EPOCH).toKey(9L)).isEqualTo(MessageKey.of(1L, 9L, 2L, 3L))
    }
}
```

- [ ] **Step 2: Run it and see it fail.**

- [ ] **Step 3: Change the interface.**

```kotlin
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("key")
@JsonSubTypes(JsonSubTypes.Type(MessageKey::class))
interface Key<T> {
    val id: T
    val root: T
    val empty: Boolean

    companion object Factory {
        @JvmStatic fun <T> of(id: T, root: T): Key<T> = SimpleKey(id, root)
        @JvmStatic fun <T> root(id: T): Key<T> = SimpleKey(id, id)
        @JvmStatic fun <T> empty(placeholder: T, root: T): Key<T> = EmptyKey(placeholder, root)

        @Deprecated("Transitional. Task 10 removes it. It sets root = id, which is wrong for every non-root key.")
        @JvmStatic fun <T> funKey(id: T): Key<T> = SimpleKey(id, id)
    }
}
```

- [ ] **Step 4: Write the three classes and the one rule.**

```kotlin
/** One equality rule for every `Key`. See `CHAT-avduuqwp`. Equality does not verify a root. */
object KeyEquality {
    fun equals(a: Key<*>, other: Any?): Boolean =
        other is Key<*> && other.empty == a.empty && other.id == a.id && other.root == a.root

    fun hash(k: Key<*>): Int = Objects.hash(k.id, k.root, k.empty)
}

@JsonTypeName("key")
class SimpleKey<T>(override val id: T, override val root: T) : Key<T> {
    override val empty: Boolean get() = false
    override fun equals(other: Any?) = KeyEquality.equals(this, other)
    override fun hashCode() = KeyEquality.hash(this)
    override fun toString() = id.toString()
}

@JsonTypeName("key")
class EmptyKey<T>(override val id: T, override val root: T) : NoKey<T> {
    override val empty: Boolean get() = true
    override fun equals(other: Any?) = KeyEquality.equals(this, other)
    override fun hashCode() = KeyEquality.hash(this)
    override fun toString() = id.toString()
}

@JsonTypeName("key")
class SimpleMessageKey<T>(
    override val id: T, override val root: T, override val from: T, override val dest: T
) : MessageKey<T> {
    override val empty: Boolean get() = false
    override fun equals(other: Any?) = KeyEquality.equals(this, other)
    override fun hashCode() = KeyEquality.hash(this)
    override fun toString() = id.toString()
}
```

`MessageKey.create(…)` keeps both overloads for Task 10 and marks them
`@Deprecated`. They build `SimpleMessageKey` with `root = id`. Add
`MessageKey.of(id, root, from, dest)`.

**`from` and `dest` stay out of equality.** A message id names one message. A
`MessageKey` and a `Key` with the same id and root are the same key.

- [ ] **Step 5: `ChatMessageKey` stops implementing `MessageKey`.** It keeps its
fields and its data equality, because it is a request value, not a key. Add:

```kotlin
fun toKey(root: T): MessageKey<T> = MessageKey.of(id, root, from, dest)
```

Move each caller that used it as a `Key` to `toKey(roots.of(MESSAGE).id)`.

- [ ] **Step 6: Run the module tests.**

Run: `mvn -o -q -B -pl chat-core test`
Expected: PASS. Then run the full default reactor, because `Key` gained a
member: `shell-scripts/build-health.sh`. Expected: exit 0. Every Cassandra key
class must add `override val root: T get() = id` until Task 8 removes the
interface from it. Mark each one with a comment that names Task 8.

- [ ] **Step 7: Commit.**

```bash
git commit -am "Give every key a root, and one equality rule (CHAT-avduuqwp)"
```

---

## Task 3: The key service owns the root keys

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/core/RootKeyStore.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/core/RootKeyLoader.kt`
- Create: `chat-persistence-memory/src/main/kotlin/com/demo/chat/persistence/memory/impl/RootKeyStoreInMemory.kt`
- Create: `chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RootKeyStoreRedis.kt`
- Create: `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/RootKeyStoreCassandra.kt`
- Modify: `shared-resources-cassandra/src/main/resources/keyspace-long.cql`, `keyspace-uuid.cql`
- Delete: `RootKeysSupplier.kt`, `GenerateRootKeyInitializer.kt`, `RootKeyConsumerHttp.kt`, `RootKeyInitRunner.kt`, `KnownRootKeys.kt`
- Modify: `chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/init/RootKeyInitializationListeners.kt`
- Modify: `shell-scripts/chat-build` lines 681 to 697
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/key/RootKeyLoaderTests.kt`
- Test: `chat-persistence-redis/src/test/kotlin/com/demo/chat/test/persistence/redis/RootKeyStoreRedisTests.kt`
- Test: `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/RootKeyStoreCassandraTests.kt`
- Create: `chat-core/src/test/kotlin/com/demo/chat/test/key/RootKeysFixture.kt`

**Interfaces:**
- Consumes: `ChatDomain`, `RootKeys.loadDomains`.
- Produces:

```kotlin
interface RootKeyStore<T> {
    /** Every stored root, by domain. */
    fun read(): Mono<Map<ChatDomain, T>>

    /** Writes [id] as the root of [domain] only when none exists. Answers the stored root, which is [id] or the winner. */
    fun createIfAbsent(domain: ChatDomain, id: T): Mono<T>
}

class RootKeyLoader<T>(private val store: RootKeyStore<T>, private val ids: IKeyGenerator<T>) {
    /** Loads every root, and creates each missing one. Fails when any domain has no root after that. */
    fun load(): Mono<Map<ChatDomain, Key<T>>>
}
```

- [ ] **Step 1: Write the loader test against an in-memory store.**

```kotlin
class RootKeyLoaderTests {

    @Test
    fun `an empty store gets one root per domain`() {
        val roots = RootKeyLoader(RootKeyStoreInMemory<Long>(), counter()).load().block()!!

        assertThat(roots.keys).containsExactlyInAnyOrderElementsOf(ChatDomain.entries)
        roots.values.forEach { assertThat(it.root).isEqualTo(it.id) }
    }

    @Test
    fun `a second load reads the stored roots and creates none`() {
        val store = RootKeyStoreInMemory<Long>()
        val first = RootKeyLoader(store, counter()).load().block()!!
        val second = RootKeyLoader(store, counter(start = 1000)).load().block()!!

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `two loaders at once agree on one root per domain`() {
        val store = RootKeyStoreInMemory<Long>()
        val a = RootKeyLoader(store, counter(start = 0))
        val b = RootKeyLoader(store, counter(start = 5000))

        val both = Mono.zip(a.load().subscribeOn(Schedulers.parallel()), b.load().subscribeOn(Schedulers.parallel())).block()!!

        assertThat(both.t1).isEqualTo(both.t2)
    }

    private fun counter(start: Long = 0): IKeyGenerator<Long> =
        AtomicLong(start).let { n -> object : IKeyGenerator<Long> { override fun nextId() = n.incrementAndGet() } }
}
```

- [ ] **Step 2: Run it and see it fail.** `RootKeyLoader` does not exist.

- [ ] **Step 3: Write the loader.**

```kotlin
class RootKeyLoader<T>(private val store: RootKeyStore<T>, private val ids: IKeyGenerator<T>) {

    fun load(): Mono<Map<ChatDomain, Key<T>>> = store.read().flatMap { stored ->
        Flux.fromIterable(ChatDomain.entries)
            .concatMap { domain ->
                stored[domain]?.let { Mono.just(domain to it) }
                    ?: store.createIfAbsent(domain, ids.nextId()).map { domain to it }
            }
            .collectMap({ it.first }, { Key.root(it.second) })
    }.flatMap { roots ->
        if (roots.keys == ChatDomain.entries.toSet()) Mono.just(roots)
        else Mono.error(ChatException("Root keys incomplete: ${ChatDomain.entries - roots.keys}"))
    }
}
```

- [ ] **Step 4: Write the three stores.**

Memory:

```kotlin
class RootKeyStoreInMemory<T> : RootKeyStore<T> {
    private val roots = ConcurrentHashMap<ChatDomain, T>()
    override fun read(): Mono<Map<ChatDomain, T>> = Mono.fromCallable { roots.toMap() }
    override fun createIfAbsent(domain: ChatDomain, id: T): Mono<T> =
        Mono.fromCallable { roots.putIfAbsent(domain, id) ?: id }
}
```

Redis, one hash per key type:

```kotlin
class RootKeyStoreRedis<T : Any>(
    private val template: ReactiveStringRedisTemplate,
    private val typeUtil: TypeUtil<T>,
    keyType: String
) : RootKeyStore<T> {
    private val hash = "chat:rootkeys:$keyType"

    override fun read(): Mono<Map<ChatDomain, T>> = template.opsForHash<String, String>()
        .entries(hash)
        .collectMap({ ChatDomain.parse(it.key) ?: throw ChatException("Unknown domain in $hash: ${it.key}") },
                    { typeUtil.fromString(it.value) })

    override fun createIfAbsent(domain: ChatDomain, id: T): Mono<T> = template.opsForHash<String, String>()
        .putIfAbsent(hash, domain.wireName, typeUtil.toString(id))
        .then(template.opsForHash<String, String>().get(hash, domain.wireName))
        .map { typeUtil.fromString(it) }
}
```

Cassandra, one table per keyspace:

```sql
CREATE TABLE chat_long.root_keys (
    domain varchar,
    id BIGINT,
    PRIMARY KEY (domain)
);
```

`chat_uuid.root_keys` uses `id uuid`. Neither truncate script names the table.

```kotlin
class RootKeyStoreCassandra<T : Any>(private val template: ReactiveCassandraTemplate, private val idClass: Class<T>) : RootKeyStore<T> {

    override fun read(): Mono<Map<ChatDomain, T>> = template.reactiveCqlOperations
        .query("SELECT domain, id FROM root_keys") { row, _ ->
            (ChatDomain.parse(row.getString("domain")!!) ?: throw ChatException("Unknown domain in root_keys")) to row.get("id", idClass)!!
        }
        .collectMap({ it.first }, { it.second })

    override fun createIfAbsent(domain: ChatDomain, id: T): Mono<T> = template.reactiveCqlOperations
        .execute("INSERT INTO root_keys (domain, id) VALUES (?, ?) IF NOT EXISTS", domain.wireName, id)
        .then(template.reactiveCqlOperations.queryForObject("SELECT id FROM root_keys WHERE domain = ?", idClass, domain.wireName))
}
```

- [ ] **Step 5: Write the Redis and Cassandra store tests.** Each runs the
three loader cases above against its container, plus one more: a `long` and a
`uuid` store on one Redis read separate roots.

```kotlin
@Test
fun `two key types on one redis keep separate roots`() {
    val longRoots = RootKeyLoader(RootKeyStoreRedis(template, TypeUtil.LongUtil, "long"), longIds()).load().block()!!
    val uuidRoots = RootKeyLoader(RootKeyStoreRedis(template, TypeUtil.UUIDUtil, "uuid"), uuidIds()).load().block()!!

    assertThat(template.hasKey("chat:rootkeys:long").block()).isTrue()
    assertThat(template.hasKey("chat:rootkeys:uuid").block()).isTrue()
    assertThat(longRoots.keys).isEqualTo(uuidRoots.keys)
}
```

- [ ] **Step 6: Replace the startup wiring.** In
`RootKeyInitializationListeners`, remove both `app.rootkeys.create` beans. Add
one bean that runs `RootKeyLoader.load()` and then `rootKeys.loadDomains(…)`,
on `ApplicationStartedEvent`, before `RootKeyInitializationReadyEvent`. It
blocks, and a failure stops the start. The kv publish bean and the HTTP and kv
consume beans stay. In `shell-scripts/chat-build`, remove the
`-Dapp.rootkeys.create=true` flag. Run `shell-scripts/test-flags.sh` and
update the golden cases that named it.

- [ ] **Step 7: Add `RootKeysFixture` for tests.** It replaces
`GenerateRootKeyInitializer` in every test that used it.

```kotlin
object RootKeysFixture {
    fun <T> of(ids: IKeyGenerator<T>): RootKeys<T> = RootKeys<T>().apply {
        loadDomains(ChatDomain.entries.associateWith { Key.root(ids.nextId()) })
        loadIdentities(Key.of(ids.nextId(), of(ChatDomain.USER).id), Key.of(ids.nextId(), of(ChatDomain.USER).id))
    }
}
```

- [ ] **Step 8: Run the gates.**

Run: `mvn -o -q -B -pl chat-core,chat-persistence-memory,chat-persistence-redis,chat-persistence-cassandra,chat-deploy test`
Then: `shell-scripts/test-flags.sh`
Expected: PASS for both.

- [ ] **Step 9: Commit.**

```bash
git commit -am "The key service owns stable root keys (CHAT-avduuqwp, CHAT-bafkgkko)"
```

---

## Task 4: Mint takes a domain, and records the root

**Files:**
- Modify: `chat-core/src/main/kotlin/com/demo/chat/service/core/KeyService.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/UnsupportedDomainException.kt`
- Modify: `KeyServiceInMemory.kt`, `KeyServiceRedis.kt`, `KeyServiceCassandra.kt`, `persistence/cassandra/domain/Key.kt`
- Modify: every `key(Class)` caller in the mint table of section C
- Modify: `IKeyRestMapping.kt`, `IKeyServiceMapping.kt`, `KeyClient.kt`, `IKeyServiceAccess.kt`, `DummyKeyService.kt`
- Modify: `SecretsRestMapping.kt`, `chat-shell` `UserCommands.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/key/TestKeyServiceBase.kt`

**Interfaces:**
- Consumes: `ChatDomain`, `RootKeys.of`, `Key.of`.
- Produces:

```kotlin
interface IKeyService<T> {
    /** Mints a key in [domain]. Its root is the root of [domain]. */
    fun key(domain: ChatDomain): Mono<out Key<T>>

    /** Removes a key. Refuses a root key with [RootKeyDeletionException]. */
    fun rem(key: Key<T>): Mono<Void>

    fun exists(key: Key<T>): Mono<Boolean>

    /** The stored root of [id], or empty when the registry does not hold it. A root key answers itself. */
    fun rootOf(id: T): Mono<T>
}
```

`kind(key)` and `key(kind: Class<S>)` are removed.

- [ ] **Step 1: Rewrite the shared key service test base.**

```kotlin
@Test
fun `mint sets the root of the domain`() {
    ChatDomain.entries.forEach { domain ->
        val key = keyService.key(domain).block()!!
        assertThat(key.root).isEqualTo(rootKeys.of(domain).id)
        assertThat(keyService.rootOf(key.id).block()).isEqualTo(key.root)
    }
}

@Test
fun `rem refuses a root key`() {
    StepVerifier.create(keyService.rem(rootKeys.of(ChatDomain.USER)))
        .verifyError(RootKeyDeletionException::class.java)
}

@Test
fun `a removed key has no root`() {
    val key = keyService.key(ChatDomain.USER).block()!!
    keyService.rem(key).block()
    StepVerifier.create(keyService.rootOf(key.id)).verifyComplete()
}

@Test
fun `a root key answers itself`() {
    val root = rootKeys.of(ChatDomain.MESSAGE)
    assertThat(keyService.rootOf(root.id).block()).isEqualTo(root.id)
}
```

- [ ] **Step 2: Run it against memory, and see it fail.**

- [ ] **Step 3: Write the exceptions.**

```kotlin
class UnsupportedDomainException(type: String) :
    ChatException("$type has no domain contract, so no key is minted for it. See CHAT-avduuqwp.")

class RootKeyDeletionException(id: Any?) :
    ChatException("Key $id is a root key. A root key is never removed.")
```

- [ ] **Step 4: Rewrite the three key services.** Each takes `RootKeys<T>`.
Registry values are root ids. Redis uses `chat:keys:<keyType>`. Cassandra
uses a `keys (id, root)` row class `CSKeyRow` that does not implement `Key`.

Memory, in full:

```kotlin
class KeyServiceInMemory<T>(private val keyGen: Supplier<T>, private val rootKeys: RootKeys<T>) : IKeyService<T> {
    private val roots = ConcurrentHashMap<T, T>()

    override fun key(domain: ChatDomain): Mono<out Key<T>> = Mono.fromCallable {
        val root = rootKeys.of(domain).id
        Key.of(keyGen.get(), root).also { roots[it.id] = root }
    }

    override fun rem(key: Key<T>): Mono<Void> =
        if (rootKeys.domainOfRoot(key.id) != null) Mono.error(RootKeyDeletionException(key.id))
        else Mono.fromRunnable { roots.remove(key.id) }

    override fun exists(key: Key<T>): Mono<Boolean> = rootOf(key.id).hasElement()

    override fun rootOf(id: T): Mono<T> =
        if (rootKeys.domainOfRoot(id) != null) Mono.just(id) else Mono.justOrEmpty(roots[id])
}
```

Redis and Cassandra follow the same four methods. `rootOf` reads the hash
field or the `keys` row.

- [ ] **Step 5: Move every mint caller.** Use the mint table in section C.
`InMemoryPersistence` takes a `ChatDomain` constructor argument in place of
`entityClass`.

- [ ] **Step 6: Refuse the two unsupported mints explicitly.**

`SecretsRestMapping.kt:28` and `:17`, `:22`, `:38` answer
`Mono.error(UnsupportedDomainException("KeyCredential"))`. Map that exception
to HTTP 501 in the webflux exception handler. `UserCommands.kt:57` prints the
exception message and returns. Add one test for each that asserts the
exception type and the 501 status.

- [ ] **Step 7: Move the two inbound mint routes.**

```kotlin
data class DomainRequest(val domain: ChatDomain)

fun restKey(@RequestBody req: DomainRequest): Mono<out Key<T>> = key(req.domain)
```

An unknown value fails Jackson enum binding with 400, and no class loads.
`IKeyServiceMapping.key(domain: ChatDomain)` takes the enum.
`IKeyServiceAccess.key` reads `@chatAccess.hasAccessToDomain(#domain.wireName, 'NEW')`.

Add a test that posts `{"domain":"java.lang.Runtime"}` and asserts 400 and
that `Runtime` was not initialized by the call.

- [ ] **Step 8: Run the key service tests on all three backends.**

Run: `mvn -o -q -B -pl chat-core,chat-persistence-memory,chat-persistence-redis,chat-persistence-cassandra,chat-webflux,chat-shell test -Pintegration`
Expected: PASS.

- [ ] **Step 9: Commit.**

```bash
git commit -am "Mint by domain, record roots, and refuse unsupported mints (CHAT-avduuqwp)"
```

---

## Task 5: Verify and resolve

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/core/KeyVerifier.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/key/KeyVerifierTests.kt`

**Interfaces:**
- Consumes: `IKeyService.rootOf`, `RootKeys.domainOfRoot`.
- Produces:

```kotlin
class KeyVerifier<T>(private val keys: IKeyService<T>, private val rootKeys: RootKeys<T>) {
    /** The key, when its root is its stored root and, if [expected] is set, the root of [expected]. Otherwise [KeyVerificationException]. */
    fun verify(key: Key<T>, expected: ChatDomain?): Mono<Key<T>>

    /** A key for an inbound [id], with its stored root. [KeyVerificationException] when the id is unknown or not in [expected]. */
    fun resolve(id: T, expected: ChatDomain?): Mono<Key<T>>
}
```

- [ ] **Step 1: Write the tests.**

```kotlin
@Test fun `a minted key verifies`() { … verify(minted, USER) emits minted }
@Test fun `a forged root is refused`() { … verify(Key.of(minted.id, rootKeys.of(MESSAGE).id), null) errors }
@Test fun `a key of another domain is refused`() { … verify(userKey, MESSAGE_TOPIC) errors }
@Test fun `an unknown id is refused`() { … resolve(424242L, null) errors }
@Test fun `resolve reads the stored root`() { … resolve(minted.id, USER) emits Key.of(minted.id, userRoot) }
@Test fun `a root key resolves to itself`() { … resolve(userRoot.id, null) emits Key.root(userRoot.id) }
```

Write each body in full with `StepVerifier`. Example:

```kotlin
@Test
fun `a forged root is refused`() {
    val minted = keys.key(ChatDomain.USER).block()!!
    StepVerifier.create(verifier.verify(Key.of(minted.id, rootKeys.of(ChatDomain.MESSAGE).id), null))
        .verifyError(KeyVerificationException::class.java)
}
```

- [ ] **Step 2: Run and see them fail.**

- [ ] **Step 3: Write the verifier.**

```kotlin
class KeyVerificationException(message: String) : ChatException(message)

class KeyVerifier<T>(private val keys: IKeyService<T>, private val rootKeys: RootKeys<T>) {

    fun resolve(id: T, expected: ChatDomain?): Mono<Key<T>> = keys.rootOf(id)
        .switchIfEmpty(Mono.error(KeyVerificationException("Key $id is not in the registry.")))
        .flatMap { root -> check(Key.of(id, root), expected) }

    fun verify(key: Key<T>, expected: ChatDomain?): Mono<Key<T>> = resolve(key.id, expected)
        .flatMap { stored ->
            if (stored.root == key.root) Mono.just(key)
            else Mono.error(KeyVerificationException("Key ${key.id} carries root ${key.root}. The stored root is ${stored.root}."))
        }

    private fun check(key: Key<T>, expected: ChatDomain?): Mono<Key<T>> =
        if (expected == null || rootKeys.of(expected).id == key.root) Mono.just(key)
        else Mono.error(KeyVerificationException("Key ${key.id} is not in ${expected.wireName}."))
}
```

- [ ] **Step 4: Run and pass.** `mvn -o -q -B -pl chat-core test -Dtest=KeyVerifierTests`

- [ ] **Step 5: Commit.**

```bash
git commit -am "Verify inbound keys and resolve inbound ids (CHAT-avduuqwp)"
```

---

## Task 6: The wire requires a root

**Files:**
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/serializers/ChatDeserializers.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/serializers/ChatJackson3Deserializers.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/convert/NodeValueRules.kt`
- Modify: `DomainWireShapeTests`, `E2eeWireShapeTests`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/domain/KeyWireTests.kt`
- Test: one payload test per pub/sub provider, for B7

- [ ] **Step 1: Write the wire test, for both Jackson generations.**

```kotlin
@Test
fun `a key writes id and root`() {
    assertThat(mapper.writeValueAsString(Key.of(1L, 9L))).isEqualTo("""{"key":{"id":1,"root":9}}""")
}

@Test
fun `a payload without root fails to decode`() {
    assertThatThrownBy { mapper.readValue("""{"key":{"id":1}}""", Key::class.java) }
        .hasMessageContaining("root")
}

@Test
fun `a message key round trips with its root`() {
    val key = MessageKey.of(1L, 9L, 2L, 3L)
    assertThat(mapper.readValue(mapper.writeValueAsString(key), Key::class.java)).isEqualTo(key)
}
```

- [ ] **Step 2: Run and see them fail.**

- [ ] **Step 3: Read `root` in every deserializer.** In each of B1 to B4:

```kotlin
val rootNode = node.get("root") ?: throw JsonMappingException.from(jp, "A key needs a root. The payload holds none.")
```

`KeyAssembly.key(id, root, from, dest)` takes the root. The serializer writes
`root` because `SimpleKey` exposes it.

- [ ] **Step 4: Update the two wire shape tests.** Every expected key gains
`"root"`.

- [ ] **Step 5: Prove B7.** For each pub/sub provider, publish a message and
read it back through the provider codec. Assert the root survives.

- [ ] **Step 6: Run.** `mvn -o -q -B -pl chat-core test`, then the full
default gate. Expected: PASS.

- [ ] **Step 7: Commit.**

```bash
git commit -am "Require the root on the wire (CHAT-avduuqwp)"
```

---

## Task 7: Verify every inbound path, before authorization

**Files:**
- Modify: every site marked **R** in section C, and D1 to D5
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/access/SpringSecurityAccessBrokerService.kt`
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/access/AuthMetadataAccessBroker.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/service/security/AccessBroker.kt`
- Create: `chat-security/src/test/kotlin/com/demo/chat/test/InboundVerificationGuardTests.kt`
- Test: `chat-security/src/test/kotlin/com/demo/chat/test/AnonymousAuthorizationMatrixTests.kt`

- [ ] **Step 1: Write the guard test.** It reads main source and fails on a
`@MessageMapping` method or a `@PathVariable` parameter that has no entry in
`INBOUND_ROUTES`, a list in the test itself. Each entry names the route and its
verification: `verify(Key)` or `resolve(id, domain)`.

```kotlin
@Test
fun `every inbound route is listed with its verification`() {
    val routes = sourceFiles("chat-service-controller", "chat-webflux")
        .flatMap { routesIn(it) }
        .toSet()

    assertThat(routes - INBOUND_ROUTES.keys).isEmpty()
    assertThat(INBOUND_ROUTES.keys - routes).isEmpty()
}
```

`routesIn` reads each file with a regex for `@MessageMapping("…")` and
`@PathVariable`. The list holds 31 plus 21 entries at the start.

- [ ] **Step 2: Write the forged root test for self authority.**

```kotlin
@Test
fun `a forged root never reaches the self rule`() {
    val service = SpringSecurityAccessBrokerService(broker(listOf()), rootKeys(), verifier())
    val forged = Key.of(CALLER_KEY.id, TOPIC_ROOT.id)

    val answer = service.hasAccessTo(forged, "DEL")
        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(authenticatedContext())))
        .block()

    assertThat(answer).isFalse()
}
```

`CALLER_KEY` has root `USER_ROOT`. The forged key has the same id with another
root. Without verification the broker sees `principal.id == target.id` and the
old self rule would allow. With `KeyEquality`, `principal != forged` already,
**but that is equality, not verification.** The test must also fail when
`KeyEquality` compares `id` alone. Step 5 proves that by mutation.

- [ ] **Step 3: Verify in `SpringSecurityAccessBrokerService`.**

```kotlin
fun hasAccessTo(target: Key<T>, perm: String): Mono<Boolean> =
    verifier.verify(target, null)
        .flatMap { access.hasAccessByPrincipal(getSecurityContextPrincipal(), it, perm) }
        .onErrorReturn(false)
        .switchIfEmpty(Mono.just(false))
```

`hasAccessToEntity` does not verify. Its entity came from a typed store, and
Task 8 makes a store stamp the root. `AccessBroker.hasAccessByKeyId(T, T)`
resolves both ids through `KeyVerifier.resolve(id, null)`.

- [ ] **Step 4: State the precondition in the broker.**

```kotlin
/**
 * **Both keys must be verified.** A caller of this broker verifies a key that
 * crossed a trust boundary, with `KeyVerifier`. Equality does not verify a
 * root. See `CHAT-avduuqwp`.
 */
private fun isSelf(principal: Key<T>, target: Key<T>): Boolean = principal == target
```

- [ ] **Step 5: Mutation proof.** Change `KeyEquality.equals` to compare `id`
alone. Run `AnonymousAuthorizationMatrixTests`. Expected: `a forged root never
reaches the self rule` still passes, because verification refuses the key.
Restore it. Remove the `verifier.verify` call instead. Expected: the test still
passes through equality. **Both defenses are required, so run both mutations
together.** Expected: the test fails. Restore both, by absolute path, and prove
the restore with `git status`.

- [ ] **Step 6: Move every **R** site.** Use section C. A composite service
calls `verifier.resolve(req.id, <domain>)`. A REST mapping calls
`resolve(id, domainOf(this))`. An RSocket `Key` payload goes through
`verify(key, <domain>)` in the controller before it reaches the service.

- [ ] **Step 7: Run.** `mvn -o -q -B -pl chat-core,chat-security,chat-service-composite,chat-service-controller,chat-webflux test`
Expected: PASS.

- [ ] **Step 8: Commit.**

```bash
git commit -am "Verify every inbound key and id before authorization (CHAT-avduuqwp)"
```

---

## Task 8: Stores stamp and check roots

**Files:**
- Modify: every Cassandra persistence store and repository in `chat-persistence-cassandra`
- Modify: every Cassandra index in `chat-index-cassandra`
- Modify: every Lucene index in `chat-index-lucene`
- Modify: every Redis store in `chat-persistence-redis`
- Modify: `chat-persistence-memory` stores
- Modify: A8 to A20
- Test: one test per store family in its module

- [ ] **Step 1: Write the store test in the shared base.** Each persistence test
base in `chat-core` test source gains two cases.

```kotlin
@Test
fun `a read key carries the root of the store domain`() {
    val ent = entity(keyService.key(domain).block()!!)
    store.add(ent).block()
    assertThat(store.get(ent.key).block()!!.key.root).isEqualTo(rootKeys.of(domain).id)
}

@Test
fun `add refuses a key of another domain`() {
    val foreign = keyService.key(otherDomain(domain)).block()!!
    StepVerifier.create(store.add(entity(foreign))).verifyError(KeyVerificationException::class.java)
}
```

- [ ] **Step 2: Run on memory and see both fail.**

- [ ] **Step 3: Cassandra row classes stop implementing `Key` and domain
interfaces.** Each store maps a row to a domain object on read. Example for
users:

```kotlin
override fun get(key: Key<T>): Mono<out User<T>> =
    userRepo.findByKeyId(key.id).map { row -> User.create(Key.of(row.key.id, root()), row.name, row.handle, row.imageUri) }

private fun root(): T = rootKeys.of(ChatDomain.USER).id
```

Remove each transitional `override val root: T get() = id` from Task 2.

- [ ] **Step 4: Add the domain check to each `add`.**

```kotlin
override fun add(ent: User<T>): Mono<Void> =
    if (ent.key.root != root()) Mono.error(KeyVerificationException("Key ${ent.key.id} is not in User."))
    else userRepo.add(ent)
```

- [ ] **Step 5: Lucene and Cassandra indexes.** Each index takes its domain and
builds `Key.of(id, rootKeys.of(domain).id)` for every key it returns. C29 to C33
and C20 to C22.

- [ ] **Step 6: Redis stores.** Stored JSON carries `root` since Task 6. Add the
`add` check.

- [ ] **Step 7: List every `add` path.** Run:

```bash
grep -rn --include='*.kt' -E "\.add\(" chat-service-composite/src/main chat-webflux/src/main chat-service-controller/src/main chat-deploy/src/main chat-shell/src/main | grep -v /target/
```

For each line, record in this plan, under this step, where its key comes from:
mint, store read, or verification. **A line with none of the three is a defect.
Stop and report it before you continue.**

- [ ] **Step 8: Run.** `shell-scripts/build-health.sh --integration`
Expected: exit 0.

- [ ] **Step 9: Commit.**

```bash
git commit -am "Stores stamp and check the root of their domain (CHAT-avduuqwp)"
```

---

## Task 9: Grants store verified roots

**Files:**
- Modify: `chat-persistence-cassandra/.../domain/AuthMetadata.kt`, `chat-index-cassandra/.../domain/AuthMetadata.kt`
- Modify: `keyspace-long.cql`, `keyspace-uuid.cql`: `auth_metadata`, `auth_metadata_principal`, `auth_metadata_target`
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/service/CoreAuthorizationService.kt`
- Modify: `chat-deploy/src/main/kotlin/com/demo/chat/service/init/InitialUsersService.kt`
- Test: `chat-security/src/test/kotlin/com/demo/chat/test/CoreAuthorizationServiceTests.kt`

- [ ] **Step 1: Write the tests.**

```kotlin
@Test
fun `authorize refuses a grant with a forged target root`() {
    val grant = AuthMetadata.create(key, principal, Key.of(target.id, otherRoot), "GET", 0L)
    StepVerifier.create(service.authorize(grant, true)).verifyError(KeyVerificationException::class.java)
}

@Test
fun `a stored grant reads back both roots`() {
    service.authorize(grant, true).block()
    val read = service.getAuthorizationsAgainst(principal, target, "GET").blockFirst()!!
    assertThat(read.principal.root).isEqualTo(principal.root)
    assertThat(read.target.root).isEqualTo(target.root)
}
```

- [ ] **Step 2: Run and see them fail.**

- [ ] **Step 3: Add the columns.** Each of the three tables gains
`principal_root` and `target_root`, of the key type. The row classes map them
to `Key.of(principalId, principalRoot)` and `Key.of(targetId, targetRoot)`.

- [ ] **Step 4: Verify in `authorize`.** `CoreAuthorizationService` takes a
`KeyVerifier`. `authorize` verifies the principal and the target before it
writes. `InitialUsersService` builds its placeholder with
`Key.empty(placeholder, rootKeys.of(AUTH_METADATA).id)`.

- [ ] **Step 5: Run.** `mvn -o -q -B -pl chat-core,chat-security,chat-persistence-cassandra,chat-index-cassandra,chat-deploy test -Pintegration`

- [ ] **Step 6: Commit.**

```bash
git commit -am "Grants store and verify both roots (CHAT-avduuqwp)"
```

---

## Task 10: Remove the transitional factories, and `kind`

**Files:**
- Modify: every remaining **M**, **S** and **P** site in section C
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/KeyValuePair.kt`, `Message.kt`
- Modify: the four test implementations in A21, and every test that calls `funKey`
- Create: `chat-core/src/test/kotlin/com/demo/chat/test/domain/KeyContractGuardTests.kt`

- [ ] **Step 1: Write the guard test.**

```kotlin
@Test
fun `no source builds a key without a root, and nothing names kind`() {
    val offenders = sourceFiles().filter { f ->
        val text = f.readText()
        listOf("Key.funKey(", "MessageKey.create(", "Key.emptyKey(", "fun kind(", "val kind:", "KindRequest")
            .any { text.contains(it) }
    }
    assertThat(offenders).isEmpty()
}
```

`sourceFiles()` walks `chat-*/src/main` and `chat-*/src/test` from the repository
root, and skips this file. `MessageDocumentMapper` and the recall filters name
`"kind"` as vector metadata. The test matches the patterns above, which that
code does not use.

- [ ] **Step 2: Run and see it fail.** It names every file left.

- [ ] **Step 3: Decide C12 and C13.** `InMemoryCryptoServices` builds epoch and
tag keys with random UUIDs, not through mint. Mint them with `KEY_VALUE_PAIR`
if they are persisted as key-value entries. Otherwise stop the task and ask the
owner for a domain contract, and refuse them with `UnsupportedDomainException`
until then.

- [ ] **Step 4: Move each remaining site per section C.** Delete `funKey`,
`emptyKey` and both `MessageKey.create` overloads.

- [ ] **Step 5: Run the guard, then the full default gate.**

Run: `mvn -o -q -B -pl chat-core test -Dtest=KeyContractGuardTests`, then
`shell-scripts/build-health.sh`. Expected: PASS and exit 0.

- [ ] **Step 6: Commit.**

```bash
git commit -am "Remove every key factory without a root, and kind (CHAT-avduuqwp)"
```

---

## Task 11: Fresh schemas, and the start check

**Files:**
- Modify: `keyspace-long.cql`, `keyspace-uuid.cql`, `truncate-long.cql`, `truncate-uuid.cql`
- Create: `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraStoreShapeCheck.kt`
- Create: `chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/RedisStoreShapeCheck.kt`
- Test: one container test per backend

- [ ] **Step 1: Write the Cassandra shape test.** It creates a keyspace from the
old script, starts the check, and expects failure. It repeats this once per
required element, removing only that element from the new script.

```kotlin
@ParameterizedTest
@ValueSource(strings = [
    "keys.root", "root_keys", "auth_metadata.principal_root", "auth_metadata.target_root",
    "auth_metadata_principal.principal_root", "auth_metadata_principal.target_root",
    "auth_metadata_target.principal_root", "auth_metadata_target.target_root"
])
fun `a store without a required element fails at start`(element: String) {
    val keyspace = keyspaceWithout(element)
    assertThatThrownBy { CassandraStoreShapeCheck(session, keyspace).check() }
        .hasMessageContaining(element)
        .hasMessageContaining("Recreate the store")
}
```

- [ ] **Step 2: Write the check.**

```kotlin
class CassandraStoreShapeCheck(private val session: CqlSession, private val keyspace: String) {

    private val required: Map<String, Set<String>> = mapOf(
        "keys" to setOf("id", "root"),
        "root_keys" to setOf("domain", "id"),
        "auth_metadata" to setOf("principal_root", "target_root"),
        "auth_metadata_principal" to setOf("principal_root", "target_root"),
        "auth_metadata_target" to setOf("principal_root", "target_root")
    )

    fun check() {
        val columns = session.execute(
            "SELECT table_name, column_name FROM system_schema.columns WHERE keyspace_name = ?", keyspace
        ).map { it.getString("table_name")!! to it.getString("column_name")!! }.groupBy({ it.first }, { it.second })

        val missing = required.flatMap { (table, cols) ->
            val have = columns[table]
            if (have == null) listOf(table) else (cols - have.toSet()).map { "$table.$it" }
        }
        if (missing.isNotEmpty()) throw ChatException(
            "Keyspace $keyspace does not match the required schema. Missing: ${missing.joinToString()}. " +
            "Recreate the store from keyspace-*.cql. This release has no migration."
        )
    }
}
```

It runs before `RootKeyLoader`.

- [ ] **Step 3: Write the Redis check.** It fails when the hash `chat:keys`
exists without a key type segment, and names the recreation.

- [ ] **Step 4: Edit the CQL.** Apply spec part 8: `keys (id, root)`,
`root_keys`, the six authorization root columns, and remove `event_key_meta`
and `chat_secret.kind`. Confirm that neither truncate script names `root_keys`.

- [ ] **Step 5: Run.** `shell-scripts/build-health.sh --integration`
Expected: exit 0.

- [ ] **Step 6: Commit.**

```bash
git commit -am "Fresh schemas, and a start check for the old shape (CHAT-avduuqwp)"
```

---

## Task 12: Documents and the final gate

**Files:**
- Modify: `docs/ANONYMOUS-AUTHORIZATION.md`, `docs/IDENTITY-POLICY.md`, `docs/NODEID-CLAIM.md` where it names root keys, `docs/BUILD.md` where it names `app.rootkeys.create`, `forward-register.md`

- [ ] **Step 1: Search for stale prose.**

```bash
grep -rn -e "rootkeys.create" -e "funKey" -e "kind" -e "KnownRootKeys" -e "GenerateRootKeyInitializer" docs forward-register.md
```

Correct each hit that describes current behavior. Keep dated history as it is,
and add a correction line where the register style does.

- [ ] **Step 2: Run the drift discipline.** `drift check`. Update prose before
any `drift link`.

- [ ] **Step 3: Run the final gate.**

Run: `shell-scripts/build-health.sh --ci`
Expected: exit 0, and reality matches `docs/BUILD-HEALTH.md`. Update that
document when the test counts move.

- [ ] **Step 4: Run `just check-production-classpath` and the launch gate.**
Both exit 0.

- [ ] **Step 5: Commit, push, open the pull request.**

---

## Self-review

- **Spec coverage.** Part 1: Task 1. Part 2: Task 2, Task 10. Part 3: Task 3,
  Task 4. Part 4: Task 4. Part 5: Task 6. Part 6: Task 5, Task 7. Part 7:
  Task 8, Task 9. Part 8: Task 11. Part 9: Task 4, Task 10, Task 11. Owner
  requirements 1 to 5: Task 2, Task 7, the inventory, Task 4, Task 11.
- **Known open points, stated in their tasks.** C12 and C13 need a domain
  decision in Task 10 step 3. The `add` path list is produced in Task 8 step 7.
  B7 is proved in Task 6 step 5.
- **Type consistency.** `ChatDomain`, `RootKeys.of`, `Key.of`, `Key.root`,
  `Key.empty`, `MessageKey.of`, `KeyVerifier.verify` and `resolve`,
  `IKeyService.rootOf`, `RootKeyStore.createIfAbsent`, and the three exception
  types keep one name in every task.

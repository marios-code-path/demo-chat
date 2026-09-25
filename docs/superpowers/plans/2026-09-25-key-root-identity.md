# Root Identity on Keys Implementation Plan

> **Execution:** inline, with superpowers:executing-plans. **Do not use
> subagent-driven development.** `AGENTS.md` forbids it.
>
> **Tracking:** each task is an FP issue under `CHAT-avduuqwp`. Claim it with
> `fp issue update --status in-progress <id>`, log each step with `fp comment`,
> and close it with `--status done`. This plan uses numbered steps, not
> checkboxes.
>
> **Navigation:** find a definition, a caller or a usage with the semantic
> tools: `mcp__idea__analyze_calls`, `mcp__idea__search_symbol`,
> `mcp__treesitter-mcp__find_usages`, `mcp__treesitter-mcp__affected_by_diff`.
> Use `grep` only for raw text, such as a CQL table name or a log line.

**Goal:** Every `Key<T>` carries a required, verified root, and the key service
owns stable root keys per domain and key type.

**Architecture:** A closed `ChatDomain` type replaces class names. A root key
store per backend creates one root per domain with a conditional write. `Key`
gains `root`, and three canonical classes carry one equality rule. Mint takes a
`ChatDomain`. A `VerifiedKey` that only `KeyVerifier` can build carries every
inbound key to the broker and to persistence.

**Tech Stack:** Kotlin 2.4, Spring Boot 4.0.8, Reactor 3.8, Jackson 2 and 3,
Spring Data Cassandra, Spring Data Redis (Lettuce), JUnit 6, Testcontainers 2.

**Spec:** `docs/superpowers/specs/2026-09-24-key-root-identity-design.md`,
approved on 2026-09-25. Issue `CHAT-avduuqwp`, child `CHAT-bafkgkko`.

**Revision:** fourth. The second revision fixes the five problems of the
review of `c2ffe934`. The third records D1 and D2. The fourth records D3 and
three corrections from the review of `345ff40b`. See the revision record at the
end.

## Global Constraints

- `Key<T>` is `{ id: T, root: T }`. **No factory fabricates a root**, at any
  point of execution. There is no transitional `root = id` factory.
- No migration, no backfill, no reader for a payload without `root`, no class
  name fallback. Old development stores are recreated.
- `app.rootkeys.create` is removed.
- Equality compares `id`, `root` and `empty`, in every `Key` implementation.
  `hashCode` matches. **Equality does not verify a root.**
- An inbound key or id becomes a `VerifiedKey` before it reaches the broker,
  the self authority rule, or persistence.
- Only a **mint** of a type with no domain contract is refused, with
  `UnsupportedDomainException`. An operation on an existing key is not refused.
  No compile failure and no accidental exception.
- **Each schema change lands in the task that first consumes it.**
- Redis names carry the key type: `chat:keys:<keyType>`,
  `chat:rootkeys:<keyType>`.
- `root_keys` is absent from `truncate-long.cql` and `truncate-uuid.cql`.
- Run `mvn -o -pl chat-core,<module> test`, never `-pl <module>` alone.
- One maven build at a time per worktree.
- Prose follows `AGENTS.md` controlled English.

## The coordinated change

Tasks T3a to T3e change the `Key` contract across the reactor. **They are one
coordinated change with explicit compile checkpoints.** A checkpoint names the
modules that compile at that point. The reactor is not green between T3a and
T3e. Each sub-task commits when its checkpoint passes. Push the branch, but
never merge it before T8.

This replaces the transitional `root = id` factory of the first revision. That
factory kept every commit green, but it fabricated roots, so no authorization
result between two tasks meant anything.

---

## Pre-execution decisions

**D1, D2 and D3 are closed. The owner decided all three on 2026-09-25.**
Execution waits for the owner review of the fourth revision.

### D1. The end-to-end encryption keys: **closed, two new domains**

`CryptoServiceBeans` wires `InMemoryConversationEpochService` and
`InMemoryFrankingService` as beans, and `chat-deploy-e2ee` loads them. Each
builds a key from a random UUID string cast to `T`. That cast is unsound when
`T` is `Long`.

**The owner decided on 2026-09-25:** add `CONVERSATION_EPOCH` and `FRANKING_TAG`
to `ChatDomain`. Each has its own domain root. Both services mint through the
key service. **Neither uses `KEY_VALUE_PAIR`**, because a map in the storage is
not a domain contract.

**The send path mints too.** `InMemoryEncryptedMessageService` builds its own
`InMemoryFrankingService` and calls `generateTagSync` inside
`Mono.fromCallable`. Updating the Spring franking bean alone does not reach that
path. So `InMemoryEncryptedMessageService` takes the `FrankingService` bean by
injection, `generateTagSync` is removed, and `send` composes the reactive
`generateTag`, which mints through the key service.

Required tests, in T3a and T3d:

- An epoch key resolves in `CONVERSATION_EPOCH`, and a franking key resolves in
  `FRANKING_TAG`.
- `send()` answers a tag whose key the registry holds under `FRANKING_TAG`.
- Verification refuses an epoch id that carries the `FRANKING_TAG` root, and a
  franking id that carries the `CONVERSATION_EPOCH` root.

### D2. The vector index job key: **closed, two keys**

`VectorIndexJobStoreImpl.start` minted one key from `topicPersistence`, in
`MESSAGE_TOPIC`, and stored the job in the key-value store under the same key.

**The owner decided on 2026-09-25:**

- The job key is minted in `KEY_VALUE_PAIR`. The key-value store stays
  restricted to `KEY_VALUE_PAIR`.
- The job topic key is minted separately, in `MESSAGE_TOPIC`.
- `IndexJob` stores the topic reference explicitly, in a new field
  `topicKey: Key<T>`.

Measured consequences, each assigned to T3d. Found on 2026-09-25 with
`mcp__treesitter-mcp__find_usages`:

| Site | Today | After |
|---|---|---|
| `VectorIndexJobStoreImpl.start` | one key for topic and job | mints both. `pubsub.open(topicKey.id)` |
| `MessageReindexServiceImpl.emit` | `JobRecord(jobKey = job.key)` | adds `topicKey = job.topicKey` to `JobRecord` |
| `ComposedJobRecordWriter.write` | message `dest` is `record.jobKey.id` | `dest` is `record.topicKey.id` |
| `MessageReindexServiceImpl` line 153 | adds `job.key.id` to the excluded topic ids | adds `job.topicKey.id` |
| `VectorCoveragePolicyImpl.selectCoveringJob` | `readJob(topic.key)` | `readJobByTopic(topic.key)`, and any lookup error fails the selection. D3 |
| `VectorIndexStartupAction.releaseStaleJobs` | `readJob(topic.key)` | `readJobByTopic(topic.key)`, and a lookup error is logged per topic. D3 |

Required tests, in T3d and T5:

- The job key and the topic key have distinct ids. The job key root is the
  `KEY_VALUE_PAIR` root, and the topic key root is the `MESSAGE_TOPIC` root.
- A stored job read back holds its topic reference.
- The key-value store refuses a topic key.

### D3. How a reader finds a job from its topic: **closed, the key-value index**

Two readers list the job topics by name, then call `readJob(topic.key)`. After
D2 the topic key does not find the job, because the job is stored under its own
key.

**The owner decided on 2026-09-25:** register `IndexJob` in the key-value index,
and add `readJobByTopic(topicKey)`.

**The lookup contract.**

1. `start` writes the job and indexes its topic reference in the same step. The
   index field is `topicId`. A `KeyValueIndexFieldsEntry` bean registers
   `IndexJob` with that field.
2. `readJobByTopic(topicKey)` queries the index by `topicId`. **Exactly one
   match is required.**
3. It reads the job under the matched key. The existing check stays: the stored
   `job.key` equals the key it is stored under.
4. It also requires `job.topicKey == topicKey`. Key equality compares the root,
   so a topic reference with another root does not match.
5. **Each failure is an error, never an empty answer.** `JobLookupException`
   names the kind:

| Case | Meaning |
|---|---|
| missing | no index entry names the topic |
| duplicate | two or more index entries name the topic |
| dangling | the index names a job key that the store does not hold |
| stored key mismatch | the stored `job.key` differs from its storage key |
| topic mismatch | `job.topicKey` differs from the asked topic, by id or by root |

**The two readers.**

- **`VectorCoveragePolicyImpl.selectCoveringJob` fails on any lookup error.** It
  must not skip the failing topic and select an older job. The error reaches the
  caller of the policy.
- **`VectorIndexStartupAction.releaseStaleJobs` keeps its best-effort behavior.**
  It logs the lookup error for that topic and continues with the next one, as
  `releaseOne` does today.

**Required tests,** in T3d: each of the five cases through each reader. The
coverage policy answers the error. The startup sweep logs it, and still
releases every other stale job.

### Decided by the plan, from the owner's rules

| Item | Decision | Rule |
|---|---|---|
| Join and leave alerts | Mint a separate `MESSAGE` key for each alert. Do not reuse the membership id. | an id has one registry root |
| Credentials | A credential belongs to a `USER` key. `get`, `add/{id}` and `compare` resolve the id in `USER`. Only `restAddCredential`, which mints a credential key, is refused. | refuse unsupported mints only |
| Shell `key()` command | Refused with `UnsupportedDomainException`. It mints a generic `Key`. | refuse unsupported mints |
| OAuth registered clients | The first `save` mints a `KEY_VALUE_PAIR` key. The configured client id stays an indexed field, and `findById` already reads it. A later `save` finds the stored key through the index. | an id is minted, not configured |
| Root snapshot in Consul and HTTP | A string keyed snapshot, not a domain key. See T2. | a bootstrap store is not a domain store |

---

## Inventory

Measured on 2026-09-25 at master `3527e7cb`. **Every entry has a root source
before any factory changes.**

| Code | Source | Trusted |
|---|---|---|
| **M** | mint, from the key service for a `ChatDomain` | yes |
| **S** | a typed store or index read, from the domain of that store | yes |
| **P** | a minted key carried forward, for example a `MessageKey` built from a minted message id | yes |
| **R** | a raw id or a key from a caller, resolved or verified to a `VerifiedKey` with an expected domain | after the lookup |
| **B** | the root snapshot contract of T2, which is not a domain key | see T2 |
| **X** | a mint with no domain contract, refused | refused |
| **D** | deleted | none |

### A. `Key` implementations

| # | Class | Module | After | Task |
|---|---|---|---|---|
| A1 | `Key.funKey` anonymous object | chat-core | deleted. `SimpleKey` replaces it | T3a |
| A2 | `Key.emptyKey` anonymous object | chat-core | deleted. `EmptyKey` replaces it | T3a |
| A3 | `MessageKey.create(id, from, dest)` anonymous | chat-core | deleted. `SimpleMessageKey` | T3a |
| A4 | `MessageKey.create(id, dest)` anonymous | chat-core | deleted. `SimpleMessageKey` | T3a |
| A5 | `ChatMessageKey` data class | chat-core | stops implementing `MessageKey`. `toKey(root)` converts it | T3a |
| A6 | `Admin` data class | chat-core | stops implementing `Key`. `RootKeys.admin()` | T1 |
| A7 | `Anon` data class | chat-core | stops implementing `Key`. `RootKeys.anon()` | T1 |
| A8 to A14 | `ChatUserKey`, `ChatTopicKey`, `ChatMessageByIdKey`, `AuthMetadataIdKey`, `KVKey`, `CSKey`, `CredKey` | chat-persistence-cassandra | stop implementing `Key`. The store maps rows | T3b |
| A15 to A20 | `ChatUserHandleKey`, `ChatTopicNameKey`, `ChatMessageByUserKey`, `ChatMessageByTopicKey`, `ChatKeyValueIndexKey`, `ChatKeyValueIndexByIdKey` | chat-index-cassandra | stop implementing `Key`. The index maps rows | T3c |
| A21 | four test implementations | test source | move to `Key.of` | the sub-task of their module |

**Today equality is asymmetric.** `funKey(7) == ChatUserKey(7)` is true, and
`ChatUserKey(7) == funKey(7)` is false, because a data class requires its own
class. After T3c only the three canonical classes implement `Key`, and one rule
serves all of them.

### B. Deserializers and generated constructors

| # | Path | After | Task |
|---|---|---|---|
| B1 | `KeyDeserializer`, Jackson 2 | reads `id` and `root`, fails without `root` | T3a |
| B2 | `MessageKeyDeserializer`, Jackson 2 | reads `root` | T3a |
| B3 | `KeyAssembly.key`, Jackson 3 | takes `root` | T3a |
| B4 | `ChatJackson3Deserializers.kt:78` | reads `root` | T3a |
| B5 | Spring Data Cassandra builds A8 to A20 | not a `Key`. The store maps rows | T3b, T3c |
| B6 | Redis JSON in each Redis store | goes through B1 and B2 | T3b |
| B7 | Kafka and Redis pub/sub payloads | through B1 to B4. **Unmeasured.** T3e proves it per provider | T3e |

A decoded key is a claim. It becomes a `VerifiedKey` only through T4.

### C. Factory calls in main source

Seventy-seven calls. Corrections from the review are marked **fixed**.

| # | Site | Src | After | Task |
|---|---|---|---|---|
| C1 | `KeyValueStoreRegisteredClientRepository.kt:26` | M | first `save` mints `KEY_VALUE_PAIR`. Later saves read the stored key through the index | T3d |
| C2 | `ConsulKVStore.kt:22` | B | the Consul store takes a string name. It holds no domain key | T2 |
| C3 | `KeyService.kt:15` `IKeyGenerator.nextKey()` | D | a generator makes ids, and mint makes keys | T3a |
| C4 | `AccessBroker.kt:13` `hasAccessByKeyId(T, T)` | R | resolves both ids to `VerifiedKey` | T4 |
| C5 to C10 | deserializers and `KeyAssembly` | R | B1 to B4 | T3a |
| C11 | `GenerateRootKeyInitializer.kt:15` | D | removed. Tests use `RootKeysFixture` | T2 |
| C12 | `InMemoryCryptoServices.kt:149` epoch key | M | **D1.** Minted in `CONVERSATION_EPOCH` | T3d |
| C13 | `InMemoryCryptoServices.kt:192` franking tag key | M | **D1.** Minted in `FRANKING_TAG` | T3d |
| C14 | `HttpRootKeyConsumeOnStart.kt:64` | B | reads the snapshot | T2 |
| C15 | `RootKeyConsumerHttp.kt:62` | D | a commented out duplicate | T2 |
| C16 | `InitialUsersService.kt:24` `emptyKey` | P | **fixed.** Today one placeholder serves two roles: the grant key before `authorize` mints one (line 68), and the fallback when user creation answers empty (line 46). **The roles split.** The grant placeholder is `Key.empty(placeholder, rootKeys.of(AUTH_METADATA).id)`, used only at line 68. User creation that answers empty fails the initialization with a message that names the user. No fallback key exists | T3d |
| C17 to C19 | `RootKeyService.kt:25`, `:38`, `:47` | B | **fixed.** A string name in the snapshot store, not a `KEY_VALUE_PAIR` key | T2 |
| C20 | `KeyValueIndex.kt:84` | S | root of `KEY_VALUE_PAIR` | T3c |
| C21 | `MembershipIndex.kt:59` | D | commented out | T3c |
| C22 | `MembershipIndex.kt:71` | S | root of `TOPIC_MEMBERSHIP` | T3c |
| C23 to C28 | `index-cassandra` `AuthMetadata.kt` | S | principal and target from `principal_root` and `target_root`. The grant key from the root of `AUTH_METADATA` | T3c |
| C29 to C32 | Lucene key decoders | S | one decoder per index, with its domain root | T3c |
| C33 | `IndexEntryEncoder.kt:63` | D | uses the raw id | T3c |
| C34, C35 | `persistence-cassandra` `AuthMetadata.kt` | S | from the root columns | T3b |
| C36 | `MemoryPersistenceServices.kt:36` | S | root of `TOPIC_MEMBERSHIP` | T3b |
| C37 | `KeyServiceInMemory.kt:21` | M | mint | T3b |
| C38 | `KeyServiceRedis.kt:28` | M | mint | T3b |
| C39 | `MessagingServiceImpl.kt:38` `messageById` | R | `resolve(req.id, MESSAGE)` | T3d |
| C40 | `MessagingServiceImpl.kt:42` | P | the minted message key | T3d |
| C41 | `UserServiceImpl.kt:33` | P | use the minted key. Do not rebuild it | T3d |
| C42 | `UserServiceImpl.kt:54` | R | `resolve(req.id, USER)` | T3d |
| C43 | `UserServiceImpl.kt:60` | S | ids from the user index. Root of `USER` | T3d |
| C44 | `ComposedJobRecordWriter.kt:35` | P | `record.key` is minted from the message store in `MessageReindexServiceImpl.emit`. Carry its root | T3d |
| C45 | `MessageRecallServiceImpl.kt:97` | S | ids from vector metadata that this server wrote. Root of `MESSAGE` | T3d |
| C46 to C48 | `TopicServiceImpl.kt:64`, `:84`, `:102` | R | `resolve(…, MESSAGE_TOPIC)` | T3d |
| C49 | `TopicServiceImpl.kt:113` join alert | M | **fixed.** Mint a new `MESSAGE` key for the alert from `messagePersistence.key()`. The membership id stays the membership key | T3d |
| C50 | `TopicServiceImpl.kt:132` leave alert | M | **fixed.** Same as C49 | T3d |
| C51 | `TopicServiceImpl.kt:148` | S | the id comes from a stored membership. Root of `USER` | T3d |
| C52 | `MessagingServiceAccess.kt:20` `listenTopic` | R | `resolve(req.id, MESSAGE_TOPIC)` | T3d |
| C53 | `MessagingServiceAccess.kt:24` `messageById` | R | **fixed.** `resolve(req.id, MESSAGE)`. The id is a message id | T3d |
| C54 | `MessagingServiceAccess.kt:28` `send` | R | `resolve(req.dest, MESSAGE_TOPIC)` | T3d |
| C55 to C59 | `TopicServiceAccess.kt:30` to `:46` | R | `resolve(…, MESSAGE_TOPIC)` | T3d |
| C60 | `UserServiceAccess.kt:26` | R | `resolve(req.id, USER)` | T3d |
| C61 | `TopicCommands.kt:43` `emptyKey` | P | `Key.empty(placeholder, rootKeys.of(MESSAGE).id)` from the consumed snapshot | T3d |
| C62 | `TopicCommands.kt:44` | P | the identity key the shell holds from login | T3d |
| C63 to C66 | `UserCommands.kt:46`, `:110`, `:140`, `:141` | R | the shell sends an id. The server resolves it | T3d |
| C67 | `PersistenceControllers.kt:50` | P | the minted message key | T3d |
| C68 | `KeyValueStoreRestMapping.kt:31` | R | `resolve(req.key, KEY_VALUE_PAIR)`. An unknown id is refused. A client mints first | T3d |
| C69 to C71 | `IndexRestMapping.kt:20`, `PersistenceRestMapping.kt:24`, `:27` | R | a `@Resolved` path parameter with the domain of the mapping | T3d, T4 |
| C72 | `SecretsRestMapping.kt:17` `get/{id}` | R | **fixed.** `resolve(id, USER)`. Not refused | T3d |
| C73 | `SecretsRestMapping.kt:22` `add/{id}` | R | **fixed.** `resolve(id, USER)`. Not refused | T3d |
| C74 | `SecretsRestMapping.kt:38` `compare/{id}` | R | **fixed.** `resolve(id, USER)`. Not refused | T3d |
| C75 | `PubSubRestMapping.kt:53` | P | the minted message key | T3d |
| C76, C77 | `IKeyRestMapping.kt:27`, `:31` | R | `@Resolved` with no expected domain | T3d, T4 |

Mint calls with a class today:

| Site | Class today | After |
|---|---|---|
| `MembershipPersistenceCassandra.kt:17` | `TopicMembershipByKey` | `TOPIC_MEMBERSHIP` |
| `MembershipPersistenceRedis.kt:34` | `TopicMembership` | `TOPIC_MEMBERSHIP` |
| six `*PersistenceCassandra` and `*PersistenceRedis` stores | their entity class | the matching domain |
| `InMemoryPersistence.kt:20` | `entityClass` | a `ChatDomain` constructor argument |
| `PubSubRestMapping.kt:49` | `MessageKey` | `MESSAGE` |
| `UserCommands.kt:35`, `:136` | `KeyValuePair`, `AuthMetadata` | `KEY_VALUE_PAIR`, `AUTH_METADATA` |
| `UserCommands.kt:57` `key()` | `Key` | **X**. The command prints the refusal |
| `SecretsRestMapping.kt:28` `restAddCredential` | `KeyCredential` | **X**. HTTP 501 |
| `RootKeysSupplier.kt:15` | each `KnownRootKeys` class | **D**. T2 replaces it |

### D. Inbound routes

| # | Surface | After | Task |
|---|---|---|---|
| D1 | RSocket `@MessageMapping` parameters of type `Key<T>` in `chat-service-controller` | the parameter type becomes `VerifiedKey<T>`, built by a messaging argument resolver | T4 |
| D2 | REST `@PathVariable` ids in `chat-webflux` | a `@Resolved(domain)` parameter of type `VerifiedKey<T>`, built by a web argument resolver | T4 |
| D3 | `IKeyRestMapping.restKey`, `IKeyServiceMapping.key` | take a `ChatDomain` | T3d |
| D4 | `SpringSecurityAccessBrokerService.hasAccessTo(target)` | verifies to `VerifiedKey` before the broker | T4 |
| D5 | `AuthMetadataAccessBroker` | takes `target: VerifiedKey<T>`, so an unverified key cannot reach `isSelf` | T4 |
| D6 | `PersistenceStoreMapping.add(ent)`, RSocket | **new.** Verifies the entity key in the store domain before the store | T4 |
| D7 | Request ids in `ByIdRequest`, `MembershipRequest`, `MessageSendRequest`, `MemberTopicRequest` | resolved in the composite service, sites C39 to C60 | T3d, T4 |

### E. Every `add` path, and its key source

**Measured on 2026-09-25 with `mcp__treesitter-mcp__find_usages` for `add`**
in each main source tree. Index writes that follow a store write share its key
and are not listed. T0 step 2 measures again.

| # | Path | Key source | Task |
|---|---|---|---|
| E1 | `MessagingServiceImpl.send`, `messagePersistence.add` | M, `messagePersistence.key()` | T5 |
| E2 | `UserServiceImpl.addUser`, `userPersistence.add` | M, `userPersistence.key()`. C41 stops rebuilding it | T5 |
| E3 | `TopicServiceImpl.addRoom`, `topicPersistence.add` | M | T5 |
| E4 | `TopicServiceImpl.joinRoom`, `membershipPersistence.add` | M | T5 |
| E5 | `VectorIndexJobStoreImpl.start`, `topicPersistence.add` | M, `MESSAGE_TOPIC`. **D2.** A separate key from the job key | T5 |
| E6 | `VectorIndexJobStoreImpl.write`, `keyValueStore.add(job.key)` | M. **D2.** The job key is minted in `KEY_VALUE_PAIR` | T5 |
| E7 | `ComposedJobRecordWriter.write`, `messagePersistence.add` | M, minted from the message store in `MessageReindexServiceImpl.emit` | T5 |
| E8 | `PersistenceControllers` in `chat-webflux`, lines 35, 50, 64, 78 | M. T0 step 2 confirms each key comes from `key()` | T5 |
| E9 | `KeyValueStoreRestMapping` add | R, `KEY_VALUE_PAIR` | T4, T5 |
| E10 | `PersistenceStoreMapping.add(ent)`, RSocket | R, the store domain | T4, T5 |
| E11 | `KeyValueStoreRegisteredClientRepository.save` | M on first save, S after | T5 |
| E12 | `InitialUsersService`, `secretsStore.addCredential` | S or M: the user key from `addUser`, or from `findByUsername` | T5 |
| E13 | `RootKeyService.publishRootKeys`, `kvStore.add` | B | T2 |
| E14 | `CoreAuthorizationService.authorize`, `authPersist.add` | M for the grant key. Principal and target verified in T6 | T5, T6 |
| E15 | `UserCommands.kv`, `keyValuePersistence.add` | M | T5 |

---

## T0: Record the inventory and the pre-execution decisions

FP: `CHAT-ufqdvmkp`.

**Files:** this plan.

1. **Confirm the owner review of the fourth revision.** D1, D2 and D3 are
   closed. Record the review result in a comment on `CHAT-avduuqwp`. Stop until
   it arrives.

2. **Measure again at the branch head.** For each symbol, run
   `mcp__treesitter-mcp__find_usages` over each `chat-*/src/main` tree:
   `funKey`, `emptyKey`, `create` in `Message.kt`, and `add` in each module of
   section E. Expected: the 77 factory calls of section C and the 15 add paths
   of section E. For E8, read each `PersistenceControllers` add and confirm its
   key comes from `key()`. A difference means the tree moved. Update the
   inventory before any other task.

3. **Commit.**

```bash
git add docs/superpowers/plans/2026-09-25-key-root-identity.md
git commit -m "Plan: record the pre-execution decisions (CHAT-avduuqwp)"
```

---

## T1: Type the domains and the identities

FP: `CHAT-lhizttet`.

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/knownkey/ChatDomain.kt`
- Modify: `RootKeys.kt`, `Admin.kt`, `Anon.kt` in `chat-core/.../domain/knownkey`
- Modify: `chat-security/.../access/SpringSecurityAccessBrokerService.kt`, `ContextIdentity.kt`
- Modify: every caller of `RootKeys.getRootKey`, `addRootKey` and `hasKey`. Find them with `mcp__idea__analyze_calls` on each method.
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/domain/ChatDomainTests.kt`

**Interfaces produced:** `enum class ChatDomain(val wireName: String)`,
`ChatDomain.parse(name): ChatDomain?`, `RootKeys.of(domain): Key<T>`,
`RootKeys.admin()`, `RootKeys.anon()`, `RootKeys.domainOfRoot(id: T): ChatDomain?`,
`RootKeys.loadDomains(Map<ChatDomain, Key<T>>)`,
`RootKeys.loadIdentities(admin, anon)`.

**`Key` does not change in T1.** `RootKeys` stores the keys that exist today.

1. **Write the test.**

```kotlin
class ChatDomainTests {

    @Test
    fun `parse reads each wire name and nothing else`() {
        ChatDomain.entries.forEach { assertThat(ChatDomain.parse(it.wireName)).isEqualTo(it) }
        assertThat(ChatDomain.parse("KeyCredential")).isNull()
        assertThat(ChatDomain.parse("Key")).isNull()
        assertThat(ChatDomain.parse("user")).isNull()
    }

    @Test
    fun `a partial root set is refused`() {
        val keys = RootKeys<Long>()
        assertThatThrownBy { keys.loadDomains(mapOf(ChatDomain.USER to Key.funKey(1L))) }
            .hasMessageContaining("MESSAGE")
    }
}
```

   `ChatDomain` holds eight entries, including the two E2EE domains of D1.

2. **Run it and see it fail.**
   `mvn -o -q -B -pl chat-core test -Dtest=ChatDomainTests`. Expected: compile
   failure on `ChatDomain`.

3. **Write `ChatDomain`.**

```kotlin
/**
 * Every domain that owns a root key. See `CHAT-avduuqwp`.
 *
 * **The list is closed.** A type with no entry has no root, and a mint for it
 * is refused. [parse] is the only way text becomes a domain.
 */
enum class ChatDomain(val wireName: String) {
    USER("User"),
    MESSAGE("Message"),
    MESSAGE_TOPIC("MessageTopic"),
    TOPIC_MEMBERSHIP("TopicMembership"),
    AUTH_METADATA("AuthMetadata"),
    KEY_VALUE_PAIR("KeyValuePair"),
    CONVERSATION_EPOCH("ConversationEpoch"),
    FRANKING_TAG("FrankingTag");

    companion object {
        fun parse(name: String): ChatDomain? = entries.firstOrNull { it.wireName == name }
    }
}
```

4. **Rekey `RootKeys`.**

```kotlin
class RootKeys<T> {
    private val domains: MutableMap<ChatDomain, Key<T>> = ConcurrentHashMap()
    @Volatile private var admin: Key<T>? = null
    @Volatile private var anon: Key<T>? = null

    fun of(domain: ChatDomain): Key<T> = domains[domain] ?: throw ChatException(
        if (domains.isEmpty()) "No root key '${domain.wireName}': the root keys are not loaded."
        else "No root key '${domain.wireName}'. Loaded: ${domains.keys.sorted().joinToString { it.wireName }}"
    )

    fun admin(): Key<T> = admin ?: throw ChatException("The Admin identity is not loaded.")
    fun anon(): Key<T> = anon ?: throw ChatException("The Anon identity is not loaded.")

    fun domainOfRoot(id: T): ChatDomain? = domains.entries.firstOrNull { it.value.id == id }?.key

    fun loadDomains(roots: Map<ChatDomain, Key<T>>) {
        val missing = ChatDomain.entries - roots.keys
        require(missing.isEmpty()) { "A root key set must name every domain. Missing: $missing" }
        domains.putAll(roots)
    }

    fun loadIdentities(admin: Key<T>, anon: Key<T>) {
        this.admin = admin
        this.anon = anon
    }

    fun domains(): Map<ChatDomain, Key<T>> = domains.toMap()
}
```

5. **`Admin` and `Anon` stop implementing `Key`.** Each becomes an empty marker
   class. Move each caller to `rootKeys.admin()` or `rootKeys.anon()`.
   `InitialUsersService` calls `loadIdentities` after it finds or adds the two
   users.

6. **Parse domain names in the access service.**

```kotlin
fun hasAccessToDomain(domain: String, perm: String): Mono<Boolean> =
    ChatDomain.parse(domain)
        ?.let { access.hasAccessByPrincipal(getSecurityContextPrincipal(), rootKeys.of(it), perm) }
        ?.onErrorReturn(false)
        ?.switchIfEmpty(Mono.just(false))
        ?: Mono.just(false)
```

   Remove `hasAccessToDomainByKind`.

7. **Checkpoint.** `shell-scripts/build-health.sh`. Expected: exit 0.

8. **Commit.** `git commit -am "Type the domains and the identities (CHAT-avduuqwp)"`

---

## T2: Load stable roots, and define the root snapshot contract

FP: `CHAT-ijojbtpr`. Implements `CHAT-bafkgkko`.

**Two contracts, kept apart.**

- **Store backed initialization.** A process that reaches the authoritative
  store loads or creates the roots through `RootKeyStore`. The ids are typed
  as `T`. This is the only place a root is created.
- **Snapshot consumption.** A process that does not reach the store, such as
  the shell, reads a `RootKeySnapshot` from HTTP or from Consul. It never
  creates a root. The snapshot is a string keyed record, not a domain key.

**Files:**
- Create: `chat-core/.../service/core/RootKeyStore.kt`, `RootKeyLoader.kt`
- Create: `chat-core/.../domain/knownkey/RootKeySnapshot.kt`
- Create: `RootKeyStoreInMemory.kt`, `RootKeyStoreRedis.kt`, `RootKeyStoreCassandra.kt` in their backend modules
- Modify: `keyspace-long.cql`, `keyspace-uuid.cql`: add `root_keys`. **This is its first consumer.**
- Modify: `chat-core/.../service/core/PersistenceStore.kt`: `InitializingKVStore` takes string names
- Modify: `ConsulKVStore.kt`, `RootKeyService.kt`, `HttpRootKeyConsumeOnStart.kt`, `RootKeyInitializationListeners.kt`, the `rootkeys` actuator endpoint
- Delete: `RootKeysSupplier.kt`, `GenerateRootKeyInitializer.kt`, `RootKeyConsumerHttp.kt`, `RootKeyInitRunner.kt`, `KnownRootKeys.kt`
- Modify: `shell-scripts/chat-build` lines 681 to 697, and the golden cases of `shell-scripts/test-flags.sh`
- Create: `chat-core/src/test/.../key/RootKeysFixture.kt`
- Test: `RootKeyLoaderTests`, `RootKeyStoreRedisTests`, `RootKeyStoreCassandraTests`, `RootKeySnapshotTests`

**Interfaces produced:**

```kotlin
interface RootKeyStore<T> {
    fun read(): Mono<Map<ChatDomain, T>>
    /** Writes [id] only when [domain] has no root. Answers the stored root: [id] or the winner. */
    fun createIfAbsent(domain: ChatDomain, id: T): Mono<T>
}

class RootKeyLoader<T>(private val store: RootKeyStore<T>, private val ids: IKeyGenerator<T>) {
    fun load(): Mono<Map<ChatDomain, T>>
}

/** What a process with no store access reads. Ids are strings, parsed with the TypeUtil of the reader. */
data class RootKeySnapshot(val keyType: String, val domains: Map<String, String>, val admin: String, val anon: String)

interface InitializingKVStore {
    fun read(name: String): Mono<String>
    fun write(name: String, value: String): Mono<Void>
}
```

**A root key is its own root.** In T2, `Key` has no `root` yet.
`RootKeys.loadDomains` still receives `Key.funKey(id)`. That is correct for a
root key and for nothing else. T3a moves these lines to `Key.root(id)`.

1. **Write the loader tests** against `RootKeyStoreInMemory`.

```kotlin
@Test
fun `an empty store gets one root per domain`() {
    val roots = RootKeyLoader(RootKeyStoreInMemory<Long>(), counter()).load().block()!!
    assertThat(roots.keys).containsExactlyInAnyOrderElementsOf(ChatDomain.entries)
}

@Test
fun `a restart reads the stored roots and creates none`() {
    val store = RootKeyStoreInMemory<Long>()
    val first = RootKeyLoader(store, counter()).load().block()!!
    val second = RootKeyLoader(store, counter(start = 1000)).load().block()!!
    assertThat(second).isEqualTo(first)
}

@Test
fun `two loaders at once agree on one root per domain`() {
    val store = RootKeyStoreInMemory<Long>()
    val both = Mono.zip(
        RootKeyLoader(store, counter(0)).load().subscribeOn(Schedulers.parallel()),
        RootKeyLoader(store, counter(5000)).load().subscribeOn(Schedulers.parallel())
    ).block()!!
    assertThat(both.t1).isEqualTo(both.t2)
}

private fun counter(start: Long = 0): IKeyGenerator<Long> =
    AtomicLong(start).let { n -> object : IKeyGenerator<Long> { override fun nextId() = n.incrementAndGet() } }
```

2. **Run them and see them fail.**

3. **Write the loader.**

```kotlin
class RootKeyLoader<T>(private val store: RootKeyStore<T>, private val ids: IKeyGenerator<T>) {

    fun load(): Mono<Map<ChatDomain, T>> = store.read().flatMap { stored ->
        Flux.fromIterable(ChatDomain.entries)
            .concatMap { domain ->
                stored[domain]?.let { Mono.just(domain to it) }
                    ?: store.createIfAbsent(domain, ids.nextId()).map { domain to it }
            }
            .collectMap({ it.first }, { it.second })
    }.flatMap { roots ->
        val missing = ChatDomain.entries - roots.keys
        if (missing.isEmpty()) Mono.just(roots)
        else Mono.error(ChatException("Root keys incomplete. Missing: $missing"))
    }
}
```

4. **Write the three stores.** Memory uses `ConcurrentHashMap.putIfAbsent`.
   Redis uses the hash `chat:rootkeys:<keyType>` with `putIfAbsent`, then reads
   the field. Cassandra uses:

```sql
CREATE TABLE chat_long.root_keys (
    domain varchar,
    id BIGINT,
    PRIMARY KEY (domain)
);
```

   `chat_uuid.root_keys` uses `id uuid`. The write is
   `INSERT INTO root_keys (domain, id) VALUES (?, ?) IF NOT EXISTS`, then a read
   of the row. Neither truncate script names `root_keys`.

5. **Write the backend tests.** Redis and Cassandra each run the three loader
   cases against a container. Redis adds:

```kotlin
@Test
fun `two key types on one redis keep separate roots`() {
    RootKeyLoader(RootKeyStoreRedis(template, TypeUtil.LongUtil, "long"), longIds()).load().block()
    RootKeyLoader(RootKeyStoreRedis(template, TypeUtil.UUIDUtil, "uuid"), uuidIds()).load().block()

    assertThat(template.hasKey("chat:rootkeys:long").block()).isTrue()
    assertThat(template.hasKey("chat:rootkeys:uuid").block()).isTrue()
}
```

   Cassandra adds: after `truncate-long.cql` runs, `read()` still answers every
   root.

6. **Write the snapshot contract.** `RootKeySnapshot.of(rootKeys, keyType,
   typeUtil)` builds it. `RootKeySnapshot.load(into, expectedKeyType,
   typeUtil)` refuses a snapshot whose `keyType` differs, or that misses a
   domain, and the start fails. Test both refusals.

   `InitializingKVStore` changes to string names, so `ConsulKVStore` no longer
   builds a `Key`. `RootKeyService` writes and reads one snapshot under
   `app.kv.rootkeys`. The `rootkeys` actuator endpoint answers the snapshot.
   `HttpRootKeyConsumeOnStart` reads it.

7. **Replace the startup wiring.** In `RootKeyInitializationListeners`, remove
   both `app.rootkeys.create` beans. A node with a store runs
   `RootKeyLoader.load()` and `rootKeys.loadDomains(…)` on
   `ApplicationStartedEvent`, before `RootKeyInitializationReadyEvent`. It
   blocks, and a failure stops the start. The kv publish bean publishes the
   snapshot. A node with no store keeps the HTTP and kv consume beans. In
   `chat-build`, remove `-Dapp.rootkeys.create=true`, and update the golden
   cases in `test-flags.sh`.

8. **Add `RootKeysFixture`.** It replaces `GenerateRootKeyInitializer` in every
   test. Find those tests with `mcp__treesitter-mcp__find_usages` for
   `GenerateRootKeyInitializer`.

```kotlin
object RootKeysFixture {
    fun <T> of(ids: IKeyGenerator<T>): RootKeys<T> = RootKeys<T>().apply {
        loadDomains(ChatDomain.entries.associateWith { Key.funKey(ids.nextId()) })
        loadIdentities(Key.funKey(ids.nextId()), Key.funKey(ids.nextId()))
    }
}
```

   T3a moves the domains to `Key.root(id)`, and gives the two identities the
   `USER` root.

9. **Checkpoint.** `shell-scripts/build-health.sh --integration`, then
   `shell-scripts/test-flags.sh`. Expected: both exit 0.

10. **Commit.** `git commit -am "Load stable roots, and a root snapshot contract (CHAT-avduuqwp, CHAT-bafkgkko)"`

---

## T3a: Key contract in chat-core

FP: `CHAT-mnkpyjyw`. **Starts the coordinated change.**

**Files:**
- Modify: `chat-core/.../domain/KeyValuePair.kt` (the `Key` interface)
- Modify: `chat-core/.../domain/Message.kt` (`MessageKey`)
- Create: `chat-core/.../domain/Keys.kt`
- Modify: `chat-core/.../domain/RequestResponse.kt` (`ChatMessageKey`)
- Modify: `chat-core/.../service/core/KeyService.kt`
- Create: `chat-core/.../service/core/KeyVerifier.kt`, `VerifiedKey.kt`
- Create: `chat-core/.../domain/UnsupportedDomainException.kt`, `KeyVerificationException.kt`, `RootKeyDeletionException.kt`
- Modify: B1 to B4, `DomainWireShapeTests`, `E2eeWireShapeTests`
- Modify: every chat-core main and test site, found with `mcp__treesitter-mcp__find_usages` for `funKey`, `emptyKey`, and `create` on `MessageKey`
- Test: `KeyEqualityTests`, `KeyVerifierTests`, `KeyWireTests`

**Interfaces produced:**

```kotlin
interface Key<T> {
    val id: T
    val root: T
    val empty: Boolean
    companion object Factory {
        fun <T> of(id: T, root: T): Key<T>
        fun <T> root(id: T): Key<T>
        fun <T> empty(placeholder: T, root: T): Key<T>
    }
}

// MessageKey.of(id, root, from, dest): MessageKey<T>

interface IKeyService<T> {
    fun key(domain: ChatDomain): Mono<out Key<T>>
    fun rem(key: Key<T>): Mono<Void>       // RootKeyDeletionException for a root
    fun exists(key: Key<T>): Mono<Boolean>
    fun rootOf(id: T): Mono<T>             // empty when unknown. A root answers itself
}

class VerifiedKey<T> internal constructor(val key: Key<T>)

class KeyVerifier<T>(keys: IKeyService<T>, rootKeys: RootKeys<T>) {
    fun verify(key: Key<T>, expected: ChatDomain?): Mono<VerifiedKey<T>>
    fun resolve(id: T, expected: ChatDomain?): Mono<VerifiedKey<T>>
    /** Trusts its caller. No registry read. See the guarantee below. */
    fun trustTypedStore(key: Key<T>, domain: ChatDomain): VerifiedKey<T>
}
```

**What `VerifiedKey` guarantees, stated exactly.**

- The constructor is `internal`, so only code in `chat-core` can call it. That
  is a module boundary, not a proof. Any `chat-core` code could build one.
  **`KeyVerifierConstructionTests` enforces that `KeyVerifier.kt` is the only
  main source file that calls the constructor.** The test scans source text,
  because a test cannot call the semantic tools.
- `verify` and `resolve` read the registry. A `VerifiedKey` from either proves
  that the registry holds the id with that root.
- **`trustTypedStore` does not read the registry, and trusts its caller.** It
  checks only that the key root equals the root of `domain`. It accepts an
  unknown id with the right root. Its name says so. Its KDoc says so. It exists
  because `@PostFilter` evaluates entities that a typed store already returned,
  and a registry read per entity would double the read cost of `byIds`.
- **`trustTypedStore` has one permitted call site:**
  `SpringSecurityAccessBrokerService.hasAccessToEntity`. The same guard test
  fails on any other caller.
- The boundary tests of T4 stay. They prove each defense on its own. The type
  does not replace them.

1. **Write the equality tests.**

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
        val request = ChatMessageKey(1L, 2L, 3L, Instant.EPOCH)
        assertThat(request as Any).isNotInstanceOf(Key::class.java)
        assertThat(request.toKey(9L)).isEqualTo(MessageKey.of(1L, 9L, 2L, 3L))
    }
}
```

2. **Write the verifier tests** against `FakeKeyService`, a test class in
   `chat-core` test source. It holds an `id -> root` map, mints with
   `Key.of(next, rootKeys.of(domain).id)`, and answers `rootOf`. `chat-core`
   cannot depend on a persistence module, so the tests do not use
   `KeyServiceInMemory`.

```kotlin
@Test
fun `a forged root is refused`() {
    val minted = keys.key(ChatDomain.USER).block()!!
    StepVerifier.create(verifier.verify(Key.of(minted.id, rootKeys.of(ChatDomain.MESSAGE).id), null))
        .verifyError(KeyVerificationException::class.java)
}

@Test
fun `a key of another domain is refused`() {
    val minted = keys.key(ChatDomain.USER).block()!!
    StepVerifier.create(verifier.verify(minted, ChatDomain.MESSAGE_TOPIC))
        .verifyError(KeyVerificationException::class.java)
}

@Test
fun `an unknown id is refused`() {
    StepVerifier.create(verifier.resolve(424242L, null)).verifyError(KeyVerificationException::class.java)
}

@Test
fun `an epoch key with the franking root is refused, and the reverse`() {
    val epoch = keys.key(ChatDomain.CONVERSATION_EPOCH).block()!!
    val tag = keys.key(ChatDomain.FRANKING_TAG).block()!!

    StepVerifier.create(verifier.verify(Key.of(epoch.id, rootKeys.of(ChatDomain.FRANKING_TAG).id), null))
        .verifyError(KeyVerificationException::class.java)
    StepVerifier.create(verifier.verify(Key.of(tag.id, rootKeys.of(ChatDomain.CONVERSATION_EPOCH).id), null))
        .verifyError(KeyVerificationException::class.java)
}

@Test
fun `resolve reads the stored root`() {
    val minted = keys.key(ChatDomain.USER).block()!!
    assertThat(verifier.resolve(minted.id, ChatDomain.USER).block()!!.key).isEqualTo(minted)
}

@Test
fun `a root key resolves to itself`() {
    val root = rootKeys.of(ChatDomain.USER)
    assertThat(verifier.resolve(root.id, null).block()!!.key).isEqualTo(root)
}
```

3. **Write the wire tests,** for Jackson 2 and Jackson 3.

```kotlin
@Test
fun `a key writes id and root`() {
    assertThat(mapper.writeValueAsString(Key.of(1L, 9L))).isEqualTo("""{"key":{"id":1,"root":9}}""")
}

@Test
fun `a payload without root fails to decode`() {
    assertThatThrownBy { mapper.readValue("""{"key":{"id":1}}""", Key::class.java) }.hasMessageContaining("root")
}
```

3b. **Write the construction guard, and pin the trust.**

```kotlin
class KeyVerifierConstructionTests {

    @Test
    fun `only KeyVerifier constructs a VerifiedKey, and only one caller trusts a typed store`() {
        val constructors = mainSources().filter { it.name != "KeyVerifier.kt" && it.readText().contains("VerifiedKey(") }
        val trusters = mainSources().filter {
            it.name !in setOf("KeyVerifier.kt", "SpringSecurityAccessBrokerService.kt") && it.readText().contains("trustTypedStore(")
        }
        assertThat(constructors).isEmpty()
        assertThat(trusters).isEmpty()
    }

    @Test
    fun `trustTypedStore accepts an unknown id with the right root`() {
        val unknown = Key.of(424242L, rootKeys.of(ChatDomain.USER).id)
        assertThat(verifier.trustTypedStore(unknown, ChatDomain.USER).key).isEqualTo(unknown)
    }
}
```

   The second test is not a defect report. It pins the documented trust, so a
   later change that adds a registry read, or removes the root check, is seen.
   `mainSources()` walks every `chat-*/src/main` tree from the repository root.

4. **Change the interface, and write the three classes.**

```kotlin
object KeyEquality {
    /** Equality does not verify a root. See `KeyVerifier`. */
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

   Delete `funKey`, `emptyKey`, both `MessageKey.create` overloads, and
   `IKeyGenerator.nextKey()`. `from` and `dest` stay out of equality.

5. **Write the verifier.**

```kotlin
class KeyVerifier<T>(private val keys: IKeyService<T>, private val rootKeys: RootKeys<T>) {

    fun resolve(id: T, expected: ChatDomain?): Mono<VerifiedKey<T>> = keys.rootOf(id)
        .switchIfEmpty(Mono.error(KeyVerificationException("Key $id is not in the registry.")))
        .flatMap { root -> check(Key.of(id, root), expected) }

    fun verify(key: Key<T>, expected: ChatDomain?): Mono<VerifiedKey<T>> = resolve(key.id, expected)
        .flatMap { stored ->
            if (stored.key.root == key.root) Mono.just(VerifiedKey(key))
            else Mono.error(KeyVerificationException("Key ${key.id} carries root ${key.root}. The stored root is ${stored.key.root}."))
        }

    /**
     * **Trusts its caller.** It does not read the registry, so it accepts an
     * unknown id that carries the root of [domain]. Call it only for a key that
     * a typed store of [domain] returned. The one permitted caller is
     * `SpringSecurityAccessBrokerService.hasAccessToEntity`, and
     * `KeyVerifierConstructionTests` enforces that.
     */
    fun trustTypedStore(key: Key<T>, domain: ChatDomain): VerifiedKey<T> =
        if (key.root == rootKeys.of(domain).id) VerifiedKey(key)
        else throw KeyVerificationException("Key ${key.id} is not in ${domain.wireName}.")

    private fun check(key: Key<T>, expected: ChatDomain?): Mono<VerifiedKey<T>> =
        if (expected == null || rootKeys.of(expected).id == key.root) Mono.just(VerifiedKey(key))
        else Mono.error(KeyVerificationException("Key ${key.id} is not in ${expected.wireName}."))
}
```

6. **Require `root` in B1 to B4.**

```kotlin
val rootNode = node.get("root") ?: throw JsonMappingException.from(jp, "A key needs a root. The payload holds none.")
```

7. **`ChatMessageKey` stops implementing `MessageKey`,** and gains
   `fun toKey(root: T): MessageKey<T> = MessageKey.of(id, root, from, dest)`.

8. **Move the T2 lines to `Key.root(id)`.** `RootKeysFixture` gives the domains
   `Key.root(id)`, and the identities `Key.of(id, of(USER).id)`.

9. **Checkpoint.** `mvn -o -q -B -pl chat-core test`. Expected: chat-core
   compiles and every chat-core test passes, including `KeyEqualityTests`,
   `KeyVerifierTests` and `KeyWireTests`. The modules after chat-core do not
   compile yet.

10. **Commit.** `git commit -am "Key contract in chat-core; coordinated change, compiles through chat-core (CHAT-avduuqwp)"`

---

## T3b: Key contract in the persistence backends and their schema

FP: `CHAT-yfxyjnmp`.

**Files:**
- Modify: `KeyServiceInMemory.kt`, `KeyServiceRedis.kt`, `KeyServiceCassandra.kt`
- Modify: every store in `chat-persistence-memory`, `chat-persistence-redis`, `chat-persistence-cassandra`
- Modify: A8 to A14, and their repositories
- Modify: `keyspace-long.cql`, `keyspace-uuid.cql`. **Each change has its first consumer in this task:**
  - `keys (id, root)` replaces `keys (id, kind)`. The key services consume it.
  - `auth_metadata`, `auth_metadata_principal` and `auth_metadata_target` gain `principal_root` and `target_root`. `AuthMetadataById` and the index rows consume them.
  - `chat_secret.kind` is removed, with `CredKey.kind`.
  - `event_key_meta` is removed. No code consumes it.
- Test: `TestKeyServiceBase` in `chat-core` test source, and each backend subclass

1. **Rewrite the key service test base.**

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
    StepVerifier.create(keyService.rem(rootKeys.of(ChatDomain.USER))).verifyError(RootKeyDeletionException::class.java)
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

2. **Write the three key services.** Memory, in full:

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

   Redis stores `id -> root` in `chat:keys:<keyType>`. Cassandra writes a
   `keys (id, root)` row through a `CSKeyRow` class that does not implement
   `Key`.

3. **Each store maps rows.** A8 to A14 stop implementing `Key` and the domain
   interfaces. Each read builds the domain object with `Key.of(row.id, root())`,
   where `root()` is `rootKeys.of(<domain>).id`. Example:

```kotlin
override fun get(key: Key<T>): Mono<out User<T>> =
    userRepo.findByKeyId(key.id).map { row -> User.create(Key.of(row.key.id, root()), row.name, row.handle, row.imageUri) }
```

   `AuthMetadataById` maps `principal_root` and `target_root`. `CredKey` loses
   `kind`, and `CredentialSecretsStoreCassandra` stops writing `"CRED"`.

4. **Move each mint call** per the mint table in section C.

5. **Checkpoint.** `mvn -o -q -B -pl chat-core,chat-persistence-memory,chat-persistence-redis,chat-persistence-cassandra test -Pintegration`.
   Expected: PASS.

6. **Commit.** `git commit -am "Key contract in the backends and their schema; compiles through the backends (CHAT-avduuqwp)"`

---

## T3c: Key contract in the indexes

FP: `CHAT-ndkhtucf`.

**Files:** every index in `chat-index-lucene` and `chat-index-cassandra`, and
A15 to A20.

1. **Add one case to the shared index test base.** A key found by the index
   carries the root of the index domain.

```kotlin
@Test
fun `a found key carries the root of the index domain`() {
    index.add(entity).block()
    assertThat(index.findBy(queryFor(entity)).blockFirst()!!.root).isEqualTo(rootKeys.of(domain).id)
}
```

2. **Each index takes its `ChatDomain`,** and builds
   `Key.of(id, rootKeys.of(domain).id)` for every key it returns. C20 to C33.
   The Cassandra authorization index reads `principal_root` and `target_root`
   from its rows.

3. **Checkpoint.** `mvn -o -q -B -pl chat-core,chat-index-lucene,chat-index-cassandra test -Pintegration`. Expected: PASS.

4. **Commit.** `git commit -am "Key contract in the indexes; compiles through the indexes (CHAT-avduuqwp)"`

---

## T3d: Key contract in services, controllers, deploy, shell, crypto and the authorization server

FP: `CHAT-iatwqlml`.

**Files:** every remaining site in section C, D3 and D7.

1. **Write the alert tests.** A join alert carries a new message key, not the
   membership id.

```kotlin
@Test
fun `a join alert has its own message key`() {
    val alerts = RecordingPubSub<Long, String>()
    service(alerts).joinRoom(MembershipRequest(user.id, room.id)).block()

    val alert = alerts.sent.single()
    assertThat(alert.key.root).isEqualTo(rootKeys.of(ChatDomain.MESSAGE).id)
    assertThat(membershipKeys()).doesNotContain(alert.key.id)
}
```

   Write the same test for a leave alert.

2. **Write the credential tests,** with `WebTestClient` and a recording secrets
   store.

```kotlin
@Test
fun `a credential read resolves its owner in USER`() {
    val user = keys.key(ChatDomain.USER).block()!!
    secrets.stored[user] = "hash"
    client.get().uri("/secrets/{id}", user.id).exchange().expectStatus().isOk
}

@Test
fun `a credential read with a message id is refused, and the store is not called`() {
    val message = keys.key(ChatDomain.MESSAGE).block()!!
    client.get().uri("/secrets/{id}", message.id).exchange().expectStatus().isNotFound
    assertThat(secrets.calls).isEmpty()
}

@Test
fun `a credential mint is refused with 501`() {
    client.put().uri("/secrets/add").bodyValue("secret").exchange()
        .expectStatus().isEqualTo(501)
        .expectBody(String::class.java).value { assertThat(it).contains("KeyCredential") }
}
```

3. **Write the D1 tests** in `chat-crypto`. `InMemoryConversationEpochService`
   and `InMemoryFrankingService` take an `IKeyService<T>`. `CryptoServiceBeans`
   passes it. The UUID cast is removed.

```kotlin
@Test
fun `an epoch key resolves in CONVERSATION_EPOCH`() {
    val epoch = epochs.startEpoch(conversation).block()!!
    assertThat(verifier.resolve(epoch.key.id, ChatDomain.CONVERSATION_EPOCH).block()!!.key).isEqualTo(epoch.key)
}

@Test
fun `a franking key resolves in FRANKING_TAG`() {
    val tag = franking.tag(conversation, 1L, device, payload, secret).block()!!
    assertThat(verifier.resolve(tag.key.id, ChatDomain.FRANKING_TAG).block()!!.key).isEqualTo(tag.key)
}
```

```kotlin
@Test
fun `send answers a tag registered under FRANKING_TAG`() {
    val tag = messages.send(envelope).block()!!
    assertThat(keys.rootOf(tag.key.id).block()).isEqualTo(rootKeys.of(ChatDomain.FRANKING_TAG).id)
}
```

   `InMemoryEncryptedMessageService` takes the `FrankingService` bean.
   `generateTagSync` is removed, and `send` composes `generateTag`. The tests
   use the real method names of `FrankingService`, `ConversationEpochService`
   and `EncryptedMessageService`. Read them with
   `mcp__treesitter-mcp__view_code` before you write the tests.

4. **Write the D2 tests** in `chat-service-composite`.

```kotlin
@Test
fun `the job key and the topic key are distinct, with their own roots`() {
    val job = jobStore.start(nodeId, keyType, startedAt, incarnationId, worker).block()!!

    assertThat(job.key.id).isNotEqualTo(job.topicKey.id)
    assertThat(job.key.root).isEqualTo(rootKeys.of(ChatDomain.KEY_VALUE_PAIR).id)
    assertThat(job.topicKey.root).isEqualTo(rootKeys.of(ChatDomain.MESSAGE_TOPIC).id)
}

@Test
fun `a stored job keeps its topic reference`() {
    val job = jobStore.start(nodeId, keyType, startedAt, incarnationId, worker).block()!!
    assertThat(jobStore.readJob(job.key).block()!!.topicKey).isEqualTo(job.topicKey)
}

@Test
fun `a job record is published to the job topic`() {
    val job = jobStore.start(nodeId, keyType, startedAt, incarnationId, worker).block()!!
    writer.write(record(job)).block()
    assertThat(pubsub.sent.single().key.dest).isEqualTo(job.topicKey.id)
}
```

   Read the real `start` signature before you write these tests. Apply the D2
   table: `IndexJob.topicKey`, `JobRecord.topicKey`, the record `dest`, and the
   excluded topic ids.

4b. **Write the D3 lookup tests.** One parameterized test per reader, over the
   five failure cases. Each case builds its fault directly in the store or the
   index.

```kotlin
enum class LookupFault { MISSING, DUPLICATE, DANGLING, STORED_KEY_MISMATCH, TOPIC_MISMATCH }

@ParameterizedTest
@EnumSource(LookupFault::class)
fun `coverage fails on every lookup fault, and never selects an older job`(fault: LookupFault) {
    val older = succeededJob(at = t0)
    val newer = succeededJob(at = t1)
    inject(fault, newer)

    // An error signal carries no job, so the older job was not selected.
    StepVerifier.create(policy.selectCoveringJob())
        .verifyErrorSatisfies {
            assertThat(it).isInstanceOf(JobLookupException::class.java)
                .hasMessageContaining(fault.name.lowercase().replace('_', ' '))
        }
}

@ParameterizedTest
@EnumSource(LookupFault::class)
fun `the startup sweep logs a lookup fault and releases every other stale job`(fault: LookupFault) {
    val broken = runningJob()
    val healthy = runningJob()
    inject(fault, broken)

    startup.releaseStaleJobs().block()

    assertThat(jobStore.readJob(healthy.key).block()!!.outcome).isEqualTo(JobOutcome.RELEASED)
    assertThat(logs.errors()).anyMatch { it.contains(fault.name.lowercase().replace('_', ' ')) }
}
```

   `inject` builds each fault: MISSING removes the index entry, DUPLICATE adds a
   second entry for the topic, DANGLING removes the job from the store,
   STORED_KEY_MISMATCH stores a job whose `key` differs from its storage key,
   and TOPIC_MISMATCH stores a job whose `topicKey` has the right id and another
   root.

4c. **Split the initialization placeholder.** `InitialUsersService` keeps
   `Key.empty(placeholder, rootKeys.of(AUTH_METADATA).id)` for grants only.
   User creation that answers empty fails.

```kotlin
@Test
fun `initialization fails when a user cannot be created or found`() {
    givenUserServiceAnswersEmpty()
    assertThatThrownBy { initialUsers.initializeUsers() }.hasMessageContaining("Cannot initialize user")
}
```

5. **Move every R site in C39 to C77** to `verifier.resolve(id, <domain>)`, and
   pass `.key` to the service. The alert sites C49 and C50 mint through
   `messagePersistence.key()`. The credential routes resolve in `USER`. The
   registered client repository mints on the first save.

6. **Move the key mint routes.** `restKey` takes `DomainRequest(val domain: ChatDomain)`. An
   unknown value fails Jackson enum binding with 400, and no class loads. Test
   it with `{"domain":"java.lang.Runtime"}`. `IKeyServiceMapping.key` takes a
   `ChatDomain`.

7. **Refuse the two unsupported mints.** `restAddCredential` answers
   `Mono.error(UnsupportedDomainException("KeyCredential"))`, which the webflux
   exception handler maps to 501. The shell `key()` command prints the
   exception message and returns. Nothing else in `SecretsRestMapping` is
   refused.

8. **Checkpoint.** `mvn -o -q -B compile test-compile` over the whole reactor.
   Expected: compiles.

9. **Commit.** `git commit -am "Key contract in services and entry points; the reactor compiles (CHAT-avduuqwp)"`

---

## T3e: Require the root on the wire, and pass the coordinated gate

FP: `CHAT-dbvcsnww`.

1. **Prove B7.** For each pub/sub provider, publish a message and read it back
   through the provider codec. Assert that the root survives.

2. **Run the default gate.** `shell-scripts/build-health.sh`. Expected: exit 0.
   Update `docs/BUILD-HEALTH.md` when the counts move.

3. **Run the image gate.** `shell-scripts/build-health.sh --ci`. The wire shape
   changed, so the shell image must be rebuilt. Expected: exit 0.

4. **Commit.** `git commit -am "The coordinated key change passes the default and --ci gates (CHAT-avduuqwp)"`

**The coordinated change ends here.** The reactor is green from this commit on.

---

## T4: Verify inbound keys before authorization and persistence

FP: `CHAT-kliyrune`.

**Files:**
- Modify: `chat-core/.../service/security/AccessBroker.kt`: `target: VerifiedKey<T>`
- Modify: `chat-security/.../access/AuthMetadataAccessBroker.kt`, `SpringSecurityAccessBrokerService.kt`
- Create: `chat-webflux/.../config/ResolvedKeyArgumentResolver.kt`, and the `@Resolved` annotation
- Create: `chat-service-controller/.../config/rsocket/VerifiedKeyArgumentResolver.kt`
- Modify: every D1, D2 and D6 route
- Test: `chat-security/src/test/.../VerificationBoundaryTests.kt`
- Test: `chat-webflux/src/test/.../ResolvedKeyArgumentResolverTests.kt`
- Test: `chat-service-controller/src/test/.../VerifiedKeyArgumentResolverTests.kt`
- Test: `chat-service-controller/src/test/.../RouteSignatureGuardTests.kt`

**Why a type.** A route guard alone proves that a route is registered. It does
not prove that verification runs. `VerifiedKey` has an `internal` constructor in
`chat-core`, so the only way to hold one is through `KeyVerifier`. A route or a
broker that takes `VerifiedKey` cannot run without a verification before it.

1. **Write the boundary tests.** Each uses a recording broker or a recording
   store, and asserts zero calls for an invalid key.

```kotlin
@Test
fun `an unknown key never reaches the broker`() {
    val broker = RecordingBroker<Long>()
    val answer = service(broker).hasAccessTo(Key.of(424242L, userRoot.id), "GET")
        .contextWrite(authenticated()).block()

    assertThat(answer).isFalse()
    assertThat(broker.calls).isEmpty()
}

@Test
fun `a forged root never reaches the broker`() {
    val broker = RecordingBroker<Long>()
    val minted = keys.key(ChatDomain.USER).block()!!
    service(broker).hasAccessTo(Key.of(minted.id, topicRoot.id), "GET").contextWrite(authenticated()).block()

    assertThat(broker.calls).isEmpty()
}

@Test
fun `a forged entity key never reaches the store`() {
    val store = RecordingStore<User<Long>>()
    val forged = User.create(Key.of(424242L, userRoot.id), "n", "h", "http://u")

    StepVerifier.create(controller(store).add(forged)).verifyError(KeyVerificationException::class.java)
    assertThat(store.calls).isEmpty()
}
```

   Write one such test for each surface: the access service, each composite R
   site in C39 to C60, the web resolver, the messaging resolver, and D6.

2. **Mutation proof, one defense at a time.** For each surface, remove its one
   `verify` or `resolve` call. Run its test. **Expected: the test fails,
   because the recording broker or store receives a call.** Restore the call by
   absolute path, and prove the restore with `git status`. Record each mutation
   and its failing test in a comment on `CHAT-kliyrune`.

   Equality stays unmutated during these runs. The unknown key test does not
   depend on equality, because the id has no registry row.

3. **Change the broker signature.**

```kotlin
interface AccessBroker<T> {
    fun hasAccessByPrincipal(principal: Mono<Key<T>>, target: VerifiedKey<T>, action: String): Mono<Boolean>
    fun hasAccessByKey(principal: Key<T>, target: VerifiedKey<T>, action: String): Mono<Boolean>
    fun permittedTargets(principal: Key<T>, targets: List<VerifiedKey<T>>, perm: String): Flux<Key<T>>
}
```

   `hasAccessByKeyId(T, T)` resolves both ids through `KeyVerifier`. The
   principal comes from `ContextIdentity`, which reads a user that a typed
   store returned. The broker KDoc names that source.

4. **Verify in the access service.**

```kotlin
fun hasAccessTo(target: Key<T>, perm: String): Mono<Boolean> =
    verifier.verify(target, null)
        .flatMap { access.hasAccessByPrincipal(getSecurityContextPrincipal(), it, perm) }
        .onErrorReturn(false)
        .switchIfEmpty(Mono.just(false))

fun hasAccessToEntity(entity: Any?, perm: String, domain: ChatDomain): Mono<Boolean> =
    EntityTargets.keyOf<T>(entity)
        ?.let { key -> Mono.fromCallable { verifier.trustTypedStore(key, domain) } }
        ?.flatMap { access.hasAccessByPrincipal(getSecurityContextPrincipal(), it, perm) }
        ?.onErrorReturn(false)
        ?: Mono.just(false)
```

   `hasAccessToEntity` takes the domain of the store, because `@PostFilter`
   runs on entities that a typed store returned. It is the one permitted caller
   of `trustTypedStore`, and its KDoc repeats that the conversion trusts the
   store.

5. **Write the two argument resolvers.** The web resolver reads
   `@Resolved(domain = …)` and a path segment, and answers
   `verifier.resolve(typeUtil.fromString(segment), domain)`. The messaging
   resolver decodes a `Key` payload and answers `verifier.verify(key, domain)`.
   Register the messaging resolver through the `argumentResolverConfigurer` of
   `RSocketMessageHandler`. Test each resolver with a valid key, an unknown key,
   a forged root and a wrong domain.

6. **Move every D1, D2 and D6 route** to `VerifiedKey`. D6 verifies the entity
   key in the store domain before it calls the store.

7. **Write the route signature guard.** It reads the route interfaces through
   reflection, not through text.

```kotlin
@Test
fun `no route takes an unverified key or a raw path id`() {
    val offenders = routeInterfaces().flatMap { type ->
        type.methods
            .filter { it.isAnnotationPresent(MessageMapping::class.java) || it.hasWebMapping() }
            .flatMap { m ->
                m.parameters
                    .filter { p -> p.type == Key::class.java || p.isAnnotationPresent(PathVariable::class.java) }
                    .map { "${type.simpleName}.${m.name}(${it.name})" }
            }
    }
    assertThat(offenders).isEmpty()
}
```

   **The test KDoc states the limit.** The guard proves that each route declares
   the verified type. The type proves that verification ran, because only
   `KeyVerifier` builds it. Step 5 proves the resolvers themselves.

8. **Checkpoint.** `shell-scripts/build-health.sh`. Expected: exit 0.

9. **Commit.** `git commit -am "Verify inbound keys before the broker and the stores (CHAT-avduuqwp)"`

---

## T5: Enforce the store domain on every add

FP: `CHAT-yygzbzfc`.

Section E lists every add path and its key source. T5 enforces the check and
tests each path.

1. **Add two cases to the shared persistence test base.**

```kotlin
@Test
fun `add refuses a key of another domain`() {
    val foreign = keyService.key(otherDomain(domain)).block()!!
    StepVerifier.create(store.add(entity(foreign))).verifyError(KeyVerificationException::class.java)
}

@Test
fun `a read key carries the root of the store domain`() {
    val ent = entity(keyService.key(domain).block()!!)
    store.add(ent).block()
    assertThat(store.get(ent.key).block()!!.key.root).isEqualTo(rootKeys.of(domain).id)
}
```

2. **Run them and see the first one fail.**

3. **Add the check to each typed store `add`.**

```kotlin
override fun add(ent: User<T>): Mono<Void> =
    if (ent.key.root != root()) Mono.error(KeyVerificationException("Key ${ent.key.id} is not in User."))
    else userRepo.add(ent)
```

4. **Write the D2 store test.** The key-value store refuses a topic key.

```kotlin
@Test
fun `the key-value store refuses a topic key`() {
    val topicKey = keyService.key(ChatDomain.MESSAGE_TOPIC).block()!!
    StepVerifier.create(keyValueStore.add(KeyValuePair.create(topicKey, "job" as Any)))
        .verifyError(KeyVerificationException::class.java)
}
```

5. **Test each path E1 to E15** through its caller. Each test asserts that the
   write succeeds with its listed key source.

6. **Checkpoint.** `shell-scripts/build-health.sh --integration`. Expected:
   exit 0.

7. **Commit.** `git commit -am "Every store refuses a key of another domain (CHAT-avduuqwp)"`

---

## T6: Verify grant roots in authorize

FP: `CHAT-ihbesbmn`.

The columns exist since T3b. T6 adds verification.

1. **Write the tests.**

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

2. **Run them and see the first one fail.**

3. **Verify in `authorize`.** `CoreAuthorizationService` takes a `KeyVerifier`,
   and verifies the principal and the target before it writes.

4. **Mutation proof.** Remove the target verification alone. Expected: the
   forged root test fails. Restore by absolute path.

5. **Checkpoint.** `mvn -o -q -B -pl chat-core,chat-security,chat-deploy test`.
   Expected: PASS.

6. **Commit.** `git commit -am "Grants verify both roots before a write (CHAT-avduuqwp)"`

---

## T7: Check the complete store shape at start

FP: `CHAT-owyyuvpg`.

Every required schema element exists since T2 and T3b. T7 adds the start check.

1. **Write the Cassandra shape test.** One case per required element.

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

2. **Write the check.**

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
        ).map { it.getString("table_name")!! to it.getString("column_name")!! }
            .groupBy({ it.first }, { it.second })

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

3. **Write the Redis check and its test.** It fails when the hash `chat:keys`
   exists without a key type segment, and names the recreation.

4. **Checkpoint.** `shell-scripts/build-health.sh --integration`. Expected:
   exit 0.

5. **Commit.** `git commit -am "A start check for the complete store shape (CHAT-avduuqwp)"`

---

## T8: Update documents and run the final gate

FP: `CHAT-gwdifqbk`.

1. **Find stale prose.** This is a raw text search, so `grep` is correct here.

```bash
grep -rn -e "rootkeys.create" -e "funKey" -e "KnownRootKeys" -e "GenerateRootKeyInitializer" -e "chat:keys" docs forward-register.md
```

   Correct each hit that describes current behavior. Keep dated history, and
   add a correction line where the register does that.

2. **Drift.** `drift check`. Update prose before any `drift link`.

3. **Final gates.** `shell-scripts/build-health.sh --ci`,
   `just check-production-classpath`, and
   `shell-scripts/vector/gate-embedding-launch.sh`. Expected: each exits 0.

4. **Commit, push, and open the pull request.** Close each FP task with its
   evidence.

---

## Revision record

**Second revision, 2026-09-25, after the owner review of `c2ffe934`.**

1. **Wrong domains.** C49 and C50 reused membership ids for alert message keys.
   They now mint a `MESSAGE` key. C53 checked `messageById` in
   `MESSAGE_TOPIC`. It now resolves in `MESSAGE`.
2. **Bootstrap dependency.** C17 to C19 needed a `KEY_VALUE_PAIR` root to read
   the roots, and `RootKeyService` stores strings while roots are typed. T2 now
   separates store backed initialization from snapshot consumption, and the
   snapshot is a string keyed record.
3. **Credential refusal.** Only `restAddCredential` mints a credential key. The
   read, write by id and compare routes now resolve their owner in `USER`.
4. **Verification tests.** The first revision accepted a passing test after the
   `verify` call was removed. T4 now uses recording brokers and stores, one
   mutation per defense, and a `VerifiedKey` type that only `KeyVerifier`
   builds. The route guard reads signatures by reflection, and its KDoc states
   what it proves.
5. **Task order.** Each schema change now lands with its first consumer:
   `root_keys` in T2, and the `keys`, authorization root and `chat_secret`
   changes in T3b. The add path inventory is section E, measured before T5. The
   crypto domains are decision D1, before T3d.

Also: the transitional `root = id` factory is removed. T3a to T3e are one
coordinated change with compile checkpoints. Tracking uses FP tasks, and
navigation uses the semantic tools.

Found while revising: `VectorIndexJobStoreImpl` uses one key in two domains
(D2). `KeyValueStoreRegisteredClientRepository` stores under a configured
client id. `PersistenceStoreMapping.add` accepts a client entity (D6).

**Third revision, 2026-09-25, after the owner decided D1 and D2.**

- D1 is closed. `CONVERSATION_EPOCH` and `FRANKING_TAG` join `ChatDomain`, each
  with its own root. Both crypto services mint through the key service. C12 and
  C13 move from "per D1" to M.
- D2 is closed. The job key is minted in `KEY_VALUE_PAIR`, the topic key in
  `MESSAGE_TOPIC`, and `IndexJob` holds `topicKey`. The D2 table lists the four
  sites that read the job key as a topic id.
- D3 is new. Two readers find a job through its topic, and D2 breaks that path.
  The owner confirms the lookup before execution.
- The owner's required tests are in T3a step 2, T3d steps 3 and 4, and T5
  step 4.

**Fourth revision, 2026-09-25, after the owner review of `345ff40b`.**

- D3 is closed with a lookup contract: index the topic reference at creation,
  exactly one match, the stored key check, a topic check that includes the
  root, and five failure kinds that are errors. Coverage fails on each.
  The startup sweep logs each and continues. Tests cover every kind through
  both readers.
- `VerifiedKey` no longer overstates its guarantee. `internal` is a module
  boundary, and a guard test limits the constructor to `KeyVerifier.kt`.
  `fromTypedStore` is renamed `trustTypedStore`, states that it trusts its
  caller, and has one permitted call site, which the same guard enforces.
- C16 splits the placeholder. The grant placeholder takes the `AUTH_METADATA`
  root. A user creation that answers empty fails the initialization.
- D1 covers the send path. `InMemoryEncryptedMessageService` takes the
  franking bean, `generateTagSync` is removed, and a test proves that `send()`
  answers a tag registered under `FRANKING_TAG`.

# Imported message identity implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, inline in one session. `AGENTS.md` forbids subagent-driven development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Derive an imported message key id from the room id and the source message id, refuse a long key type at startup, and read the derived key directly on a rerun.

**Architecture:** A key-type declaration table in `chat-core` refuses an unsupported `app.key.type` in a `BeanFactoryPostProcessor`. `ImportedMessageId` derives a version 3 UUID from `<topicId>:<messageUuid>`. The command bus accepts a supplied message id on an import command alone. The composite reads the derived key before it submits, so the sender, time and text match retires. Four `msg_id` columns in `keyspace-uuid.cql` move from `TIMEUUID` to `UUID`.

**Tech Stack:** Kotlin 2.4, Spring Boot 4, Reactor, Cassandra, JUnit 5, AssertJ, StepVerifier, Testcontainers, Maven.

**Spec:** `docs/superpowers/specs/2026-10-15-imported-message-id-design.md`

**Issue:** `CHAT-zxrgxdlx`. Parent: `CHAT-emwhnlay`. Branch: `chat-zxrgxdlx-import-key-plan`.

**Task issues:** each task is an fp child of `CHAT-zxrgxdlx`. T1 is `CHAT-dmprweqf`. T2 is `CHAT-mdizzlif`. T3 is `CHAT-ojhsiueh`, and it depends on T2. T4 is `CHAT-bwzjstoh`, and it depends on T2 and T3. T5 is `CHAT-agqnxkbs`, and it depends on T4. T6 is `CHAT-irgksusu`. T7 is `CHAT-ijcgqfig`, and it depends on T1, T4 and T5. T8 is `CHAT-jorwpgjb`, and it depends on T4, T6 and T7.

**Three dependency edges are not obvious, and a reader must not drop them.**

- **T5 depends on T4.** Task 5 extends the UUID half of `CassandraMessageSendTests` to import through the composite and to read the message back by the derived id. That read is the Task 4 change.
- **T7 depends on T1.** Task 7 adds the startup refusal test, and the seam that refuses is the Task 1 declaration table.
- **T8 depends on T6 and T7.** Task 8 records the map artefact of Task 6 and the two image ids of Task 7 in the documents and the register.

## Global Constraints

- The derivation input is exactly `<topicId>:<messageUuid>`. The topic id is canonical UUID text. The colon is the separator.
- The generation value never enters the derivation. It enters the topic name alone.
- `MessageImportRequest.messageId` and the `import:` request id both stay. There is no wire change.
- The importer is the only production caller. It sends a room-scoped source id.
- Claude import supports the `uuid` key type alone. The refusal names the feature, the measured value, the supported set and the reason.
- `chat_long.*.msg_id` stays `BIGINT`. Only `keyspace-uuid.cql` changes.
- `idType` exists twice, in `CassandraStoreShapeCheck` and in `IndexServiceConfiguration`. Both sites move together.
- The no-migration rule of 2026-09-25 stands. An existing UUID store is recreated, never migrated.
- `git add` names exact files. Never stage a directory.
- Push and pull request steps need the owner's approval at that time. No step pushes on its own.
- Kotlin is the language. Match the comment density and naming of the file you edit.
- Agent-authored prose follows ASD-STE100 strict mode from `AGENTS.md`.
- One Maven build at a time in this checkout. A concurrent `clean` deletes the other build's output.
- Write Maven output to a log file. Read the exit code and the summary lines only.
- Run `mvn -o -pl chat-core,<module> test`, never `-pl <module>` alone.
- Container tests need `-Pintegration` and a running Docker.
- Every commit message names `CHAT-zxrgxdlx` and ends with `Co-Authored-By: Claude Code <noreply@anthropic.com>`.
- Log each finished task with `fp comment CHAT-zxrgxdlx "..."`.
- Restore every mutation by absolute path, and prove the restore with `git status --short`.

## Review Focus

These inputs are not exercised by every task's test. Each one is likely to hurt a user. Each line says what a user should expect.

1. **A rerun after the change reads a sentinel id, not the `import:` request id.** Expect one store read, and no new command. Task 4 covers it.
2. **An unscoped source id.** The derivation refuses it, because the derivation and the request id must scope the same way. Expect a `ChatException`, not a silently unscoped hash. Task 2 covers it.
3. **A long deployment that sets `app.message.import`.** Expect a refusal before any singleton is built, and a message that names the feature. Task 1 and Task 7 cover it.
4. **A version 1 UUID in a `uuid` column.** The live `CassandraUUIDKeyGenerator` produces version 1 UUIDs. Expect the widened column to accept them. Task 5 covers it.
5. **Two concurrent imports of one source.** Expect two stored messages. The read and the write are not atomic, and this limit is recorded, not fixed. Task 4 records it in KDoc.

---

## File Map

| File | Task | Change |
|---|---|---|
| `chat-core/src/main/kotlin/com/demo/chat/config/KeyTypeValidation.kt` | 1 | New declaration table and validator |
| `chat-core/src/test/kotlin/com/demo/chat/config/KeyTypeValidationTests.kt` | 1 | New matrix test |
| `chat-core/src/main/kotlin/com/demo/chat/service/command/ImportedMessageId.kt` | 2 | New derivation |
| `chat-core/src/test/kotlin/com/demo/chat/service/command/ImportedMessageIdTests.kt` | 2 | New derivation test |
| `chat-core/src/main/kotlin/com/demo/chat/domain/command/CommandTypes.kt` | 3 | `CommandSubmission.messageId` |
| `chat-core/src/main/kotlin/com/demo/chat/service/core/KeyAllocator.kt` | 3 | `adopt` |
| `chat-core/src/main/kotlin/com/demo/chat/service/command/CommandFingerprint.kt` | 3 | Version 4 with a supplied id |
| `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/MemoryDomainCommandBus.kt` | 3, 4 | Supply and refuse rules |
| `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/MemoryAdmissionTests.kt` | 3, 4 | Refusal cases |
| `chat-core/src/test/kotlin/com/demo/chat/test/command/CommandFingerprintTests.kt` | 3 | Version 4 case |
| `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessagingServiceImpl.kt` | 4 | Derive, read, drop the scan |
| `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MessageCommandRoutesTests.kt` | 4 | Rerun case |
| `shared-resources-cassandra/src/main/resources/keyspace-uuid.cql` | 5 | Four columns to `UUID` |
| `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraStoreShapeCheck.kt` | 5 | `idType` |
| `chat-index-cassandra/src/main/kotlin/com/demo/chat/config/index/cassandra/IndexServiceConfiguration.kt` | 5 | `idType` |
| `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/CassandraStoreShapeCheckTests.kt` | 5 | `uuid` expectation, `timeuuid` refusal |
| `chat-index-cassandra/src/test/kotlin/com/demo/chat/test/index/cassandra/IndexShapeTypesTests.kt` | 5 | `uuid` expectation |
| `chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraMessageSendTests.kt` | 5 | Derived id round trip |
| `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/ClaudeImportCommands.kt` | 6 | Write the map |
| `chat-shell/src/test/kotlin/com/demo/chat/test/commands/ClaudeImportMapTests.kt` | 6 | New map test |
| `chat-deploy-memory-integration-test/pom.xml` | 7 | Second image execution |
| `chat-shell/src/test/kotlin/com/demo/chat/test/init/UuidShellIntegrationTestBase.kt` | 7 | New UUID base |
| `chat-shell/src/test/kotlin/com/demo/chat/test/init/ClaudeImportDeploymentTests.kt` | 7 | Extend the UUID base |
| `chat-shell/src/test/kotlin/com/demo/chat/test/init/ImportKeyTypeRefusalTests.kt` | 7 | New startup refusal |
| `docs/BUILD-HEALTH.md` | 7 | Two image ids |
| `docs/superpowers/specs/2026-10-15-imported-message-id-design.md`, `docs/superpowers/plans/2026-10-15-imported-message-id.md` | 8 | This pair |
| `docs/MESSAGE-COMMANDS.md` | 8 | Derived id, direct read, switch, map |
| `docs/ARCHITECTURE.md` | 8 | The shape-check type paragraph |
| `forward-register.md` | 8 | One section |

---

### Task 1: The key-type refusal seam

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/config/KeyTypeValidation.kt`
- Create: `chat-core/src/test/kotlin/com/demo/chat/config/KeyTypeValidationTests.kt`

**Interfaces:**
- Produces: `object KeyTypeValidation` with `KEY_TYPE = "app.key.type"`, `IMPORT = "app.message.import"`, `REQUIREMENTS: List<KeyTypeRequirement>`, `enabled(resolver, feature)`, `validate(resolver)`. `data class KeyTypeRequirement(feature, switch, supported, reason)`. `open class KeyTypeValidationPostProcessor(environment) : BeanFactoryPostProcessor` and `open class KeyTypeValidationConfiguration`.

- [ ] **Step 1: Write the failing test**

Create `KeyTypeValidationTests.kt` with a matrix over `MapPropertySource` values. Cases:

```kotlin
// gate off, app.key.type=long starts
// gate on, app.key.type=uuid starts
// gate on, app.key.type=long refuses with the feature, the value, the set and the reason
// gate on, app.key.type unset refuses the same way
// gate present and empty counts as set, and refuses with a long key type
// gate set to "false" does not count as set
```

- [ ] **Step 2: Run it and see it fail**

```bash
mvn -o -pl chat-core test -Dtest=KeyTypeValidationTests > $LOG 2>&1; echo exit=$?; tail -20 $LOG
```

- [ ] **Step 3: Write the seam**

Copy the shape of `CommandBusValidation.kt`: an `object` with a declaration table, a `BeanFactoryPostProcessor`, and a `@Configuration` with a static `@Bean`. Copy the `enabled` rule of `CoreRestControllers.enabled` exactly: a present value that is not `false` counts as set, and an empty value counts as set.

- [ ] **Step 4: Run it and see it pass**

```bash
mvn -o -pl chat-core test -Dtest=KeyTypeValidationTests > $LOG 2>&1; echo exit=$?; tail -20 $LOG
```

- [ ] **Step 5: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/config/KeyTypeValidation.kt chat-core/src/test/kotlin/com/demo/chat/config/KeyTypeValidationTests.kt
git commit -m "Refuse an unsupported key type at startup (CHAT-zxrgxdlx)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
fp comment CHAT-zxrgxdlx "Task 1 done: the key-type refusal seam and its matrix test."
```

---

### Task 2: The derivation and the call refusal

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/command/ImportedMessageId.kt`
- Create: `chat-core/src/test/kotlin/com/demo/chat/service/command/ImportedMessageIdTests.kt`

**Interfaces:**
- Produces: `object ImportedMessageId` with `fun derive(sourceId: String, topicId: UUID): UUID` and `fun <T> deriveFor(typeUtil: TypeUtil<T>, dest: T, sourceId: String): T`.

- [ ] **Step 1: Write the failing test**

Cases: the id is stable across two calls. Two rooms give two ids for one source uuid. An unscoped source id is refused. A `Long` key type is refused, and the message names the type and the derivation.

- [ ] **Step 2: Run it and see it fail**

```bash
mvn -o -pl chat-core test -Dtest=ImportedMessageIdTests > $LOG 2>&1; echo exit=$?; tail -20 $LOG
```

- [ ] **Step 3: Write the derivation**

`derive` asserts that `sourceId` starts with `"$topicId:"`, then hashes `sourceId`. `deriveFor` checks the key type probe and destination before it calls `derive`. It refuses a non-UUID key type with a `ChatException` that names the type and derivation. Use `UUIDKeyGenerator` as the pattern reference for the name-based form.

- [ ] **Step 4: Run it and see it pass**

```bash
mvn -o -pl chat-core test -Dtest=ImportedMessageIdTests > $LOG 2>&1; echo exit=$?; tail -20 $LOG
```

- [ ] **Step 5: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/service/command/ImportedMessageId.kt chat-core/src/test/kotlin/com/demo/chat/service/command/ImportedMessageIdTests.kt
git commit -m "Derive the imported message id from the room and the source id (CHAT-zxrgxdlx)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
fp comment CHAT-zxrgxdlx "Task 2 done: the derivation and its refusal."
```

---

### Task 3: The bus carries a supplied message id

**Files:**
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/command/CommandTypes.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/service/core/KeyAllocator.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/service/command/CommandFingerprint.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/MemoryDomainCommandBus.kt`
- Modify: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/MemoryAdmissionTests.kt`
- Modify: `chat-core/src/test/kotlin/com/demo/chat/test/command/CommandFingerprintTests.kt`

**Interfaces:**
- Consumes: `CommandSubmission` from `chat-core`.
- Produces: `CommandSubmission.messageId: T? = null`, `KeyAllocator.adopt(domain: ChatDomain, id: T): Key<T>`.

- [ ] **Step 1: Write the failing tests**

A supplied id outside `IMPORT_MESSAGE` is refused. Two submissions under one request id with two different supplied ids are a `RequestConflictException`.

**The owner moved the refusal of an `IMPORT_MESSAGE` with no supplied id to Task 4 on 2026-10-15.** `MessagingServiceImpl` submits no id until Task 4. So a Task 3 refusal would break the import route between the two merges. The measured break was one failure in `MessageCommandServiceTests`. Task 3 keeps a minted id for an import with no id.

- [ ] **Step 2: Run them and see them fail**

```bash
mvn -o -pl chat-core test -Dtest=CommandFingerprintTests > $LOG 2>&1; echo exit=$?; tail -20 $LOG
mvn -o -pl chat-core,chat-service-composite test -Dtest=MemoryAdmissionTests > $LOG 2>&1; echo exit=$?; tail -20 $LOG
```

- [ ] **Step 3: Write the change**

Add the field after `target`, so every existing positional construction keeps its meaning. Add `adopt` to `KeyAllocator` beside `allocate`. In `MemoryDomainCommandBus.stageAndCommit`, use the supplied id when the operation is `IMPORT_MESSAGE`. The refusal is a `DefinitiveRefusalException`, which is the rule the pointer and its `target` follow. Extend `CommandFingerprint.of` with the supplied id and write `VERSION + 3`.

- [ ] **Step 4: Run the module tests**

```bash
mvn -o -pl chat-core,chat-service-composite test > $LOG 2>&1; echo exit=$?; tail -20 $LOG
```

- [ ] **Step 5: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/command/CommandTypes.kt chat-core/src/main/kotlin/com/demo/chat/service/core/KeyAllocator.kt chat-core/src/main/kotlin/com/demo/chat/service/command/CommandFingerprint.kt chat-core/src/test/kotlin/com/demo/chat/test/command/CommandFingerprintTests.kt chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/MemoryDomainCommandBus.kt chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/MemoryAdmissionTests.kt
git commit -m "Let an import command supply its message id (CHAT-zxrgxdlx)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
fp comment CHAT-zxrgxdlx "Task 3 done: the bus carries a supplied message id."
```

---

### Task 4: The composite reads the derived key directly

**Files:**
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessagingServiceImpl.kt`
- Modify: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MessageCommandRoutesTests.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/MemoryDomainCommandBus.kt`
- Modify: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/MemoryAdmissionTests.kt`

**Interfaces:**
- Consumes: `ImportedMessageId.deriveFor`, `CommandSubmission.messageId`.

- [ ] **Step 1: Write the failing test**

A rerun of one source id returns the stored message and submits nothing. Assert on the bus mapping count, not on the returned text alone.

- [ ] **Step 2: Run it and see it fail**

```bash
mvn -o -pl chat-core,chat-deploy-memory test -Dtest=MessageCommandRoutesTests > $LOG 2>&1; echo exit=$?; tail -20 $LOG
```

- [ ] **Step 3: Write the change**

In `importAsAdmin`, keep the three guards. Then derive the id with `ImportedMessageId.deriveFor`. Then read `messagePersistence.get(derivedKey)`. A live row is the answer. Otherwise submit with `messageId = derived`. **Remove the `history` scan and the text match.** Record the non-atomic limit in the KDoc.

Then make `MemoryDomainCommandBus.admit` refuse an `IMPORT_MESSAGE` with no supplied id. Use a `DefinitiveRefusalException`, beside the Task 3 check. Change the `MemoryAdmissionTests` case `an import with no supplied id still gets a minted id` into the refusal case. Assert that the refusal leaves no staged mapping. The owner moved this refusal from Task 3 on 2026-10-15.

- [ ] **Step 4: Measure the mutation**

Drop the direct read, so every call submits. Run the rerun case alone. Restore from git by absolute path.

- [ ] **Step 5: Run the module tests**

```bash
mvn -o -pl chat-core,chat-service-composite,chat-deploy-memory test > $LOG 2>&1; echo exit=$?; tail -20 $LOG
```

- [ ] **Step 6: Commit**

```bash
git add chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessagingServiceImpl.kt chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MessageCommandRoutesTests.kt chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/MemoryDomainCommandBus.kt chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/MemoryAdmissionTests.kt
git commit -m "Read the derived import key instead of scanning history (CHAT-zxrgxdlx)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
fp comment CHAT-zxrgxdlx "Task 4 done: the direct read, the removed scan, and the measured mutation."
```

---

### Task 5: The UUID message id column accepts a name-based UUID

**Depends on:** Task 4. The composite round trip reads the derived key, and Task 4 is that read.

**Files:**
- Modify: `shared-resources-cassandra/src/main/resources/keyspace-uuid.cql`
- Modify: `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraStoreShapeCheck.kt`
- Modify: `chat-index-cassandra/src/main/kotlin/com/demo/chat/config/index/cassandra/IndexServiceConfiguration.kt`
- Modify: `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/CassandraStoreShapeCheckTests.kt`
- Modify: `chat-index-cassandra/src/test/kotlin/com/demo/chat/test/index/cassandra/IndexShapeTypesTests.kt`
- Modify: `chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraMessageSendTests.kt`

- [ ] **Step 1: Write the failing tests**

Both type expectations read `uuid`. Add one `timeuuid` refusal case, built from the small keyspace idiom of `CHAT-pggtduxz`. Extend the UUID half of `CassandraMessageSendTests` to import a message through the composite and read it back by the derived id.

- [ ] **Step 2: Run them and see them fail**

```bash
mvn -o -pl chat-core,chat-persistence-cassandra test -Dtest='CassandraStoreShapeCheckTests' -Pintegration > $LOG 2>&1; echo exit=$?; tail -20 $LOG
mvn -o -pl chat-core,chat-index-cassandra test -Dtest='IndexShapeTypesTests' -Pintegration > $LOG 2>&1; echo exit=$?; tail -20 $LOG
mvn -o -pl chat-core,chat-deploy-cassandra test -Dtest='CassandraMessageSendTests' -Pintegration > $LOG 2>&1; echo exit=$?; tail -20 $LOG
```

- [ ] **Step 3: Write the change**

Four `msg_id TIMEUUID` columns in `keyspace-uuid.cql` move to `UUID`. Both `idType` functions return `"uuid"` for a `UUID` key type, and both KDocs follow. `keyspace-long.cql` does not change.

- [ ] **Step 4: Measure the mutation**

Leave one column as `TIMEUUID`. The shape check refuses it.

- [ ] **Step 5: Run the module tests**

```bash
mvn -o -pl chat-core,chat-persistence-cassandra,chat-index-cassandra test -Pintegration > $LOG 2>&1; echo exit=$?; tail -20 $LOG
```

- [ ] **Step 6: Commit**

```bash
git add shared-resources-cassandra/src/main/resources/keyspace-uuid.cql chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/CassandraStoreShapeCheck.kt chat-index-cassandra/src/main/kotlin/com/demo/chat/config/index/cassandra/IndexServiceConfiguration.kt chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/CassandraStoreShapeCheckTests.kt chat-index-cassandra/src/test/kotlin/com/demo/chat/test/index/cassandra/IndexShapeTypesTests.kt chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraMessageSendTests.kt
git commit -m "Widen the UUID message id column to UUID (CHAT-zxrgxdlx)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
fp comment CHAT-zxrgxdlx "Task 5 done: four columns, both idType sites, and the round trip."
```

---

### Task 6: The source-to-chat map artefact

**Files:**
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/ClaudeImportCommands.kt`
- Create: `chat-shell/src/test/kotlin/com/demo/chat/test/commands/ClaudeImportMapTests.kt`

- [ ] **Step 1: Write the failing tests**

The map is written beside the export. A rerun merges and does not replace. A reverse lookup resolves a source id to a message id. A write failure fails the run.

- [ ] **Step 2: Run them and see them fail**

```bash
mvn -o -pl chat-core,chat-shell test -Dtest=ClaudeImportMapTests > $LOG 2>&1; echo exit=$?; tail -20 $LOG
```

- [ ] **Step 3: Write the change**

Write `claude-import-map-<conversationUuid>-<generation>.json` after each thread completes. Hold the conversation uuid, the generation, the export path, `topics` and `messages`. Merge into an existing file. Fail the run on a write failure.

- [ ] **Step 4: Run the class**

```bash
mvn -o -pl chat-core,chat-shell test -Dtest='ClaudeImportMapTests,ClaudeImportCommandsTests' > $LOG 2>&1; echo exit=$?; tail -20 $LOG
```

- [ ] **Step 5: Commit**

```bash
git add chat-shell/src/main/kotlin/com/demo/chat/shell/commands/ClaudeImportCommands.kt chat-shell/src/test/kotlin/com/demo/chat/test/commands/ClaudeImportMapTests.kt
git commit -m "Write the source-to-chat import map (CHAT-zxrgxdlx)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
fp comment CHAT-zxrgxdlx "Task 6 done: the map artefact and its merge rule."
```

---

### Task 7: The UUID deployment image and the shell acceptance base

**Depends on:** Task 1, Task 4 and Task 5. The refusal test needs the Task 1 seam. The deployment tests need the Task 4 read and the Task 5 column.

**Files:**
- Modify: `chat-deploy-memory-integration-test/pom.xml`
- Create: `chat-shell/src/test/kotlin/com/demo/chat/test/init/UuidShellIntegrationTestBase.kt`
- Modify: `chat-shell/src/test/kotlin/com/demo/chat/test/init/ClaudeImportDeploymentTests.kt`
- Create: `chat-shell/src/test/kotlin/com/demo/chat/test/init/ImportKeyTypeRefusalTests.kt`
- Modify: `docs/BUILD-HEALTH.md`

- [ ] **Step 1: Add the second image execution**

The image name and `-Dapp.key.type` become properties. The `test-build` profile gains a second `build-image` execution for `docker.io/library/chat-deploy-uuid-memory-integration-test:0.0.1` with `-Dapp.key.type=uuid`. **Do not add a second profile over the same plugin.** Two profiles would merge their `<image>` blocks, which is the silent coupling this repository has already met.

- [ ] **Step 2: Add the UUID base**

`UuidShellIntegrationTestBase` names its own image and its own container, and it sets `app.key.type=uuid` in its property source. The container publishes ephemeral host ports, so both images can run in one JVM.

- [ ] **Step 3: Move the deployment tests and add the refusal test**

`ClaudeImportDeploymentTests` extends the UUID base. `ClaudeImportCommandsTests` keeps its `Long` fixtures, so the command stays pinned as key-type agnostic. The refusal test sets `app.message.import` with `app.key.type=long` and asserts the start fails and names the feature.

- [ ] **Step 4: Run the shell tests against the images**

```bash
DOCKER_CONFIG=$(mktemp -d) mvn -o -pl chat-core,chat-shell test -Pintegration,test-build > $LOG 2>&1; echo exit=$?; tail -30 $LOG
```

- [ ] **Step 5: Record the two image ids**

Read both image ids, and record them in `docs/BUILD-HEALTH.md` under the shell image passage.

- [ ] **Step 6: Commit**

```bash
git add chat-deploy-memory-integration-test/pom.xml chat-shell/src/test/kotlin/com/demo/chat/test/init/UuidShellIntegrationTestBase.kt chat-shell/src/test/kotlin/com/demo/chat/test/init/ClaudeImportDeploymentTests.kt chat-shell/src/test/kotlin/com/demo/chat/test/init/ImportKeyTypeRefusalTests.kt docs/BUILD-HEALTH.md
git commit -m "Import on a UUID deployment image (CHAT-zxrgxdlx)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
fp comment CHAT-zxrgxdlx "Task 7 done: the UUID image, the UUID base, and the fast-refusal test."
```

---

### Task 8: Documents

**Depends on:** Task 4, Task 6 and Task 7. The register section records the map artefact and the two image ids.

**Files:**
- Modify: `docs/MESSAGE-COMMANDS.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `forward-register.md`

- [ ] **Step 1: Update the bound document**

`docs/MESSAGE-COMMANDS.md` gains the derived id, the direct read, the `app.message.import` switch and the map. **This document is bound in `drift.lock`.** Review the prose, then relink with `drift link`, then verify with `drift check`. Never relink stale prose.

- [ ] **Step 2: Update the architecture note**

`docs/ARCHITECTURE.md`: the shape-check paragraph that names `timeuuid` for a `uuid` key type.

- [ ] **Step 3: Add the register section**

Add one section to `forward-register.md` in the style of the sections before it. Record the branch and the issue, the owner decisions of 2026-10-15, the four `TIMEUUID` to `UUID` columns, both `idType` sites, the removed history scan, the two image ids, both gate totals, and every mutation with its measured result. Do not move the `Checkout` row. See the rule under `Where things stand`.

- [ ] **Step 4: Run both gates**

```bash
shell-scripts/build-health.sh > $LOG 2>&1; echo exit=$?; tail -20 $LOG
DOCKER_CONFIG=$(mktemp -d) shell-scripts/build-health.sh --ci > $LOG 2>&1; echo exit=$?; tail -20 $LOG
```

Expected: exit 0 for both, no drift, and `agent http gate: ok`. Read `docker events --filter event=oom --since 2h` before you trust a red Cassandra result. Read both image ids before and after, because the shell tests run against the image and not against the reactor.

- [ ] **Step 5: Check the drift and the whitespace**

```bash
drift check; git diff --check; echo exit=$?
```

Expected: `ok`, and exit 0.

- [ ] **Step 6: Commit and hand off**

```bash
git add docs/MESSAGE-COMMANDS.md docs/ARCHITECTURE.md forward-register.md
git commit -m "Record imported message identity in the documents (CHAT-zxrgxdlx)

Co-Authored-By: Claude Code <noreply@anthropic.com>"
fp comment CHAT-zxrgxdlx "Task 8 done: documents, register, both gates green."
```

Report the gate totals to the owner, and stop. Ask the owner whether to push the branch and open a pull request. Do not push and do not open a pull request without that answer.

---

## Limits recorded, not fixed

- **An existing UUID keyspace fails the start.** Its four message id columns are `timeuuid`. The operator recreates the store.
- **No deployment sets `app.message.import`.** The import stays uuid-only and test-only after this work.
- **A pre-change import is not recognized by the derived read.** Its stored id was minted, so a rerun imports a duplicate.
- **The read and the write are not atomic.** Two concurrent imports of one source can both submit.
- **`app.key.type` is a launch value, not a store fact.** The seam reads the declaration, and it does not detect a store built for another key type.

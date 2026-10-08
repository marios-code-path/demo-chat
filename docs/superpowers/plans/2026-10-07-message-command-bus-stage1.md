# Message Command Bus Stage 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task by task, inline in one session. `AGENTS.md` forbids subagent-driven development, so do not dispatch a subagent for a task.

**Tracking:** FP holds all task state. This plan uses no markdown checklists. Task 0 creates one FP child issue per task. At the start of a task, run `fp issue update --status in-progress <task-issue>`. After each step that passes, run `fp comment <task-issue> "Step N: <result>"`. At the end of a task, run `fp issue update --status done <task-issue>`.

**Goal:** Replace the sequential message send with captured commands, independent backend handlers, and caller completion waits, on a `memory` command bus in one process.

**Architecture:** `chat-core` holds the command types, the contracts, the fingerprint, the key allocator, and the startup validation. `chat-service-composite` holds the `memory` bus runtime, the room coordinator, the four message handlers, and the rewired `MessagingServiceImpl`. The transports, the shell, and the security layer gain the submit and status operations. The legacy send stays as an adapter.

**Tech Stack:** Kotlin 2.4, Java 25, Spring Boot 4.0.8, Reactor 3.8, JUnit 6, AssertJ, Mockito, Testcontainers for Redis and Cassandra.

**Spec:** `docs/superpowers/specs/2026-10-07-domain-command-bus-design.md`. Read the sections `Delivery stages`, `Stage 1 decision recommendations`, and `Verification requirements` before Task 1.

## Global Constraints

- Stage 1 delivers the `memory` command bus in one process. Do not add Kafka code.
- `app.command.bus` must be `memory`. Startup rejects an unset value, an unknown value, and `kafka`.
- `app.command.ttl` must be absent or `disabled`. Stage 1 startup rejects a finite value.
- `app.command.replicas` defaults to `1`. Stage 1 startup rejects any other value.
- `app.command.completion.requirement` defaults to `P,I`. `app.command.completion.timeout` defaults to `5s`. Both are configurable starting values, not production measurements.
- `app.command.recovery.interval` defaults to `30s`. It is a starting value, not a production measurement.
- The duration syntax is an integer and one unit: `ms`, `s`, `m`, `h`, or `d`. Example: `30s`.
- The initial execution cycle makes at most five attempts, including the first attempt. In Reactor that is `Retry.backoff(4, Duration.ofMillis(100))`.
- An uncertain result enters recovery: one attempt per recovery interval, with the same command ID, message key, timestamp, and content.
- Every active handler declares a safe-repeat contract and version. Startup rejects a missing or unsupported contract before admission accepts a command.
- Admission writes no key registry row. `P` registers the assigned key, then stores the message.
- One commit marker exposes the admission mapping and the dispatch entry together.
- The dispatcher keeps per-room staging order, and it stops at a hidden entry of a room.
- A notification failure after the admission commit does not reject the admission. The dispatcher rescans every second.
- The publication record and the repeat marker commit together, in one room coordinator task, after `EmitResult.OK`.
- Subscriber callbacks never run inside a coordinator task.
- A listener drops each message ID that it already emitted, for the whole subscription.
- An ordinary submission binds its sender to the authenticated user. The legacy RSocket send rejects a different `from` value.
- The legacy send adapters give no retry safety. The API documentation must say so.
- The fingerprint is version `1`: a 4-byte big-endian version, then each field as a 4-byte big-endian length and its UTF-8 bytes, hashed with SHA-256.
- A request ID has 1 to 128 characters, each a visible ASCII character from `!` to `~`.
- Only a refusal that implements `NoEffectRefusal` is definitive. An unknown error can follow an effect, so the runtime records it as uncertain.
- The `30s` attempt watchdog marks a running attempt as uncertain. It never cancels the attempt. No later attempt and no later command of that room and backend starts until the earlier attempt ends.
- Only an authenticated user who is not `Anon` owns a submission. An anonymous caller cannot submit, cannot use the legacy send, and cannot read a status.
- Stage 1 supports only providers with safe-repeat evidence in CI. Key: `memory`, `redis`, `cassandra`. Persistence: `memory`, `redis`, `cassandra`. Index: `lucene`, `cassandra`. Vector: `simple`, `embedded`, `redis`. Pubsub: `memory`, `redis-pubsub`, `kafka`. Startup rejects any other set value.
- A task that changes a behavior also repairs every test that the change breaks, in the same task.
- Write all new prose in the controlled English of `AGENTS.md`: short sentences, no semicolons, active voice.
- Run Maven from the repository root. Never run `-pl <module>` without `chat-core`. Use the full reactor for boot tests.
- Log build output to a file. Read the exit code and the summary lines only.

## Review Focus

These inputs are likely to reach a real user, and the spec cases do not name them. Each line has a test in the named task.

1. An `Idempotency-Key` that is empty, longer than 128 characters, or holds a space. Expected: REST answers 400, and nothing is admitted. Test in Task 19.
2. A room that was deleted or never opened when `U` runs. Expected: `U` records a definitive failure, not an uncertain result, and `[P, I]` still completes. Test in Task 14.
3. A listener that cancels during the history read. Expected: the live subscription is disposed, and the room keeps no subscriber. Test in Task 16.
4. An anonymous caller that submits, sends, or reads a status. The spec scopes identity to an authenticated owner, and every anonymous caller holds one shared `Anon` key. Expected: Access Denied before admission, and nothing admitted. Tests in Tasks 15 and 16.
5. A caller that reads the status of a command that another user owns. Expected: 404 on REST and `Object not Found` on RSocket, with no command data. Tests in Tasks 16, 18, and 19.

---

## File Structure

### `chat-core` (`src/main/kotlin/com/demo/chat/...`)

| File | Responsibility |
|---|---|
| `domain/command/CommandTypes.kt` | Backend letters, requirement, states, receipt, status, result, accepted command, submission |
| `domain/command/CommandExceptions.kt` | Conflict, sender mismatch, pending, incomplete, uncertain, request ID, submitter errors |
| `domain/KeyRegistrationExceptions.kt` | `KeyRootConflictException` and `RootKeyRegistrationException` |
| `domain/RequestResponse.kt` | Add `MessageSubmitRequest` and `CommandStatusRequest` |
| `service/command/CommandContracts.kt` | `DomainCommandBus`, `CommandCompletionService`, `DomainCommandHandler`, `HandlerDescriptor`, `SubmitterIdentity` |
| `service/command/SafeRepeatContracts.kt` | The supported contract versions and the startup check |
| `service/command/BackendExecutionPolicy.kt` | Attempts, backoff, recovery interval, attempt timeout, error classes |
| `service/command/CommandFingerprint.kt` | The version 1 fingerprint |
| `service/command/RequestIds.kt` | Request ID validation |
| `service/core/KeyAllocator.kt` | Key assignment with no I/O |
| `service/core/KeyService.kt` | Add `register(key)` with a refusing default |
| `config/CommandBusSettings.kt` | Property names, duration parser, settings, validation |
| `config/CommandBusValidation.kt` | The `BeanFactoryPostProcessor` and its configuration |
| `service/composite/ChatMessageService.kt` | Add `submit` and `commandStatus` |

### `chat-service-composite`

| File | Responsibility |
|---|---|
| `service/composite/command/memory/CommitMarker.kt` | The one commit marker |
| `service/composite/command/memory/MemoryCommandStatusStore.kt` | Hidden and visible status records, `observe`, `await` |
| `service/composite/command/memory/DispatchLog.kt` | Staged and committed dispatch entries |
| `service/composite/command/memory/MemoryDomainCommandBus.kt` | Admission: lock, staging, commit, rollback, notification |
| `service/composite/command/memory/CommandDispatcher.kt` | Log scan, per-room order, periodic rescan |
| `service/composite/command/memory/BackendRuntime.kt` | Per-room FIFO, initial cycle, recovery cycle |
| `service/composite/command/memory/MemoryCommandRuntime.kt` | Builds and closes the whole runtime |
| `service/composite/command/publication/SerialTaskQueue.kt` | One task at a time |
| `service/composite/command/publication/RoomPublications.kt` | Room coordinator, publication log, repeat markers, subscription boundary |
| `service/composite/command/handler/MessageCommandHandlers.kt` | The `P`, `I`, `V`, and `U` handlers |
| `service/composite/impl/MessagingServiceImpl.kt` | Submit, legacy send, status, listen with replay |
| `service/composite/access/MessagingServiceAccess.kt` | Delegate the two new operations |
| `config/service/composite/command/MessageCommandConfiguration.kt` | Beans for the runtime, the handlers, and the publications |
| `config/service/composite/CompositeServiceBeansConfiguration.kt` | Pass the new collaborators to `MessagingServiceImpl` |

### Other modules

| File | Responsibility |
|---|---|
| `chat-persistence-memory/.../KeyServiceInMemory.kt` | `register` with `putIfAbsent` |
| `chat-persistence-redis/.../KeyServiceRedis.kt` | `register` with `HSETNX` |
| `chat-persistence-cassandra/.../KeyServiceCassandra.kt` | `register` with `IF NOT EXISTS` |
| `chat-messaging-memory/.../MemoryTopicPubSubService.kt` | Keep each room sink open with an internal subscriber, read the emit result, deliver through `publishOn` |
| `chat-service-controller/.../KeyServiceMapping.kt` | The `key.register` route |
| `chat-service-controller/.../RSocketSecurityConfiguration.kt` | Protect `key.register` |
| `chat-service-controller/.../MessageServiceControllerMapping.kt` | `message-submit` and `message-command-status` |
| `chat-client-rsocket/.../KeyClient.kt` | `register` over RSocket |
| `chat-client-rsocket/.../MessagingClient.kt` | `submit` and `commandStatus` over RSocket |
| `chat-security/.../MessageServiceAccess.kt` | Checks on the two new operations |
| `chat-security/.../ContextSubmitterIdentity.kt` | The `SubmitterIdentity` adapter over `ContextIdentity` |
| `chat-security/config/auth/SubmitterIdentityConfiguration.kt` | Registers the adapter under `app.service.composite.auth` |
| `chat-webflux/.../ChatMessageServiceRestMapping.kt` | `POST /message/submit/{id}` and `GET /message/command/{commandId}` |
| `chat-webflux/.../WebFluxKeyVerifierConfiguration.kt` | `KeyRefusalAdvice` handlers for the new errors |
| `chat-shell/.../PubSubCommands.kt` and `PubSubCommandsRegistrar.kt` | Submit with a request ID, and a status command |
| `shell-scripts/chat-build` and `shell-scripts/golden/*.flags` | Emit `-Dapp.command.bus=memory` |
| `chat-deploy-memory-integration-test/pom.xml` | Add the selector to the image flags |
| `docs/MESSAGE-COMMANDS.md` | Operator and API documentation |

The `memory` bus lives in `chat-service-composite`. That module already holds the composite services, and every deployment already depends on it. So Stage 1 adds no Maven module. Stage 2 adds a `kafka` module.

### Stage 1 case map

| Cases | Task |
|---|---|
| 1, 2, 4, 8, 9 | Task 9 |
| 5, 6, 7, 12, 17, 29, 30 | Task 10 |
| 9, 13, 22, 25, 26, 27 | Task 11 |
| 13, 23, 24 | Task 3 |
| 18, 19 | Tasks 5, 6, 7 |
| 14, 31, 32 | Task 13 |
| 19, 20 | Task 14 |
| 3, 10, 15, 16, 21, 28 | Task 16 |
| 11, 28 | Tasks 18 and 19 |
| 13, 23, 24, 27 | Task 17, at startup of a real composition |
| Safe-repeat evidence for each supported provider | Task 17 |

---

### Task 0: Tracking and workspace

**Files:** none.

**Step 1: Create the tracking issue**

Run:

```bash
fp issue create --title "Implement message command bus Stage 1" --parent CHAT-uxlstrdy \
  --description "Execute docs/superpowers/plans/2026-10-07-message-command-bus-stage1.md"
```

Record the new issue ID. Each later commit message names it.

**Step 2: Create one child issue per task**

Run this once for each of Tasks 1 to 22, with the task title:

```bash
fp issue create --title "Stage 1 Task <N>: <task title>" --parent <stage-1-issue-id>
```

**Step 3: Create the worktree**

Use the superpowers:using-git-worktrees skill. Name the branch `chat-<stage-1-issue-suffix>-command-bus-stage1`. Cut it from `master`.

**Step 4: Record the baseline**

Run:

```bash
shell-scripts/build-health.sh > /tmp/stage1-baseline.log 2>&1; echo "exit $?"
tail -5 /tmp/stage1-baseline.log
```

Expected: `exit 0` and `reality matches docs/BUILD-HEALTH.md`. Record the test count with `fp comment`.

---

### Task 1: Command domain types

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/command/CommandTypes.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/command/CommandExceptions.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/NoEffectRefusal.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/Exception.kt` (`NotFoundException`)
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/KeyVerificationException.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/domain/RequestResponse.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/command/CommandTypesTests.kt`

**Interfaces:**
- Consumes: `MessageKey<T>`, `Message<T, V>`, `ChatException`.
- Produces: `NoEffectRefusal`, `DefinitiveRefusalException`, `BackendId`, `CompletionRequirement`, `CommandOperation`, `BackendState`, `BackendStatus`, `CallerOutcome`, `Receipt<T>`, `CommandStatus<T>`, `MessageSendResult<T>`, `AcceptedCommand<T, V>`, `CommandSubmission<T, V>`, `RequestIdentity<T>`, the exceptions below, `MessageSubmitRequest<T, V>`, and `CommandStatusRequest`.

**Step 1: Write the failing test**

```kotlin
package com.demo.chat.test.command

import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.BackendState
import com.demo.chat.domain.command.BackendStatus
import com.demo.chat.domain.command.CompletionRequirement
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CommandTypesTests {

    @Test
    fun `a requirement parses backend letters`() {
        assertThat(CompletionRequirement.parse("P,I").backends)
            .containsExactlyInAnyOrder(BackendId.PERSISTENCE, BackendId.INDEX)
    }

    @Test
    fun `a requirement parses none as the empty requirement`() {
        assertThat(CompletionRequirement.parse("none")).isEqualTo(CompletionRequirement.NONE)
        assertThat(CompletionRequirement.NONE.backends).isEmpty()
    }

    @Test
    fun `a requirement refuses an unknown letter and names it`() {
        assertThatThrownBy { CompletionRequirement.parse("P,X") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("'X'")
    }

    @Test
    fun `a pending backend status starts with zero attempts`() {
        assertThat(BackendStatus(BackendState.PENDING).attempts).isZero()
    }
}
```

**Step 2: Run the test to verify that it fails**

Run: `mvn -o -B -pl chat-core test -Dtest=CommandTypesTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t1.log 2>&1; echo "exit $?"; grep -E "Tests run|ERROR" /tmp/t1.log | tail -5`
Expected: `exit 1`. The compiler reports `Unresolved reference 'command'`.

**Step 3: Write the types**

`CommandTypes.kt`:

```kotlin
package com.demo.chat.domain.command

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import java.time.Instant

/** The logical backends of a message command. The letters match the spec. */
enum class BackendId(val letter: String) {
    PERSISTENCE("P"), INDEX("I"), VECTOR("V"), PUBSUB("U");

    companion object {
        fun ofLetter(letter: String): BackendId =
            entries.firstOrNull { it.letter == letter.trim() }
                ?: throw IllegalArgumentException("Unknown backend letter '${letter.trim()}'. The known letters are P, I, V, and U.")
    }
}

/** The backends that a caller waits for. The empty set is `None`. */
data class CompletionRequirement(val backends: Set<BackendId>) {
    companion object {
        val NONE = CompletionRequirement(emptySet())

        /** This method parses `P,I` or `none`. */
        fun parse(text: String): CompletionRequirement =
            if (text.trim().equals("none", ignoreCase = true)) NONE
            else CompletionRequirement(text.split(',').filter { it.isNotBlank() }.map(BackendId::ofLetter).toSet())
    }
}

enum class CommandOperation { RECORD_MESSAGE }

/** `UNCERTAIN` stays pending for the caller. It contributes no success and is not a terminal failure. */
enum class BackendState { PENDING, SUCCEEDED, FAILED, UNCERTAIN }

data class BackendStatus(
    val state: BackendState,
    val attempts: Int = 0,
    val reason: String? = null,
    val nextRecoveryAt: Instant? = null,
)

enum class CallerOutcome { ACCEPTED, PENDING, COMPLETED, INCOMPLETE }

data class Receipt<T>(val commandId: String, val messageKey: MessageKey<T>)

data class CommandStatus<T>(
    val commandId: String,
    val owner: T,
    val requestId: String,
    val receipt: Receipt<T>,
    val backends: Map<BackendId, BackendStatus>,
    val version: Long,
)

data class MessageSendResult<T>(
    val receipt: Receipt<T>,
    val outcome: CallerOutcome,
    val backends: Map<BackendId, BackendStatus>,
)

/** One admission identity. The owner comes from the trusted service boundary. */
data class RequestIdentity<T>(val owner: T, val requestId: String)

/** The input of admission. Stage 1 has one operation, `RECORD_MESSAGE`. */
data class CommandSubmission<T, V>(
    val owner: T,
    val requestId: String,
    val sender: T,
    val dest: T,
    val content: V,
)

/** One accepted command. Every attempt and every recovery uses these values. */
data class AcceptedCommand<T, V>(
    val commandId: String,
    val owner: T,
    val requestId: String,
    val rootId: T,
    val operation: CommandOperation,
    val orderingKey: T,
    val schemaVersion: Int,
    val message: Message<T, V>,
    val obligations: Set<BackendId>,
    val executionPolicyVersion: Int,
    val expiresAt: Instant? = null,
)
```

`CommandExceptions.kt`:

```kotlin
package com.demo.chat.domain.command

import com.demo.chat.domain.ChatException

class RequestConflictException(val requestId: String) :
    ChatException("The request $requestId already names a different message.")

class SenderMismatchException(val sender: Any?, val user: Any?) :
    ChatException("The sender $sender is not the authenticated user $user. Stage 1 does not allow another sender.")

class SubmitterUnavailableException :
    ChatException("No submitter identity exists. A message submission needs app.service.composite.auth=true.")

class InvalidRequestIdException(reason: String) : ChatException("The request ID is not valid. $reason")

class CommandPendingException(val commandId: String) :
    ChatException("Command $commandId is still pending. Read its status with the command ID.")

class CommandIncompleteException(val commandId: String, val failed: Set<BackendId>) :
    ChatException("Command $commandId failed in ${failed.joinToString { it.name }}.")

/** A handler throws this when an effect may have happened. The runtime records an uncertain result. */
class UncertainOutcomeException(message: String, cause: Throwable? = null) : ChatException(message, cause)
```

`NoEffectRefusal.kt`:

```kotlin
package com.demo.chat.domain

/**
 * A refusal that happens before any backend effect. Decision 12 of the spec.
 * Only these errors are definitive. Any other error after an attempt starts
 * can follow an effect, so the runtime records it as uncertain.
 */
interface NoEffectRefusal

/** A general refusal before any effect, for a handler that has no narrower type. */
class DefinitiveRefusalException(message: String) : ChatException(message), NoEffectRefusal
```

Mark the two existing refusals that a message handler can raise before a write:

```kotlin
object NotFoundException : ChatException("Object not Found"), NoEffectRefusal
```

```kotlin
class KeyVerificationException(message: String) : ChatException(message), NoEffectRefusal
```

Add to `CommandTypesTests`:

```kotlin
    @Test
    fun `the refusals before a write carry the marker`() {
        assertThat(com.demo.chat.domain.NotFoundException).isInstanceOf(com.demo.chat.domain.NoEffectRefusal::class.java)
        assertThat(com.demo.chat.domain.KeyVerificationException("x")).isInstanceOf(com.demo.chat.domain.NoEffectRefusal::class.java)
    }
```

Append to `RequestResponse.kt`
, after `MessageSendRequest`. The class is sealed, so the subclasses must stay in this file:

```kotlin
@JsonTypeName("MessageSubmitRequest")
data class MessageSubmitRequest<T, V>(val msg: V, val dest: T, val requestId: String) : RequestResponse<T>()

@JsonTypeName("CommandStatusRequest")
data class CommandStatusRequest(val commandId: String) : RequestResponse<Any>()
```

**Step 4: Run the test to verify that it passes**

Run: `mvn -o -B -pl chat-core test -Dtest=CommandTypesTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t1.log 2>&1; echo "exit $?"; grep -E "Tests run:" /tmp/t1.log | tail -1`
Expected: `exit 0` and `Tests run: 5, Failures: 0, Errors: 0`.

**Step 5: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain chat-core/src/test/kotlin/com/demo/chat/test/command/CommandTypesTests.kt
git commit -m "Add the message command types (<task-1-issue>)"
```

---

### Task 2: Command contracts and the execution policy

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/command/CommandContracts.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/command/SafeRepeatContracts.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/command/BackendExecutionPolicy.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/command/BackendExecutionPolicyTests.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/command/SafeRepeatContractsTests.kt`

**Interfaces:**
- Consumes: Task 1 types.
- Produces:
  - `interface DomainCommandBus<T, V> { fun submit(submission: CommandSubmission<T, V>): Mono<Receipt<T>> }`
  - `interface CommandCompletionService<T> { fun status(commandId: String): Mono<CommandStatus<T>>; fun observe(commandId: String): Flux<CommandStatus<T>>; fun await(commandId: String, requirement: CompletionRequirement, timeout: Duration): Mono<MessageSendResult<T>> }`
  - `data class HandlerDescriptor(backend: BackendId, roots: Set<ChatDomain>, operations: Set<CommandOperation>, safeRepeat: SafeRepeatContract?)`
  - `interface DomainCommandHandler<T, V> { val descriptor: HandlerDescriptor; fun handle(command: AcceptedCommand<T, V>): Mono<Void> }`
  - `interface SubmitterIdentity<T> { fun current(): Mono<Key<T>> }`
  - `data class SafeRepeatContract(name: String, version: Int)` and `object SafeRepeatContracts` with `MESSAGE_PERSISTENCE`, `MESSAGE_INDEX`, `MESSAGE_VECTOR`, `MESSAGE_PUBSUB`, `SUPPORTED`, `requireSupported(descriptor)`
  - `enum class FailureClass { DEFINITIVE, TRANSIENT, UNCERTAIN }`
  - `data class BackendExecutionPolicy(version, initialAttempts, firstBackoff, recoveryInterval, attemptTimeout)` with `classify(error): FailureClass` and `retriesAfterFirst: Long`

**Step 1: Write the failing tests**

`BackendExecutionPolicyTests.kt`:

```kotlin
package com.demo.chat.test.command

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.DefinitiveRefusalException
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.command.UncertainOutcomeException
import com.demo.chat.service.command.BackendExecutionPolicy
import com.demo.chat.service.command.FailureClass
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeoutException

class BackendExecutionPolicyTests {
    private val policy = BackendExecutionPolicy()

    @Test
    fun `five attempts include the first attempt`() {
        assertThat(policy.initialAttempts).isEqualTo(5)
        assertThat(policy.retriesAfterFirst).isEqualTo(4L)
    }

    @Test
    fun `the starting values match the spec`() {
        assertThat(policy.firstBackoff).isEqualTo(Duration.ofMillis(100))
        assertThat(policy.recoveryInterval).isEqualTo(Duration.ofSeconds(30))
    }

    @Test
    fun `a timeout and a connection error are transient`() {
        assertThat(policy.classify(TimeoutException("slow"))).isEqualTo(FailureClass.TRANSIENT)
        assertThat(policy.classify(IOException("reset"))).isEqualTo(FailureClass.TRANSIENT)
    }

    @Test
    fun `a wrapped driver timeout is transient`() {
        val wrapped = RuntimeException("data access", TimeoutException("driver"))
        assertThat(policy.classify(wrapped)).isEqualTo(FailureClass.TRANSIENT)
    }

    @Test
    fun `an uncertain outcome is uncertain even when it wraps a connection error`() {
        val error = UncertainOutcomeException("bookkeeping", IOException("reset"))
        assertThat(policy.classify(error)).isEqualTo(FailureClass.UNCERTAIN)
    }

    @Test
    fun `a refusal before any effect is definitive`() {
        assertThat(policy.classify(DefinitiveRefusalException("refused"))).isEqualTo(FailureClass.DEFINITIVE)
        assertThat(policy.classify(NotFoundException)).isEqualTo(FailureClass.DEFINITIVE)
    }

    @Test
    fun `an unknown error is uncertain, because it can follow an effect`() {
        assertThat(policy.classify(IllegalStateException("bug"))).isEqualTo(FailureClass.UNCERTAIN)
        assertThat(policy.classify(ChatException("unmarked"))).isEqualTo(FailureClass.UNCERTAIN)
    }

    @Test
    fun `a policy refuses fewer than one attempt`() {
        assertThatThrownBy { BackendExecutionPolicy(initialAttempts = 0) }
            .hasMessageContaining("at least one attempt")
    }
}
```

`SafeRepeatContractsTests.kt`:

```kotlin
package com.demo.chat.test.command

import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CommandOperation
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.command.HandlerDescriptor
import com.demo.chat.service.command.SafeRepeatContract
import com.demo.chat.service.command.SafeRepeatContracts
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SafeRepeatContractsTests {
    private fun descriptor(contract: SafeRepeatContract?) = HandlerDescriptor(
        BackendId.PERSISTENCE, setOf(ChatDomain.MESSAGE), setOf(CommandOperation.RECORD_MESSAGE), contract,
    )

    @Test
    fun `a supported contract passes`() {
        assertThatCode { SafeRepeatContracts.requireSupported(descriptor(SafeRepeatContracts.MESSAGE_PERSISTENCE)) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `a missing contract fails and names the backend`() {
        assertThatThrownBy { SafeRepeatContracts.requireSupported(descriptor(null)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("PERSISTENCE")
            .hasMessageContaining("declares no safe-repeat contract")
    }

    @Test
    fun `an unsupported version fails and names both versions`() {
        assertThatThrownBy { SafeRepeatContracts.requireSupported(descriptor(SafeRepeatContract("message-persistence", 2))) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("message-persistence version 2")
            .hasMessageContaining("message-persistence version 1")
    }
}
```

**Step 2: Run the tests to verify that they fail**

Run: `mvn -o -B -pl chat-core test -Dtest='BackendExecutionPolicyTests,SafeRepeatContractsTests' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t2.log 2>&1; echo "exit $?"`
Expected: `exit 1` with unresolved references to `com.demo.chat.service.command`.

**Step 3: Write the contracts**

`CommandContracts.kt`:

```kotlin
package com.demo.chat.service.command

import com.demo.chat.domain.Key
import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CommandOperation
import com.demo.chat.domain.command.CommandStatus
import com.demo.chat.domain.command.CommandSubmission
import com.demo.chat.domain.command.CompletionRequirement
import com.demo.chat.domain.command.MessageSendResult
import com.demo.chat.domain.command.Receipt
import com.demo.chat.domain.knownkey.ChatDomain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

/** Admits a new request, or recovers the receipt of an earlier one. */
interface DomainCommandBus<T, V> {
    fun submit(submission: CommandSubmission<T, V>): Mono<Receipt<T>>
}

/** Reads retained status and evaluates caller completion. */
interface CommandCompletionService<T> {
    /** This method answers empty for an unknown or uncommitted command. */
    fun status(commandId: String): Mono<CommandStatus<T>>

    /** This method emits the current record, then each later change. It never completes. */
    fun observe(commandId: String): Flux<CommandStatus<T>>

    /** This method answers `PENDING` when [timeout] ends before [requirement] resolves. */
    fun await(commandId: String, requirement: CompletionRequirement, timeout: Duration): Mono<MessageSendResult<T>>
}

data class HandlerDescriptor(
    val backend: BackendId,
    val roots: Set<ChatDomain>,
    val operations: Set<CommandOperation>,
    val safeRepeat: SafeRepeatContract?,
)

/** Performs the backend work of one command. The runtime owns retries and status. */
interface DomainCommandHandler<T, V> {
    val descriptor: HandlerDescriptor
    fun handle(command: AcceptedCommand<T, V>): Mono<Void>
}

/** The authenticated user of the current request. An empty answer means no identity. */
interface SubmitterIdentity<T> {
    fun current(): Mono<Key<T>>
}
```

`SafeRepeatContracts.kt`:

```kotlin
package com.demo.chat.service.command

import com.demo.chat.domain.command.BackendId

data class SafeRepeatContract(val name: String, val version: Int)

/**
 * The safe-repeat contracts that Stage 1 supports. Startup validates the
 * declaration of each active handler. CI proves each contract with tests.
 */
object SafeRepeatContracts {
    val MESSAGE_PERSISTENCE = SafeRepeatContract("message-persistence", 1)
    val MESSAGE_INDEX = SafeRepeatContract("message-index", 1)
    val MESSAGE_VECTOR = SafeRepeatContract("message-vector", 1)
    val MESSAGE_PUBSUB = SafeRepeatContract("message-pubsub", 1)

    val SUPPORTED: Map<BackendId, SafeRepeatContract> = mapOf(
        BackendId.PERSISTENCE to MESSAGE_PERSISTENCE,
        BackendId.INDEX to MESSAGE_INDEX,
        BackendId.VECTOR to MESSAGE_VECTOR,
        BackendId.PUBSUB to MESSAGE_PUBSUB,
    )

    fun requireSupported(descriptor: HandlerDescriptor) {
        val declared = descriptor.safeRepeat
            ?: throw IllegalStateException(
                "The ${descriptor.backend} handler declares no safe-repeat contract. Stage 1 refuses to start it."
            )
        val supported = SUPPORTED[descriptor.backend]
        if (declared != supported) {
            throw IllegalStateException(
                "The ${descriptor.backend} handler declares ${declared.name} version ${declared.version}. " +
                    "Stage 1 supports ${supported?.name} version ${supported?.version}."
            )
        }
    }
}
```

`BackendExecutionPolicy.kt`:

```kotlin
package com.demo.chat.service.command

import com.demo.chat.domain.NoEffectRefusal
import com.demo.chat.domain.command.UncertainOutcomeException
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeoutException

enum class FailureClass { DEFINITIVE, TRANSIENT, UNCERTAIN }

/**
 * Retry and recovery values for one backend. Decision 12 of the spec.
 *
 * [initialAttempts] includes the first attempt. Reactor counts retries only,
 * so the runtime passes [retriesAfterFirst] to `Retry.backoff`.
 *
 * Only a [NoEffectRefusal] is definitive. An unknown error can follow an
 * effect, so it is uncertain. [attemptTimeout] marks a running attempt as
 * uncertain. It never cancels the attempt, because a cancel does not prove
 * that the backend work stopped. All values are starting values, not
 * production measurements.
 */
data class BackendExecutionPolicy(
    val version: Int = 1,
    val initialAttempts: Int = 5,
    val firstBackoff: Duration = Duration.ofMillis(100),
    val recoveryInterval: Duration = Duration.ofSeconds(30),
    val attemptTimeout: Duration = Duration.ofSeconds(30),
) {
    init {
        require(initialAttempts >= 1) { "A policy needs at least one attempt. It has $initialAttempts." }
        require(!recoveryInterval.isNegative && !recoveryInterval.isZero) { "The recovery interval must be positive." }
    }

    val retriesAfterFirst: Long get() = (initialAttempts - 1).toLong()

    /** The first matching rule wins. The rules read the whole cause chain. */
    fun classify(error: Throwable): FailureClass {
        val chain = generateSequence(error) { e -> e.cause?.takeIf { it !== e } }.take(MAX_CHAIN).toList()
        return when {
            chain.any { it is UncertainOutcomeException } -> FailureClass.UNCERTAIN
            chain.any { it is NoEffectRefusal } -> FailureClass.DEFINITIVE
            chain.any { it is TimeoutException || it is IOException || it.javaClass.name in TRANSIENT_TYPES } ->
                FailureClass.TRANSIENT
            else -> FailureClass.UNCERTAIN
        }
    }

    companion object {
        private const val MAX_CHAIN = 16

        /** Driver types that `chat-core` cannot import. The runtime matches them by name. */
        val TRANSIENT_TYPES = setOf(
            "com.datastax.oss.driver.api.core.DriverTimeoutException",
            "com.datastax.oss.driver.api.core.AllNodesFailedException",
            "com.datastax.oss.driver.api.core.NoNodeAvailableException",
            "com.datastax.oss.driver.api.core.servererrors.WriteTimeoutException",
            "io.lettuce.core.RedisConnectionException",
            "io.lettuce.core.RedisCommandTimeoutException",
            "org.springframework.dao.QueryTimeoutException",
            "org.springframework.data.redis.RedisConnectionFailureException",
        )
    }
}
```

**Step 4: Run the tests to verify that they pass**

Run: `mvn -o -B -pl chat-core test -Dtest='BackendExecutionPolicyTests,SafeRepeatContractsTests' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t2.log 2>&1; echo "exit $?"; grep "Tests run:" /tmp/t2.log | tail -1`
Expected: `exit 0` and `Tests run: 11, Failures: 0, Errors: 0`.

**Step 5: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/service/command chat-core/src/test/kotlin/com/demo/chat/test/command
git commit -m "Add the command contracts and the execution policy (<task-2-issue>)"
```

---

### Task 3: Settings and startup validation

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/config/CommandBusSettings.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/config/CommandBusValidation.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/config/CommandBusValidationTests.kt`

**Interfaces:**
- Consumes: `CompletionRequirement`, `BackendId`.
- Produces:
  - `object CommandDurations { fun parse(property: String, text: String): Duration }`
  - `data class CommandBusSettings(requirement: CompletionRequirement, timeout: Duration, recoveryInterval: Duration)` with `companion fun from(env: PropertyResolver): CommandBusSettings`
  - `object CommandBusValidation { fun appliesTo(env): Boolean; fun validate(env): CommandBusSettings; val SUPPORTED_PROVIDERS: Map<String, Set<String>> }` and the property name constants
  - `CommandBusValidationPostProcessor(environment)`. Task 17 registers it.

**Step 1: Write the failing test**

```kotlin
package com.demo.chat.test.config

import com.demo.chat.config.CommandBusValidation
import com.demo.chat.config.CommandDurations
import com.demo.chat.domain.command.BackendId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.time.Duration

class CommandBusValidationTests {

    private fun composite(vararg pairs: Pair<String, String>) = MockEnvironment().apply {
        setProperty("app.service.composite", "")
        pairs.forEach { (name, value) -> setProperty(name, value) }
    }

    @Test
    fun `a composition without the composite is not checked`() {
        assertThat(CommandBusValidation.appliesTo(MockEnvironment())).isFalse()
    }

    @Test
    fun `memory with defaults passes and gives the starting values`() {
        val settings = CommandBusValidation.validate(composite("app.command.bus" to "memory"))
        assertThat(settings.requirement.backends).containsExactlyInAnyOrder(BackendId.PERSISTENCE, BackendId.INDEX)
        assertThat(settings.timeout).isEqualTo(Duration.ofSeconds(5))
        assertThat(settings.recoveryInterval).isEqualTo(Duration.ofSeconds(30))
    }

    @Test
    fun `an unset bus fails and names the property`() {
        assertThatThrownBy { CommandBusValidation.validate(composite()) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("app.command.bus is not set")
    }

    @Test
    fun `an unknown bus fails and names the value`() {
        assertThatThrownBy { CommandBusValidation.validate(composite("app.command.bus" to "rabbit")) }
            .hasMessageContaining("app.command.bus=rabbit")
    }

    @Test
    fun `kafka fails and names Stage 2`() {
        assertThatThrownBy { CommandBusValidation.validate(composite("app.command.bus" to "kafka")) }
            .hasMessageContaining("Stage 2")
    }

    @Test
    fun `a finite ttl fails`() {
        assertThatThrownBy {
            CommandBusValidation.validate(composite("app.command.bus" to "memory", "app.command.ttl" to "10m"))
        }.hasMessageContaining("app.command.ttl=10m")
    }

    @Test
    fun `a disabled ttl passes`() {
        CommandBusValidation.validate(composite("app.command.bus" to "memory", "app.command.ttl" to "disabled"))
    }

    @Test
    fun `a declared replica count above one fails and states the limit of the check`() {
        assertThatThrownBy {
            CommandBusValidation.validate(composite("app.command.bus" to "memory", "app.command.replicas" to "2"))
        }.hasMessageContaining("app.command.replicas=2")
            .hasMessageContaining("does not detect other processes")
    }

    @Test
    fun `a requirement on V without the vector selector fails`() {
        assertThatThrownBy {
            CommandBusValidation.validate(
                composite("app.command.bus" to "memory", "app.command.completion.requirement" to "P,V")
            )
        }.hasMessageContaining("app.command.completion.requirement=P,V")
            .hasMessageContaining("app.service.core.vector")
    }

    @Test
    fun `a requirement on V with the vector selector passes`() {
        CommandBusValidation.validate(
            composite(
                "app.command.bus" to "memory",
                "app.command.completion.requirement" to "P,V",
                "app.service.core.vector" to "simple",
            )
        )
    }

    @Test
    fun `a provider without safe-repeat evidence fails and names the property`() {
        assertThatThrownBy {
            CommandBusValidation.validate(composite("app.command.bus" to "memory", "app.service.core.pubsub" to "redis-xstream"))
        }.hasMessageContaining("app.service.core.pubsub=redis-xstream")
            .hasMessageContaining("no safe-repeat evidence")
    }

    @Test
    fun `each supported provider passes`() {
        CommandBusValidation.SUPPORTED_PROVIDERS.forEach { (property, values) ->
            values.forEach { value -> CommandBusValidation.validate(composite("app.command.bus" to "memory", property to value)) }
        }
    }

    @Test
    fun `durations parse each unit`() {
        assertThat(CommandDurations.parse("x", "250ms")).isEqualTo(Duration.ofMillis(250))
        assertThat(CommandDurations.parse("x", "30s")).isEqualTo(Duration.ofSeconds(30))
        assertThat(CommandDurations.parse("x", "2m")).isEqualTo(Duration.ofMinutes(2))
        assertThat(CommandDurations.parse("x", "1h")).isEqualTo(Duration.ofHours(1))
        assertThat(CommandDurations.parse("x", "30d")).isEqualTo(Duration.ofDays(30))
    }

    @Test
    fun `a malformed or zero duration fails and names the property`() {
        assertThatThrownBy { CommandDurations.parse("app.command.recovery.interval", "soon") }
            .hasMessageContaining("app.command.recovery.interval=soon")
        assertThatThrownBy { CommandDurations.parse("app.command.recovery.interval", "0s") }
            .hasMessageContaining("must be positive")
    }
}
```

**Step 2: Run the test to verify that it fails**

Run: `mvn -o -B -pl chat-core test -Dtest=CommandBusValidationTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t3.log 2>&1; echo "exit $?"`
Expected: `exit 1` with `Unresolved reference 'CommandBusValidation'`.

**Step 3: Write the settings and the validation**

`CommandBusSettings.kt`:

```kotlin
package com.demo.chat.config

import com.demo.chat.domain.command.CompletionRequirement
import org.springframework.core.env.PropertyResolver
import java.time.Duration

/** The duration syntax of the command properties: an integer and one of ms, s, m, h, d. */
object CommandDurations {
    private val pattern = Regex("^(\\d+)(ms|s|m|h|d)$")

    fun parse(property: String, text: String): Duration {
        val match = pattern.matchEntire(text.trim())
            ?: throw IllegalStateException(
                "$property=$text is not a duration. Use an integer and one unit: ms, s, m, h, or d. Example: 30s."
            )
        val amount = match.groupValues[1].toLong()
        val duration = when (match.groupValues[2]) {
            "ms" -> Duration.ofMillis(amount)
            "s" -> Duration.ofSeconds(amount)
            "m" -> Duration.ofMinutes(amount)
            "h" -> Duration.ofHours(amount)
            else -> Duration.ofDays(amount)
        }
        if (duration.isZero) throw IllegalStateException("$property=$text must be positive.")
        return duration
    }
}

data class CommandBusSettings(
    val requirement: CompletionRequirement,
    val timeout: Duration,
    val recoveryInterval: Duration,
) {
    companion object {
        fun from(env: PropertyResolver): CommandBusSettings = CommandBusSettings(
            requirement = CompletionRequirement.parse(
                env.getProperty(CommandBusValidation.REQUIREMENT, CommandBusValidation.DEFAULT_REQUIREMENT)
            ),
            timeout = CommandDurations.parse(
                CommandBusValidation.TIMEOUT,
                env.getProperty(CommandBusValidation.TIMEOUT, CommandBusValidation.DEFAULT_TIMEOUT),
            ),
            recoveryInterval = CommandDurations.parse(
                CommandBusValidation.RECOVERY_INTERVAL,
                env.getProperty(CommandBusValidation.RECOVERY_INTERVAL, CommandBusValidation.DEFAULT_RECOVERY_INTERVAL),
            ),
        )
    }
}
```

`CommandBusValidation.kt`:

```kotlin
package com.demo.chat.config

import com.demo.chat.domain.command.BackendId
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.core.env.Environment
import org.springframework.core.env.PropertyResolver

/**
 * The Stage 1 startup check of the command bus. Decisions 3, 4, and 5 of the spec.
 *
 * The replica check reads a declaration. It does not detect other processes.
 * The handler contract check runs where the runtime is built, because it reads beans.
 */
object CommandBusValidation {
    const val COMPOSITE = "app.service.composite"
    const val BUS = "app.command.bus"
    const val TTL = "app.command.ttl"
    const val REPLICAS = "app.command.replicas"
    const val REQUIREMENT = "app.command.completion.requirement"
    const val TIMEOUT = "app.command.completion.timeout"
    const val RECOVERY_INTERVAL = "app.command.recovery.interval"
    const val VECTOR = "app.service.core.vector"

    const val DEFAULT_REQUIREMENT = "P,I"
    const val DEFAULT_TIMEOUT = "5s"
    const val DEFAULT_RECOVERY_INTERVAL = "30s"

    /** Stage 1 providers with safe-repeat evidence in CI. Task 17 holds that evidence. */
    val SUPPORTED_PROVIDERS: Map<String, Set<String>> = linkedMapOf(
        "app.service.core.key" to setOf("memory", "redis", "cassandra"),
        "app.service.core.persistence" to setOf("memory", "redis", "cassandra"),
        "app.service.core.index" to setOf("lucene", "cassandra"),
        "app.service.core.pubsub" to setOf("memory", "redis-pubsub", "kafka"),
        "app.service.core.vector" to setOf("simple", "embedded", "redis"),
    )

    fun appliesTo(env: PropertyResolver): Boolean =
        env.containsProperty(COMPOSITE) && env.getProperty(COMPOSITE) != "false"

    fun validate(env: PropertyResolver): CommandBusSettings {
        when (val bus = env.getProperty(BUS)?.trim()) {
            null, "" -> throw IllegalStateException("$BUS is not set. Stage 1 accepts $BUS=memory.")
            "memory" -> Unit
            "kafka" -> throw IllegalStateException("$BUS=kafka is Stage 2 work. Stage 1 accepts $BUS=memory.")
            else -> throw IllegalStateException("$BUS=$bus is unknown. Stage 1 accepts $BUS=memory.")
        }

        val ttl = env.getProperty(TTL)?.trim()
        if (!ttl.isNullOrEmpty() && ttl != "disabled") {
            throw IllegalStateException("Stage 1 rejects a finite $TTL=$ttl. Remove it or set it to disabled.")
        }

        val replicas = env.getProperty(REPLICAS)?.trim()
        if (!replicas.isNullOrEmpty() && replicas != "1") {
            throw IllegalStateException(
                "$REPLICAS=$replicas is unsupported with $BUS=memory. Stage 1 runs one process. " +
                    "This check reads the declaration and does not detect other processes."
            )
        }

        SUPPORTED_PROVIDERS.forEach { (property, supported) ->
            val value = env.getProperty(property)?.trim()
            if (!value.isNullOrEmpty() && value !in supported) {
                throw IllegalStateException(
                    "Stage 1 has no safe-repeat evidence for $property=$value. Supported values: ${supported.joinToString()}."
                )
            }
        }

        val requirementText = env.getProperty(REQUIREMENT, DEFAULT_REQUIREMENT)
        val settings = try {
            CommandBusSettings.from(env)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("$REQUIREMENT=$requirementText is not valid. ${e.message}", e)
        }
        if (BackendId.VECTOR in settings.requirement.backends && env.getProperty(VECTOR).isNullOrBlank()) {
            throw IllegalStateException(
                "$REQUIREMENT=$requirementText names V, and $VECTOR is not set. No vector handler is active."
            )
        }
        return settings
    }
}

open class CommandBusValidationPostProcessor(private val environment: Environment) : BeanFactoryPostProcessor {
    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        if (CommandBusValidation.appliesTo(environment)) CommandBusValidation.validate(environment)
    }
}

```

**Step 4: Run the test to verify that it passes**

Run: `mvn -o -B -pl chat-core test -Dtest=CommandBusValidationTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t3.log 2>&1; echo "exit $?"; grep "Tests run:" /tmp/t3.log | tail -1`
Expected: `exit 0` and `Tests run: 14, Failures: 0, Errors: 0`.

**Step 5: Run a mutation check**

Change `"kafka" -> throw` to `"kafka" -> Unit` in `CommandBusValidation.kt`. Run the Step 4 command. Expected: `kafka fails and names Stage 2` fails. Restore the file by absolute path, then run `git status --short chat-core` and confirm that only the intended new files are listed.

**Step 6: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/config/CommandBus*.kt chat-core/src/test/kotlin/com/demo/chat/test/config/CommandBusValidationTests.kt
git commit -m "Add the Stage 1 command bus settings and startup validation (<task-3-issue>)"
```

Nothing registers the check yet. Task 17 registers it, after every composition sets `app.command.bus`. So each task leaves a green tree.

---

### Task 4: The fingerprint and request ID rules

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/command/CommandFingerprint.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/command/RequestIds.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/command/CommandFingerprintTests.kt`

**Interfaces:**
- Consumes: `CommandOperation`, `InvalidRequestIdException`.
- Produces: `object CommandFingerprint { const val VERSION = 1; fun of(operation: CommandOperation, senderId: String, destinationId: String, content: Any?): String }` and `object RequestIds { fun requireValid(requestId: String?): String }`.

**Step 1: Write the failing test**

The golden values were computed with an independent Perl encoder on 2026-10-07.

```kotlin
package com.demo.chat.test.command

import com.demo.chat.domain.command.CommandOperation
import com.demo.chat.domain.command.InvalidRequestIdException
import com.demo.chat.service.command.CommandFingerprint
import com.demo.chat.service.command.RequestIds
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CommandFingerprintTests {
    private val op = CommandOperation.RECORD_MESSAGE

    @Test
    fun `a Long sender and destination give the golden value`() {
        assertThat(CommandFingerprint.of(op, "10", "100", "hello"))
            .isEqualTo("3d0ac229ec15f3e7d52cea0f5b49a615aae9ef406a503cea25c132933efec7ad")
    }

    @Test
    fun `UUID ids give the golden value`() {
        assertThat(
            CommandFingerprint.of(op, "123e4567-e89b-12d3-a456-426614174000", "123e4567-e89b-12d3-a456-426614174001", "hello")
        ).isEqualTo("8c43a7a92b7ff67570bed080836504c4cda6c9e5d2378b45d8ec527e4cb6f390")
    }

    @Test
    fun `content encodes as UTF-8`() {
        assertThat(CommandFingerprint.of(op, "10", "100", "héllo"))
            .isEqualTo("5b4d05ac2ae5ac4bd7cecffb20c5b680aab709a629c31d1cf1c5634238ce115e")
    }

    @Test
    fun `swapping sender and destination changes the value`() {
        assertThat(CommandFingerprint.of(op, "100", "10", "hello"))
            .isEqualTo("8e4209aaaf2e65ef16c2a73d3e7d58217dd2f028c5da1e8cea99f2da48c185ad")
    }

    @Test
    fun `a change in each field changes the value`() {
        val base = CommandFingerprint.of(op, "10", "100", "hello")
        assertThat(CommandFingerprint.of(op, "11", "100", "hello")).isNotEqualTo(base)
        assertThat(CommandFingerprint.of(op, "10", "101", "hello")).isNotEqualTo(base)
        assertThat(CommandFingerprint.of(op, "10", "100", "hello!")).isNotEqualTo(base)
    }

    @Test
    fun `a non-text value is refused`() {
        assertThatThrownBy { CommandFingerprint.of(op, "10", "100", 42) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("String")
    }

    @Test
    fun `a request ID of visible ASCII up to 128 characters passes`() {
        assertThat(RequestIds.requireValid("a1-B2_c3.~!")).isEqualTo("a1-B2_c3.~!")
        assertThat(RequestIds.requireValid("x".repeat(128))).hasSize(128)
    }

    @Test
    fun `an empty, long, spaced, or non-ASCII request ID is refused`() {
        listOf(null, "", "x".repeat(129), "has space", "café").forEach { bad ->
            assertThatThrownBy { RequestIds.requireValid(bad) }.isInstanceOf(InvalidRequestIdException::class.java)
        }
    }
}
```

**Step 2: Run the test to verify that it fails**

Run: `mvn -o -B -pl chat-core test -Dtest=CommandFingerprintTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t4.log 2>&1; echo "exit $?"`
Expected: `exit 1` with `Unresolved reference 'CommandFingerprint'`.

**Step 3: Write the implementation**

`CommandFingerprint.kt`:

```kotlin
package com.demo.chat.service.command

import com.demo.chat.domain.command.CommandOperation
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.HexFormat

/**
 * The version 1 fingerprint of decision 6. It detects changed intent under one
 * request identity. It never identifies a duplicate by content alone.
 *
 * The encoding writes the version as a 4-byte big-endian integer. Each field
 * then writes a 4-byte big-endian length and its UTF-8 bytes. SHA-256 hashes
 * the result. The request ID, the owner, and timestamps stay out.
 */
object CommandFingerprint {
    const val VERSION = 1

    fun of(operation: CommandOperation, senderId: String, destinationId: String, content: Any?): String {
        val text = content as? String
            ?: throw IllegalArgumentException(
                "Stage 1 fingerprints a String message only. The value type is ${content?.javaClass?.name}."
            )
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(VERSION)
            listOf(operation.name, senderId, destinationId, text).forEach { field ->
                val encoded = field.toByteArray(Charsets.UTF_8)
                out.writeInt(encoded.size)
                out.write(encoded)
            }
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()))
    }
}
```

`RequestIds.kt`:

```kotlin
package com.demo.chat.service.command

import com.demo.chat.domain.command.InvalidRequestIdException

/** A request ID has 1 to 128 visible ASCII characters, from '!' to '~'. */
object RequestIds {
    const val MAX_LENGTH = 128

    fun requireValid(requestId: String?): String {
        if (requestId.isNullOrEmpty()) throw InvalidRequestIdException("It is empty.")
        if (requestId.length > MAX_LENGTH) {
            throw InvalidRequestIdException("It has ${requestId.length} characters. The limit is $MAX_LENGTH.")
        }
        if (requestId.any { it < '!' || it > '~' }) {
            throw InvalidRequestIdException("It holds a character outside visible ASCII.")
        }
        return requestId
    }
}
```

**Step 4: Run the test to verify that it passes**

Run: `mvn -o -B -pl chat-core test -Dtest=CommandFingerprintTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t4.log 2>&1; echo "exit $?"; grep "Tests run:" /tmp/t4.log | tail -1`
Expected: `exit 0` and `Tests run: 8, Failures: 0, Errors: 0`.

**Step 5: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/service/command/CommandFingerprint.kt chat-core/src/main/kotlin/com/demo/chat/service/command/RequestIds.kt chat-core/src/test/kotlin/com/demo/chat/test/command/CommandFingerprintTests.kt
git commit -m "Add the version 1 command fingerprint and request ID rules (<task-4-issue>)"
```

---
### Task 5: Key allocation and memory registration

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/core/KeyAllocator.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/KeyRegistrationExceptions.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/service/core/KeyService.kt` (interface `IKeyService`)
- Modify: `chat-core/src/test/kotlin/com/demo/chat/test/TestStringKeyService.kt` (class `TestGeneratorKeyService`)
- Modify: `chat-persistence-memory/src/main/kotlin/com/demo/chat/persistence/memory/impl/KeyServiceInMemory.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/key/KeyAllocatorTests.kt`
- Test: `chat-persistence-memory/src/test/kotlin/com/demo/chat/test/persistence/memory/KeyServiceInMemoryRegistrationTests.kt`

**Interfaces:**
- Consumes: `IKeyGenerator<T>`, `RootKeys<T>`, `ChatDomain`.
- Produces:
  - `class KeyAllocator<T>(ids: IKeyGenerator<T>, rootKeys: RootKeys<T>) { fun allocate(domain: ChatDomain): Key<T> }`
  - `IKeyService.register(key: Key<T>): Mono<Void>`. The default refuses with `UnsupportedOperationException`.
  - `class KeyRootConflictException(id, storedRoot, requestedRoot)` and `class RootKeyRegistrationException(id)`. Both implement `NoEffectRefusal`, so the policy classifies them as definitive.

**Step 1: Write the failing tests**

`KeyAllocatorTests.kt`:

```kotlin
package com.demo.chat.test.key

import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.core.KeyAllocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KeyAllocatorTests {
    private val roots = FakeKeyServices.longRoots()
    private val registry = FakeKeyServices.long(roots)

    @Test
    fun `an allocated key carries the root of its domain`() {
        val key = KeyAllocator(LongKeyGenerator(1), roots).allocate(ChatDomain.MESSAGE)
        assertThat(key.root).isEqualTo(roots.of(ChatDomain.MESSAGE).id)
    }

    @Test
    fun `allocation writes nothing to the key registry`() {
        val key = KeyAllocator(LongKeyGenerator(1), roots).allocate(ChatDomain.MESSAGE)
        assertThat(registry.rootOf(key.id).block()).isNull()
    }

    @Test
    fun `two allocations give two ids`() {
        val allocator = KeyAllocator(LongKeyGenerator(1), roots)
        assertThat(allocator.allocate(ChatDomain.MESSAGE).id).isNotEqualTo(allocator.allocate(ChatDomain.MESSAGE).id)
    }
}
```

`KeyServiceInMemoryRegistrationTests.kt`:

```kotlin
package com.demo.chat.test.persistence.memory

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyRootConflictException
import com.demo.chat.domain.RootKeyRegistrationException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.persistence.memory.impl.KeyServiceInMemory
import com.demo.chat.test.key.FakeKeyServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.util.concurrent.atomic.AtomicLong

class KeyServiceInMemoryRegistrationTests {
    private val roots = FakeKeyServices.longRoots()
    private val ids = AtomicLong(1000)
    private val keys = KeyServiceInMemory({ ids.incrementAndGet() }, roots)
    private val messageRoot = roots.of(ChatDomain.MESSAGE).id
    private val userRoot = roots.of(ChatDomain.USER).id

    @Test
    fun `a new key registers under its root`() {
        StepVerifier.create(keys.register(Key.of(42L, messageRoot))).verifyComplete()
        assertThat(keys.rootOf(42L).block()).isEqualTo(messageRoot)
    }

    @Test
    fun `a repeated registration succeeds and keeps one root`() {
        keys.register(Key.of(43L, messageRoot)).block()
        StepVerifier.create(keys.register(Key.of(43L, messageRoot))).verifyComplete()
        assertThat(keys.rootOf(43L).block()).isEqualTo(messageRoot)
    }

    @Test
    fun `a different root fails with a conflict and keeps the stored root`() {
        keys.register(Key.of(44L, messageRoot)).block()
        StepVerifier.create(keys.register(Key.of(44L, userRoot)))
            .expectError(KeyRootConflictException::class.java)
            .verify()
        assertThat(keys.rootOf(44L).block()).isEqualTo(messageRoot)
    }

    @Test
    fun `a root key id is refused`() {
        StepVerifier.create(keys.register(Key.of(messageRoot, messageRoot)))
            .expectError(RootKeyRegistrationException::class.java)
            .verify()
    }
}
```

**Step 2: Run the tests to verify that they fail**

Run: `mvn -o -B -pl chat-core,chat-persistence-memory test -Dtest='KeyAllocatorTests,KeyServiceInMemoryRegistrationTests' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t5.log 2>&1; echo "exit $?"`
Expected: `exit 1` with `Unresolved reference 'KeyAllocator'`.

**Step 3: Write the implementation**

`KeyRegistrationExceptions.kt`:

```kotlin
package com.demo.chat.domain

/** Both refusals happen before any registry write, so both are definitive. */
class KeyRootConflictException(val id: Any?, val storedRoot: Any?, val requestedRoot: Any?) :
    ChatException("The key $id is registered under root $storedRoot. A registration under root $requestedRoot is refused."),
    NoEffectRefusal

class RootKeyRegistrationException(id: Any?) :
    ChatException("The key $id is a root key. A key service does not register a root key."),
    NoEffectRefusal
```

`KeyAllocator.kt`:

```kotlin
package com.demo.chat.service.core

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys

/**
 * Assigns a key with no I/O. Decision 1 of the spec. Admission uses it, and
 * the `P` handler registers the key later through [IKeyService.register].
 * The generator carries the node id, so the key-type rules still hold.
 */
class KeyAllocator<T>(private val ids: IKeyGenerator<T>, private val rootKeys: RootKeys<T>) {
    fun allocate(domain: ChatDomain): Key<T> = Key.of(ids.nextId(), rootKeys.of(domain).id)
}
```

In `KeyService.kt`, add this member to `IKeyService<T>`, after `rootOf`:

```kotlin
    /**
     * This method registers [key] with no new id. Decision 2 of the spec.
     * A repeat with the same root succeeds and writes nothing. A different
     * root fails with `KeyRootConflictException`. A root key id fails with
     * `RootKeyRegistrationException`. A provider that cannot register keeps
     * this default, which refuses.
     */
    fun register(key: Key<T>): Mono<Void> =
        Mono.error(UnsupportedOperationException("${this::class.simpleName} does not register keys."))
```

In `KeyServiceInMemory.kt`, add:

```kotlin
    override fun register(key: Key<T>): Mono<Void> = Mono.defer {
        if (rootKeys.domainOfRoot(key.id) != null) return@defer Mono.error<Void>(RootKeyRegistrationException(key.id))
        val stored = roots.putIfAbsent(key.id!!, key.root!!)
        if (stored == null || stored == key.root) Mono.empty()
        else Mono.error(KeyRootConflictException(key.id, stored, key.root))
    }
```

Add the imports `com.demo.chat.domain.KeyRootConflictException` and `com.demo.chat.domain.RootKeyRegistrationException`.

In `TestGeneratorKeyService` (file `chat-core/src/test/kotlin/com/demo/chat/test/TestStringKeyService.kt`), add the same rule, so later handler tests can register keys:

```kotlin
    override fun register(key: Key<T>): Mono<Void> = Mono.defer {
        if (isRoot(key.id!!)) return@defer Mono.error<Void>(RootKeyRegistrationException(key.id))
        val stored = registry.putIfAbsent(key.id!!, key.root!!)
        if (stored == null || stored == key.root) Mono.empty()
        else Mono.error(KeyRootConflictException(key.id, stored, key.root))
    }
```

**Step 4: Run the tests to verify that they pass**

Run: `mvn -o -B -pl chat-core,chat-persistence-memory test -Dtest='KeyAllocatorTests,KeyServiceInMemoryRegistrationTests' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t5.log 2>&1; echo "exit $?"; grep "Tests run:" /tmp/t5.log | tail -2`
Expected: `exit 0`. `chat-core` reports 3 tests, and `chat-persistence-memory` reports 4, all with 0 failures.

**Step 5: Run a mutation check**

Replace `roots.putIfAbsent(key.id!!, key.root!!)` with `roots.put(key.id!!, key.root!!)` in `KeyServiceInMemory.kt`. Run Step 4. Expected: `a different root fails with a conflict and keeps the stored root` fails. Restore the file by absolute path and confirm with `git diff --stat`.

**Step 6: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/service/core/KeyAllocator.kt chat-core/src/main/kotlin/com/demo/chat/domain/KeyRegistrationExceptions.kt chat-core/src/main/kotlin/com/demo/chat/service/core/KeyService.kt chat-core/src/test chat-persistence-memory/src
git commit -m "Add key allocation and idempotent memory key registration (<task-5-issue>)"
```

---

### Task 6: Redis key registration

**Files:**
- Modify: `chat-persistence-redis/src/main/kotlin/com/demo/chat/persistence/redis/impl/KeyServiceRedis.kt`
- Modify: `chat-persistence-redis/src/test/kotlin/com/demo/chat/test/persistence/redis/RedisKeyServiceTests.kt`

**Interfaces:**
- Consumes: Task 5 exceptions and the `register` contract.
- Produces: `KeyServiceRedis.register(key)`.

**Step 1: Read the existing test class**

Run: `sed -n 1,80p chat-persistence-redis/src/test/kotlin/com/demo/chat/test/persistence/redis/RedisKeyServiceTests.kt`
Note the field that holds the service under test and the field that holds the roots. The steps below call them `keyService` and `rootKeys`. Use the real names if they differ.

**Step 2: Write the failing tests**

Add to `RedisKeyServiceTests`:

```kotlin
    @Test
    fun `a new key registers under its root`() {
        val root = rootKeys.of(ChatDomain.MESSAGE).id
        StepVerifier.create(keyService.register(Key.of(77001L, root))).verifyComplete()
        assertThat(keyService.rootOf(77001L).block()).isEqualTo(root)
    }

    @Test
    fun `a repeated registration succeeds and keeps one root`() {
        val root = rootKeys.of(ChatDomain.MESSAGE).id
        keyService.register(Key.of(77002L, root)).block()
        StepVerifier.create(keyService.register(Key.of(77002L, root))).verifyComplete()
        assertThat(keyService.rootOf(77002L).block()).isEqualTo(root)
    }

    @Test
    fun `a different root fails with a conflict and keeps the stored root`() {
        val root = rootKeys.of(ChatDomain.MESSAGE).id
        keyService.register(Key.of(77003L, root)).block()
        StepVerifier.create(keyService.register(Key.of(77003L, rootKeys.of(ChatDomain.USER).id)))
            .expectError(KeyRootConflictException::class.java)
            .verify()
        assertThat(keyService.rootOf(77003L).block()).isEqualTo(root)
    }

    @Test
    fun `a root key id is refused`() {
        val root = rootKeys.of(ChatDomain.MESSAGE).id
        StepVerifier.create(keyService.register(Key.of(root, root)))
            .expectError(RootKeyRegistrationException::class.java)
            .verify()
    }
```

The test class can be generic over `T`. Then convert the literal ids as its existing tests do.

**Step 3: Run the tests to verify that they fail**

Run: `mvn -B -pl chat-core,chat-persistence-redis -Pintegration verify -Dtest=RedisKeyServiceTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t6.log 2>&1; echo "exit $?"; grep -E "Tests run:|UnsupportedOperation" /tmp/t6.log | tail -3`
Expected: `exit 1`. The new tests fail with `KeyServiceRedis does not register keys`.

**Step 4: Write the implementation**

Add to `KeyServiceRedis`:

```kotlin
    override fun register(key: Key<T>): Mono<Void> {
        if (rootKeys.domainOfRoot(key.id) != null) return Mono.error(RootKeyRegistrationException(key.id))
        val field = typeUtil.toString(key.id)
        val root = typeUtil.toString(key.root)
        val hash = stringTemplate.opsForHash<String, String>()
        return hash.putIfAbsent(keyRegistryHash, field, root)
            .flatMap { written ->
                if (written) Mono.empty<Void>()
                else hash.get(keyRegistryHash, field).flatMap { stored ->
                    if (stored == root) Mono.empty<Void>()
                    else Mono.error(KeyRootConflictException(key.id, stored, key.root))
                }
            }
    }
```

`putIfAbsent` sends `HSETNX`, so one write decides.

**Step 5: Run the tests to verify that they pass**

Run the Step 3 command. Expected: `exit 0`, and `RedisKeyServiceTests` reports 0 failures.

**Step 6: Commit**

```bash
git add chat-persistence-redis/src
git commit -m "Register Redis keys with HSETNX and refuse a root conflict (<task-6-issue>)"
```

---

### Task 7: Cassandra key registration

**Files:**
- Modify: `chat-persistence-cassandra/src/main/kotlin/com/demo/chat/persistence/cassandra/impl/KeyServiceCassandra.kt`
- Modify: `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/KeyServiceTests.kt`

**Interfaces:**
- Consumes: Task 5 exceptions and the `register` contract.
- Produces: `KeyServiceCassandra.register(key)`.

**Step 1: Read the existing test class**

Run: `sed -n 1,80p chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/KeyServiceTests.kt`
Note the field names for the key service and the roots, and the id type.

**Step 2: Write the failing tests**

Add the four tests of Task 6 Step 2 to `KeyServiceTests`, with these id literals: `88001`, `88002`, `88003`. For a `UUID` class, use `UUIDs.timeBased()` from the Cassandra driver, because the `keys.id` column of the `uuid` keyspace is a `TIMEUUID`.

**Step 3: Run the tests to verify that they fail**

Run: `mvn -B -pl chat-core,shared-resources-cassandra,chat-persistence-cassandra -Pintegration verify -Dtest=KeyServiceTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t7.log 2>&1; echo "exit $?"; grep -E "Tests run:|UnsupportedOperation" /tmp/t7.log | tail -3`
Expected: `exit 1`, with `KeyServiceCassandra does not register keys`.

**Step 4: Write the implementation**

Add to `KeyServiceCassandra`. It follows the `RootKeyStoreCassandra.createIfAbsent` pattern: a lightweight transaction, then a read of the stored row:

```kotlin
    /**
     * `IF NOT EXISTS` writes the row only when the id is absent. The read
     * after it returns the stored root in both cases. Decision 2 of the spec.
     */
    @Suppress("UNCHECKED_CAST")
    override fun register(key: Key<T>): Mono<Void> =
        if (rootKeys.domainOfRoot(key.id) != null) Mono.error(RootKeyRegistrationException(key.id))
        else template.reactiveCqlOperations
            .execute("INSERT INTO keys (id, root) VALUES (?, ?) IF NOT EXISTS", key.id, key.root)
            .then(
                template.reactiveCqlOperations
                    .query("SELECT root FROM keys WHERE id = ?", { row, _ -> row.getObject("root") as T }, key.id)
                    .next()
            )
            .flatMap { stored ->
                if (stored == key.root) Mono.empty<Void>()
                else Mono.error(KeyRootConflictException(key.id, stored, key.root))
            }
```

**Step 5: Run the tests to verify that they pass**

Run the Step 3 command. Expected: `exit 0`, and `KeyServiceTests` reports 0 failures. If Cassandra fails with `NoNodeAvailableException`, run `docker events --since 10m --filter event=oom --until 0s` before you treat it as a code failure.

**Step 6: Commit**

```bash
git add chat-persistence-cassandra/src
git commit -m "Register Cassandra keys with IF NOT EXISTS and refuse a root conflict (<task-7-issue>)"
```

---

### Task 8: Protect registration on the RSocket transport

**Files:**
- Modify: `chat-service-controller/src/main/kotlin/com/demo/chat/controller/core/mapping/KeyServiceMapping.kt`
- Modify: `chat-service-controller/src/main/kotlin/com/demo/chat/config/rsocket/RSocketSecurityConfiguration.kt:34-35`
- Modify: `chat-client-rsocket/src/main/kotlin/com/demo/chat/client/rsocket/clients/core/KeyClient.kt`
- Test: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/CoreRouteAccessTests.kt`

**Interfaces:**
- Consumes: `IKeyService.register`.
- Produces: the RSocket route `key.register`, `KeyClient.register`, and `KeyClientProxy.register`.

REST does not expose registration. `IKeyRestMapping` gains no route, and `CoreRestControllers.requireAbsent` refuses the core key controller on a REST launch. Do not add a REST route.

**Step 1: Write the failing transport test**

Add to `CoreRouteAccessTests`, beside `the key boundary refuses a removal and the key stays`:

```kotlin
    /** **Key registration.** A refused registration writes no registry row. Decision 2 of the spec. */
    @Test
    fun `the key boundary refuses a registration and registers nothing`() {
        val messageRoot = rootKeys.of(ChatDomain.MESSAGE).id
        val unregistered = Key.of(990_001L, messageRoot)

        refusedCallers().forEach {
            assertRefused(it.route("key.register").data(unregistered).retrieveMono(Void::class.java))
        }
        assertThat(keys.keyService().rootOf(unregistered.id).block(timeout))
            .describedAs("the registry after two refused registrations")
            .isNull()

        service().route("key.register").data(unregistered).retrieveMono(Void::class.java).block(timeout)
        assertThat(keys.keyService().rootOf(unregistered.id).block(timeout))
            .describedAs("the registry after the service registration")
            .isEqualTo(messageRoot)
    }
```

If the class has no `rootKeys` field, add `@Autowired lateinit var rootKeys: RootKeys<Long>`.

**Step 2: Run the test to verify that it fails**

Run: `mvn -o -B clean install -DskipTests > /tmp/t8-install.log 2>&1; echo "install exit $?"; mvn -o -B -pl chat-deploy-memory test -Dtest=CoreRouteAccessTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t8.log 2>&1; echo "exit $?"; grep -E "Tests run:|FAIL" /tmp/t8.log | tail -5`
Expected: `install exit 0`, then `exit 1`. The service call fails, because no `key.register` route exists.

**Step 3: Add the route, the rule, and the client**

In `IKeyServiceMapping`, add:

```kotlin
    /** Registration of an assigned key. The core route rule requires `ROLE_SERVICE` or `ROLE_ADMIN`. */
    @MessageMapping("register")
    override fun register(key: Key<T>): Mono<Void>
```

`KeyServiceController` delegates `IKeyService<T> by that`, so it needs no change.

In `RSocketSecurityConfiguration`, after `.route("key.rem").hasAnyRole(SERVICE, ADMIN)`, add:

```kotlin
                .route("key.register").hasAnyRole(SERVICE, ADMIN)
```

The rule ends with `anyExchange().permitAll()`, so this line is the only protection of the new route.

In `KeyClient`, add:

```kotlin
    override fun register(key: Key<T>): Mono<Void> = requester
            .route("${prefix}register")
            .data(key)
            .retrieveMono(Void::class.java)
```

`retrieveMono` returns the refusal to the caller. A `send()` would lose it.

In `KeyClientProxy`, add:

```kotlin
    @RSocketExchange("key.register")
    override fun register(@Payload key: Key<T>): Mono<Void>
```

**Step 4: Run the test to verify that it passes**

Run the Step 2 command. Expected: `exit 0`, and `CoreRouteAccessTests` reports 0 failures.

**Step 5: Run a mutation check**

Remove the `.route("key.register")` line. Run Step 2. Expected: the new test fails, because a plain user registers the key. Restore the file by absolute path and confirm with `git diff --stat`.

**Step 6: Commit**

```bash
git add chat-service-controller/src chat-client-rsocket/src chat-deploy-memory/src/test
git commit -m "Expose key registration on RSocket under the service role rule (<task-8-issue>)"
```

---
### Task 9: The status store and caller completion

**Files:**
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/CommitMarker.kt`
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/MemoryCommandStatusStore.kt`
- Create: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/CommandFixtures.kt`
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/MemoryCommandStatusStoreTests.kt`

**Interfaces:**
- Consumes: Task 1 types and `CommandCompletionService<T>`.
- Produces:
  - `class CommitMarker` with `state`, `committed`, `commit()`, `rollBack()`, and `enum State { UNCOMMITTED, COMMITTED, ROLLED_BACK }`
  - `class MemoryCommandStatusStore<T : Any> : CommandCompletionService<T>` with `stage(command, receipt, marker)`, `remove(commandId)`, and `update(commandId, backend, change: (BackendStatus) -> BackendStatus)`
  - `fun <T> evaluate(status: CommandStatus<T>, requirement: CompletionRequirement): MessageSendResult<T>` on the store companion
  - Test fixtures: `CommandFixtures.command(...)`, `CommandFixtures.receipt(command)`, `CommandFixtures.descriptor(backend)`, `ScriptedHandler`, and `waitUntil(timeout, condition)`

**Step 1: Write the fixtures**

`CommandFixtures.kt`:

```kotlin
package com.demo.chat.test.service.composite.command

import com.demo.chat.domain.Message
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CommandOperation
import com.demo.chat.domain.command.Receipt
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.command.HandlerDescriptor
import com.demo.chat.service.command.SafeRepeatContracts
import com.demo.chat.test.key.FakeKeyServices
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

object CommandFixtures {
    val ROOTS = FakeKeyServices.longRoots()
    val PIU = setOf(BackendId.PERSISTENCE, BackendId.INDEX, BackendId.PUBSUB)
    val SENT_AT: Instant = Instant.parse("2026-10-07T00:00:00Z")

    fun command(
        commandId: String = "c-1",
        room: Long = 100L,
        obligations: Set<BackendId> = PIU,
        text: String = "hello",
        messageId: Long = 1L,
    ): AcceptedCommand<Long, String> {
        val key = SimpleMessageKey(messageId, ROOTS.of(ChatDomain.MESSAGE).id, 10L, room, SENT_AT)
        return AcceptedCommand(
            commandId, 10L, "req-$commandId", key.root, CommandOperation.RECORD_MESSAGE, room, 1,
            Message.create(key, text, true), obligations, 1,
        )
    }

    fun receipt(command: AcceptedCommand<Long, String>) = Receipt(command.commandId, command.message.key)

    fun descriptor(backend: BackendId) = HandlerDescriptor(
        backend, setOf(ChatDomain.MESSAGE), setOf(CommandOperation.RECORD_MESSAGE), SafeRepeatContracts.SUPPORTED.getValue(backend),
    )

    fun waitUntil(timeout: Duration = Duration.ofSeconds(5), condition: () -> Boolean) {
        val end = System.nanoTime() + timeout.toNanos()
        while (!condition()) {
            check(System.nanoTime() < end) { "The condition did not hold within $timeout." }
            Thread.sleep(10)
        }
    }
}

/** A handler that records each call. [script] receives the command and the attempt number, from 1. */
class ScriptedHandler(
    backend: BackendId,
    private val script: (AcceptedCommand<Long, String>, Int) -> Mono<Void> = { _, _ -> Mono.empty() },
) : DomainCommandHandler<Long, String> {
    override val descriptor = CommandFixtures.descriptor(backend)
    val calls = CopyOnWriteArrayList<AcceptedCommand<Long, String>>()

    override fun handle(command: AcceptedCommand<Long, String>): Mono<Void> = Mono.defer {
        calls.add(command)
        script(command, calls.count { it.commandId == command.commandId })
    }

    fun attemptsOf(commandId: String): Int = calls.count { it.commandId == commandId }
}
```

**Step 2: Write the failing test**

`MemoryCommandStatusStoreTests.kt`:

```kotlin
package com.demo.chat.test.service.composite.command

import com.demo.chat.domain.command.BackendId.INDEX
import com.demo.chat.domain.command.BackendId.PERSISTENCE
import com.demo.chat.domain.command.BackendId.PUBSUB
import com.demo.chat.domain.command.BackendState
import com.demo.chat.domain.command.CallerOutcome
import com.demo.chat.domain.command.CommandStatus
import com.demo.chat.domain.command.CompletionRequirement
import com.demo.chat.service.composite.command.memory.CommitMarker
import com.demo.chat.service.composite.command.memory.MemoryCommandStatusStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MemoryCommandStatusStoreTests {
    private val store = MemoryCommandStatusStore<Long>()
    private val command = CommandFixtures.command()
    private val id = command.commandId
    private val pi = CompletionRequirement(setOf(PERSISTENCE, INDEX))
    private val piu = CompletionRequirement(setOf(PERSISTENCE, INDEX, PUBSUB))
    private val wait = Duration.ofSeconds(2)

    private fun committed(): CommitMarker = CommitMarker().also {
        store.stage(command, CommandFixtures.receipt(command), it)
        it.commit()
    }

    private fun succeed(backend: com.demo.chat.domain.command.BackendId) =
        store.update(id, backend) { it.copy(state = BackendState.SUCCEEDED) }

    @Test
    fun `an uncommitted command is hidden`() {
        store.stage(command, CommandFixtures.receipt(command), CommitMarker())
        assertThat(store.status(id).block()).isNull()
    }

    @Test
    fun `case 1 - P completes while the other backends remain pending`() {
        committed()
        succeed(PERSISTENCE)
        val result = store.await(id, CompletionRequirement(setOf(PERSISTENCE)), wait).block()!!
        assertThat(result.outcome).isEqualTo(CallerOutcome.COMPLETED)
        assertThat(result.backends[INDEX]!!.state).isEqualTo(BackendState.PENDING)
    }

    @Test
    fun `case 2 - P and I complete in either arrival order`() {
        committed()
        val waiting = store.await(id, pi, wait).toFuture()
        succeed(INDEX)
        assertThat(waiting.isDone).isFalse()
        succeed(PERSISTENCE)
        assertThat(waiting.get(2, TimeUnit.SECONDS).outcome).isEqualTo(CallerOutcome.COMPLETED)
    }

    @Test
    fun `case 4 - a completion before the wait remains observable`() {
        committed()
        succeed(PERSISTENCE)
        succeed(INDEX)
        assertThat(store.await(id, pi, wait).block()!!.outcome).isEqualTo(CallerOutcome.COMPLETED)
    }

    @Test
    fun `case 8 - a duplicate success counts once and a stale failure cannot reverse it`() {
        committed()
        succeed(PERSISTENCE)
        val version = store.status(id).block()!!.version
        succeed(PERSISTENCE)
        store.update(id, PERSISTENCE) { it.copy(state = BackendState.FAILED, reason = "stale") }
        val status = store.status(id).block()!!
        assertThat(status.version).isEqualTo(version)
        assertThat(status.backends[PERSISTENCE]!!.state).isEqualTo(BackendState.SUCCEEDED)
    }

    @Test
    fun `case 9 - an uncertain backend stays pending and a requirement without it completes`() {
        committed()
        store.update(id, PUBSUB) { it.copy(state = BackendState.UNCERTAIN, reason = "bookkeeping") }
        succeed(PERSISTENCE)
        succeed(INDEX)
        assertThat(store.await(id, pi, wait).block()!!.outcome).isEqualTo(CallerOutcome.COMPLETED)
        val waitingOnU = store.await(id, piu, Duration.ofMillis(200)).block()!!
        assertThat(waitingOnU.outcome).isEqualTo(CallerOutcome.PENDING)
        assertThat(waitingOnU.backends[PUBSUB]!!.state).isEqualTo(BackendState.UNCERTAIN)
    }

    @Test
    fun `a failed required backend gives Incomplete and a failed other backend does not`() {
        committed()
        store.update(id, PUBSUB) { it.copy(state = BackendState.FAILED, reason = "room closed") }
        succeed(PERSISTENCE)
        succeed(INDEX)
        assertThat(store.await(id, pi, wait).block()!!.outcome).isEqualTo(CallerOutcome.COMPLETED)
        assertThat(store.await(id, piu, wait).block()!!.outcome).isEqualTo(CallerOutcome.INCOMPLETE)
    }

    @Test
    fun `case 10 - a timeout answers Pending with the receipt and execution continues`() {
        committed()
        val result = store.await(id, pi, Duration.ofMillis(100)).block()!!
        assertThat(result.outcome).isEqualTo(CallerOutcome.PENDING)
        assertThat(result.receipt.commandId).isEqualTo(id)
        succeed(PERSISTENCE)
        succeed(INDEX)
        assertThat(store.await(id, pi, wait).block()!!.outcome).isEqualTo(CallerOutcome.COMPLETED)
    }

    @Test
    fun `an empty requirement answers Accepted at once`() {
        committed()
        assertThat(store.await(id, CompletionRequirement.NONE, wait).block()!!.outcome).isEqualTo(CallerOutcome.ACCEPTED)
    }

    @Test
    fun `review 6 - a timeout keeps a terminal result`() {
        val stalled = object : MemoryCommandStatusStore<Long>() {
            override fun observe(commandId: String): Flux<CommandStatus<Long>> = Flux.never()
        }
        val marker = CommitMarker()
        stalled.stage(command, CommandFixtures.receipt(command), marker)
        marker.commit()
        stalled.update(id, PERSISTENCE) { it.copy(state = BackendState.SUCCEEDED) }
        stalled.update(id, INDEX) { it.copy(state = BackendState.SUCCEEDED) }
        assertThat(stalled.await(id, pi, Duration.ofMillis(100)).block()!!.outcome).isEqualTo(CallerOutcome.COMPLETED)
        stalled.update(id, PUBSUB) { it.copy(state = BackendState.FAILED, reason = "room closed") }
        assertThat(stalled.await(id, piu, Duration.ofMillis(100)).block()!!.outcome).isEqualTo(CallerOutcome.INCOMPLETE)
    }

    @Test
    fun `observe loses no change while changes race the subscription`() {
        committed()
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        pool.submit {
            start.await()
            repeat(400) { n -> store.update(id, PUBSUB) { it.copy(attempts = n + 1) } }
        }
        val seen = store.observe(id).doOnSubscribe { start.countDown() }
            .takeUntil { it.backends[PUBSUB]!!.attempts == 400 }
            .map { it.version }
            .collectList()
            .block(Duration.ofSeconds(10))!!
        pool.shutdownNow()
        assertThat(seen).isSorted
        assertThat(seen).doesNotHaveDuplicates()
        assertThat(seen.last()).isEqualTo(400L)
    }
}
```

**Step 3: Run the test to verify that it fails**

Run: `mvn -o -B -pl chat-core,chat-service-composite test -Dtest=MemoryCommandStatusStoreTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t9.log 2>&1; echo "exit $?"`
Expected: `exit 1` with `Unresolved reference 'memory'`.

**Step 4: Write the implementation**

`CommitMarker.kt`:

```kotlin
package com.demo.chat.service.composite.command.memory

/**
 * The one admission commit marker of decision 7. The mapping, the dispatch
 * entry, and the status record refer to one marker. They stay hidden until
 * [commit], which is one volatile write that cannot fail.
 */
class CommitMarker {
    enum class State { UNCOMMITTED, COMMITTED, ROLLED_BACK }

    @Volatile
    var state: State = State.UNCOMMITTED
        private set

    val committed: Boolean get() = state == State.COMMITTED

    fun commit() {
        state = State.COMMITTED
    }

    fun rollBack() {
        state = State.ROLLED_BACK
    }
}
```

`MemoryCommandStatusStore.kt`:

```kotlin
package com.demo.chat.service.composite.command.memory

import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.BackendState
import com.demo.chat.domain.command.BackendStatus
import com.demo.chat.domain.command.CallerOutcome
import com.demo.chat.domain.command.CommandStatus
import com.demo.chat.domain.command.CompletionRequirement
import com.demo.chat.domain.command.MessageSendResult
import com.demo.chat.domain.command.Receipt
import com.demo.chat.service.command.CommandCompletionService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Status records for the process lifetime. A record stays hidden until its
 * commit marker commits. A terminal backend state never changes again, so a
 * duplicate or stale completion cannot reverse it. Case 8 of the spec.
 */
open class MemoryCommandStatusStore<T : Any> : CommandCompletionService<T> {

    private inner class Entry(val marker: CommitMarker, @Volatile var status: CommandStatus<T>) {
        val changes: Sinks.Many<CommandStatus<T>> = Sinks.many().multicast().directBestEffort()
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    fun stage(command: AcceptedCommand<T, *>, receipt: Receipt<T>, marker: CommitMarker) {
        entries[command.commandId] = Entry(
            marker,
            CommandStatus(
                commandId = command.commandId,
                owner = command.owner,
                requestId = command.requestId,
                receipt = receipt,
                backends = command.obligations.associateWith { BackendStatus(BackendState.PENDING) },
                version = 0L,
            ),
        )
    }

    fun remove(commandId: String) {
        entries.remove(commandId)
    }

    /** One change per call. Changes of one command emit in version order, because one lock orders them. */
    fun update(commandId: String, backend: BackendId, change: (BackendStatus) -> BackendStatus) {
        val entry = entries[commandId] ?: return
        synchronized(entry) {
            val current = entry.status.backends[backend] ?: return
            if (current.state == BackendState.SUCCEEDED || current.state == BackendState.FAILED) return
            val updated = change(current)
            if (updated == current) return
            val next = entry.status.copy(
                backends = entry.status.backends + (backend to updated),
                version = entry.status.version + 1,
            )
            entry.status = next
            entry.changes.tryEmitNext(next)
        }
    }

    private fun visible(commandId: String): Entry? = entries[commandId]?.takeIf { it.marker.committed }

    override fun status(commandId: String): Mono<CommandStatus<T>> = Mono.fromCallable { visible(commandId)?.status }

    /** Decision 7: subscribe to the changes first, then read the record. Emit the record, then each newer change. */
    override fun observe(commandId: String): Flux<CommandStatus<T>> = Flux.defer {
        val entry = visible(commandId) ?: return@defer Flux.error<CommandStatus<T>>(NotFoundException)
        val buffer = Sinks.many().unicast().onBackpressureBuffer<CommandStatus<T>>()
        val subscription = entry.changes.asFlux().subscribe { buffer.tryEmitNext(it) }
        val snapshot = entry.status
        Flux.concat(Mono.just(snapshot), buffer.asFlux().filter { it.version > snapshot.version })
            .doFinally { subscription.dispose() }
    }

    /**
     * At the timeout, the record is evaluated again. A terminal result stays
     * terminal, and only an unresolved requirement answers `PENDING`.
     */
    override fun await(
        commandId: String,
        requirement: CompletionRequirement,
        timeout: Duration,
    ): Mono<MessageSendResult<T>> =
        observe(commandId)
            .map { evaluate(it, requirement) }
            .filter { it.outcome != CallerOutcome.PENDING }
            .next()
            .timeout(timeout, Mono.defer { status(commandId).map { evaluate(it, requirement) } })

    companion object {
        fun <T> evaluate(status: CommandStatus<T>, requirement: CompletionRequirement): MessageSendResult<T> {
            val required = requirement.backends.map { status.backends[it] }
            val outcome = when {
                requirement.backends.isEmpty() -> CallerOutcome.ACCEPTED
                required.any { it == null || it.state == BackendState.FAILED } -> CallerOutcome.INCOMPLETE
                required.all { it!!.state == BackendState.SUCCEEDED } -> CallerOutcome.COMPLETED
                else -> CallerOutcome.PENDING
            }
            return MessageSendResult(status.receipt, outcome, status.backends)
        }
    }
}
```

**Step 5: Run the test to verify that it passes**

Run: `mvn -o -B -pl chat-core,chat-service-composite test -Dtest=MemoryCommandStatusStoreTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t9.log 2>&1; echo "exit $?"; grep "Tests run:" /tmp/t9.log | tail -1`
Expected: `exit 0` and `Tests run: 11, Failures: 0, Errors: 0`.

**Step 6: Run two mutation checks**

Run Step 5 after each change, then restore the file by absolute path:

1. Delete the line `if (current.state == BackendState.SUCCEEDED || current.state == BackendState.FAILED) return`. Expected: `case 8` fails.
2. In the `await` fallback, map to `evaluate(it, requirement).copy(outcome = CallerOutcome.PENDING)`. Expected: `review 6` fails.

**Step 7: Commit**

```bash
git add chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command
git commit -m "Add the memory command status store and caller completion (<task-9-issue>)"
```

---

### Task 10: Admission with one commit marker

**Files:**
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/DispatchLog.kt`
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/MemoryDomainCommandBus.kt`
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/MemoryAdmissionTests.kt`

**Interfaces:**
- Consumes: `KeyAllocator<T>`, `TypeUtil<T>`, `CommandFingerprint`, `RequestIds`, `MemoryCommandStatusStore<T>`, `CommitMarker`.
- Produces:
  - `class DispatchEntry<T, V>(command: AcceptedCommand<T, V>, marker: CommitMarker)`
  - `class DispatchLog<T, V>` with `stage(entry)`, `remove(entry)`, `iterator()`, and `size()`
  - `interface AdmissionFaults { fun afterMapping(); fun afterDispatchEntry(); fun afterStaging(); fun beforeNotify() }` with `AdmissionFaults.NONE`
  - `class MemoryDomainCommandBus<T : Any, V>(allocator, typeUtil, status, log, obligations: Set<BackendId>, policy, notifier: () -> Unit, faults = AdmissionFaults.NONE, commandIds = { UUID.randomUUID().toString() }, clock = Clock.systemUTC()) : DomainCommandBus<T, V>` with `committedMappings(): Int` and `stagedMappings(): Int`

**Step 1: Write the failing test**

`MemoryAdmissionTests.kt`:

```kotlin
package com.demo.chat.test.service.composite.command

import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.command.CommandSubmission
import com.demo.chat.domain.command.InvalidRequestIdException
import com.demo.chat.domain.command.RequestConflictException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.command.BackendExecutionPolicy
import com.demo.chat.service.core.KeyAllocator
import com.demo.chat.service.composite.command.memory.AdmissionFaults
import com.demo.chat.service.composite.command.memory.DispatchLog
import com.demo.chat.service.composite.command.memory.MemoryCommandStatusStore
import com.demo.chat.service.composite.command.memory.MemoryDomainCommandBus
import com.demo.chat.test.key.FakeKeyServices
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MemoryAdmissionTests {
    private val roots = CommandFixtures.ROOTS
    private val registry = FakeKeyServices.long(roots)
    private val status = MemoryCommandStatusStore<Long>()
    private val log = DispatchLog<Long, String>()
    private val notifications = AtomicInteger()

    private fun bus(faults: AdmissionFaults = AdmissionFaults.NONE, ids: () -> String = { java.util.UUID.randomUUID().toString() }) =
        MemoryDomainCommandBus<Long, String>(
            KeyAllocator(LongKeyGenerator(1), roots), TypeUtil.LongUtil, status, log,
            CommandFixtures.PIU, BackendExecutionPolicy(), { notifications.incrementAndGet() }, faults, ids,
        )

    private fun submission(requestId: String = "r-1", text: String = "hello", owner: Long = 10L) =
        CommandSubmission(owner, requestId, owner, 100L, text)

    @Test
    fun `a new identity commits one mapping, one dispatch entry, and a visible status`() {
        val receipt = bus().submit(submission()).block()!!
        assertThat(log.size()).isEqualTo(1)
        assertThat(log.iterator().next().marker.committed).isTrue()
        assertThat(status.status(receipt.commandId).block()).isNotNull
        assertThat(receipt.messageKey.root).isEqualTo(roots.of(ChatDomain.MESSAGE).id)
        assertThat(notifications.get()).isEqualTo(1)
    }

    @Test
    fun `a repeat with the same intent answers the original receipt and emits nothing`() {
        val bus = bus()
        val first = bus.submit(submission()).block()!!
        val second = bus.submit(submission()).block()!!
        assertThat(second).isEqualTo(first)
        assertThat(log.size()).isEqualTo(1)
    }

    @Test
    fun `case 5 - concurrent duplicates admit one command and recover one receipt`() {
        val bus = bus()
        val pool = Executors.newFixedThreadPool(16)
        val start = CountDownLatch(1)
        val receipts = (1..16).map { pool.submit(Callable { start.await(); bus.submit(submission()).block()!! }) }
        start.countDown()
        val distinct = receipts.map { it.get(5, TimeUnit.SECONDS) }.toSet()
        pool.shutdownNow()
        assertThat(distinct).hasSize(1)
        assertThat(log.size()).isEqualTo(1)
        assertThat(bus.committedMappings()).isEqualTo(1)
    }

    @Test
    fun `case 6 - changed content under one identity is a conflict and the original stays`() {
        val bus = bus()
        val first = bus.submit(submission(text = "hello")).block()!!
        assertThatThrownBy { bus.submit(submission(text = "changed")).block() }
            .isInstanceOf(RequestConflictException::class.java)
        assertThat(bus.submit(submission(text = "hello")).block()).isEqualTo(first)
        assertThat(log.size()).isEqualTo(1)
    }

    @Test
    fun `case 7 - admission writes no key registry row`() {
        val receipt = bus().submit(submission()).block()!!
        assertThat(registry.rootOf(receipt.messageKey.id).block()).isNull()
    }

    @Test
    fun `case 29 - a failure after any staging write leaves no mapping, entry, status, or notification`() {
        val points = listOf("mapping", "dispatch entry", "status")
        points.forEach { point ->
            val localStatus = MemoryCommandStatusStore<Long>()
            val localLog = DispatchLog<Long, String>()
            val localNotifications = AtomicInteger()
            val failing = object : AdmissionFaults {
                override fun afterMapping() = fail("mapping", point)
                override fun afterDispatchEntry() = fail("dispatch entry", point)
                override fun afterStaging() = fail("status", point)
            }
            val bus = MemoryDomainCommandBus<Long, String>(
                KeyAllocator(LongKeyGenerator(1), roots), TypeUtil.LongUtil, localStatus, localLog,
                CommandFixtures.PIU, BackendExecutionPolicy(), { localNotifications.incrementAndGet() }, failing, { "c-fault" },
            )
            assertThatThrownBy { bus.submit(submission()).block() }.describedAs(point).hasMessageContaining("injected after $point")
            assertThat(bus.committedMappings()).describedAs(point).isZero()
            assertThat(bus.stagedMappings()).describedAs(point).isZero()
            assertThat(localLog.size()).describedAs(point).isZero()
            assertThat(localStatus.status("c-fault").block()).describedAs(point).isNull()
            assertThat(localNotifications.get()).describedAs(point).isZero()
        }
        val retried = bus().submit(submission()).block()!!
        assertThat(status.status(retried.commandId).block()).isNotNull
    }

    private fun fail(at: String, point: String) {
        if (at == point) throw IllegalStateException("injected after $point")
    }

    @Test
    fun `case 30 - a notification failure after commit still answers the receipt`() {
        val failing = object : AdmissionFaults {
            override fun beforeNotify() = throw IllegalStateException("injected notification failure")
        }
        val receipt = bus(failing).submit(submission()).block()!!
        assertThat(status.status(receipt.commandId).block()).isNotNull
        assertThat(log.iterator().next().marker.committed).isTrue()
    }

    @Test
    fun `case 12 - the mapping stays after every obligation resolves`() {
        val bus = bus()
        val first = bus.submit(submission()).block()!!
        CommandFixtures.PIU.forEach { backend ->
            status.update(first.commandId, backend) { it.copy(state = com.demo.chat.domain.command.BackendState.SUCCEEDED) }
        }
        assertThat(bus.submit(submission()).block()).isEqualTo(first)
        assertThat(bus.committedMappings()).isEqualTo(1)
    }

    @Test
    fun `an invalid request ID is refused before staging`() {
        assertThatThrownBy { bus().submit(submission(requestId = "has space")).block() }
            .isInstanceOf(InvalidRequestIdException::class.java)
        assertThat(log.size()).isZero()
    }

    @Test
    fun `a staged command stays hidden until the marker commits`() {
        val staged = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holding = object : AdmissionFaults {
            override fun afterStaging() {
                staged.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        val pool = Executors.newSingleThreadExecutor()
        val pending = pool.submit(Callable { bus(holding, { "c-held" }).submit(submission()).block()!! })
        staged.await(5, TimeUnit.SECONDS)
        assertThat(status.status("c-held").block()).isNull()
        assertThat(log.iterator().next().marker.committed).isFalse()
        release.countDown()
        assertThat(pending.get(5, TimeUnit.SECONDS).commandId).isEqualTo("c-held")
        assertThat(status.status("c-held").block()).isNotNull
        pool.shutdownNow()
    }
}
```

`TypeUtil.LongUtil` is the companion object of `TypeUtil`, in `chat-core/src/main/kotlin/com/demo/chat/domain/TypeUtil.kt`.

**Step 2: Run the test to verify that it fails**

Run: `mvn -o -B -pl chat-core,chat-service-composite test -Dtest=MemoryAdmissionTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t10.log 2>&1; echo "exit $?"`
Expected: `exit 1` with `Unresolved reference 'MemoryDomainCommandBus'`.

**Step 3: Write the implementation**

`DispatchLog.kt`:

```kotlin
package com.demo.chat.service.composite.command.memory

import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.domain.command.BackendId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class DispatchEntry<T, V>(val command: AcceptedCommand<T, V>, val marker: CommitMarker) {
    /** The obligations that a runtime queue holds. A rescan skips them. */
    val dispatched: MutableSet<BackendId> = ConcurrentHashMap.newKeySet()
}

/** Dispatch entries in staging order. An entry stays hidden until its marker commits. */
class DispatchLog<T, V> {
    private val entries = ConcurrentLinkedQueue<DispatchEntry<T, V>>()

    fun stage(entry: DispatchEntry<T, V>) {
        entries.add(entry)
    }

    fun remove(entry: DispatchEntry<T, V>) {
        entries.remove(entry)
    }

    fun iterator(): MutableIterator<DispatchEntry<T, V>> = entries.iterator()

    fun size(): Int = entries.size
}
```

`MemoryDomainCommandBus.kt`:

```kotlin
package com.demo.chat.service.composite.command.memory

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CommandOperation
import com.demo.chat.domain.command.CommandSubmission
import com.demo.chat.domain.command.Receipt
import com.demo.chat.domain.command.RequestConflictException
import com.demo.chat.domain.command.RequestIdentity
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.command.BackendExecutionPolicy
import com.demo.chat.service.command.CommandFingerprint
import com.demo.chat.service.command.DomainCommandBus
import com.demo.chat.service.command.RequestIds
import com.demo.chat.service.core.KeyAllocator
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Test seams for decision 7. Each runs after one staging write. Production uses [NONE]. */
interface AdmissionFaults {
    fun afterMapping() {}
    fun afterDispatchEntry() {}
    fun afterStaging() {}
    fun beforeNotify() {}

    companion object {
        val NONE = object : AdmissionFaults {}
    }
}

/**
 * Admission of decision 7. A per-identity lock is the critical section.
 * Admission stages the mapping, the dispatch entry, and the status record
 * behind one [CommitMarker], then exposes all three with one commit write.
 * A failure before the commit removes all three. A notification failure after
 * the commit does not reject the admission. The dispatcher rescans the log.
 */
class MemoryDomainCommandBus<T : Any, V>(
    private val allocator: KeyAllocator<T>,
    private val typeUtil: TypeUtil<T>,
    private val status: MemoryCommandStatusStore<T>,
    private val log: DispatchLog<T, V>,
    private val obligations: Set<BackendId>,
    private val policy: BackendExecutionPolicy,
    private val notifier: () -> Unit,
    private val faults: AdmissionFaults = AdmissionFaults.NONE,
    private val commandIds: () -> String = { UUID.randomUUID().toString() },
    private val clock: Clock = Clock.systemUTC(),
) : DomainCommandBus<T, V> {

    private class Mapping<T>(val fingerprint: String, val receipt: Receipt<T>, val marker: CommitMarker)

    private val logger = LoggerFactory.getLogger(MemoryDomainCommandBus::class.java)
    private val mappings = ConcurrentHashMap<RequestIdentity<T>, Mapping<T>>()
    private val locks = ConcurrentHashMap<RequestIdentity<T>, Any>()

    override fun submit(submission: CommandSubmission<T, V>): Mono<Receipt<T>> =
        Mono.fromCallable { admit(submission) }

    fun committedMappings(): Int = mappings.values.count { it.marker.committed }

    fun stagedMappings(): Int = mappings.values.count { !it.marker.committed }

    private fun admit(submission: CommandSubmission<T, V>): Receipt<T> {
        RequestIds.requireValid(submission.requestId)
        val identity = RequestIdentity(submission.owner, submission.requestId)
        val fingerprint = CommandFingerprint.of(
            CommandOperation.RECORD_MESSAGE,
            typeUtil.toString(submission.sender),
            typeUtil.toString(submission.dest),
            submission.content,
        )
        val lock = locks.computeIfAbsent(identity) { Any() }
        val receipt = synchronized(lock) {
            val existing = mappings[identity]
            if (existing != null) {
                if (existing.fingerprint != fingerprint) throw RequestConflictException(submission.requestId)
                return existing.receipt
            }
            stageAndCommit(identity, submission, fingerprint)
        }
        try {
            faults.beforeNotify()
            notifier()
        } catch (e: Exception) {
            logger.warn("The dispatcher notification failed after the commit of ${receipt.commandId}. The rescan dispatches it.", e)
        }
        return receipt
    }

    private fun stageAndCommit(
        identity: RequestIdentity<T>,
        submission: CommandSubmission<T, V>,
        fingerprint: String,
    ): Receipt<T> {
        val key = allocator.allocate(ChatDomain.MESSAGE)
        val messageKey: MessageKey<T> = SimpleMessageKey(key.id, key.root, submission.sender, submission.dest, clock.instant())
        val command = AcceptedCommand(
            commandId = commandIds(),
            owner = submission.owner,
            requestId = submission.requestId,
            rootId = key.root,
            operation = CommandOperation.RECORD_MESSAGE,
            orderingKey = submission.dest,
            schemaVersion = SCHEMA_VERSION,
            message = Message.create(messageKey, submission.content, true),
            obligations = obligations,
            executionPolicyVersion = policy.version,
        )
        val receipt = Receipt(command.commandId, messageKey)
        val marker = CommitMarker()
        val mapping = Mapping(fingerprint, receipt, marker)
        val entry = DispatchEntry(command, marker)

        // Every staging write sits inside the guard, so a failure between two
        // writes removes the writes before it. Each removal is safe to repeat.
        try {
            mappings[identity] = mapping
            faults.afterMapping()
            log.stage(entry)
            faults.afterDispatchEntry()
            status.stage(command, receipt, marker)
            faults.afterStaging()
            marker.commit()
        } catch (e: Throwable) {
            marker.rollBack()
            mappings.remove(identity, mapping)
            log.remove(entry)
            status.remove(command.commandId)
            throw e
        }
        return receipt
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
```

**Step 4: Run the test to verify that it passes**

Run: `mvn -o -B -pl chat-core,chat-service-composite test -Dtest=MemoryAdmissionTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t10.log 2>&1; echo "exit $?"; grep "Tests run:" /tmp/t10.log | tail -1`
Expected: `exit 0` and `Tests run: 10, Failures: 0, Errors: 0`.

**Step 5: Run two mutation checks**

1. In the `catch` block, delete `log.remove(entry)`. Run Step 4. Expected: `case 29` fails at the point `status`. Restore by absolute path.
   Then move `mappings[identity] = mapping` above the `try`, and delete `mappings.remove(identity, mapping)`. Expected: `case 29` fails at the point `mapping`. Restore by absolute path.
2. Move `notifier()` out of the `try` block, so its failure escapes. Run Step 4. Expected: `case 30` fails. Restore by absolute path.

Run `git diff --stat` and confirm that only the new files changed.

**Step 6: Commit**

```bash
git add chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/MemoryAdmissionTests.kt
git commit -m "Admit commands behind one commit marker (<task-10-issue>)"
```

---

### Task 11: Dispatch, execution, and recovery

**Files:**
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/CommandDispatcher.kt`
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/BackendRuntime.kt`
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/memory/MemoryCommandRuntime.kt`
- Create: `chat-core/src/test/kotlin/com/demo/chat/test/command/CommandBusContractTests.kt`
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/MemoryCommandRuntimeTests.kt`
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/MemoryCommandBusContractTests.kt`

**Interfaces:**
- Consumes: Tasks 9 and 10.
- Produces:
  - `interface DispatchFaults { fun beforeEnqueue(backend: BackendId) }` with `DispatchFaults.NONE`
  - `class CommandDispatcher<T : Any, V>(log, runtimes: Map<BackendId, BackendRuntime<T, V>>, scheduler: Scheduler, rescanInterval: Duration, faults: DispatchFaults = DispatchFaults.NONE) : AutoCloseable` with `signal()`
  - `class BackendRuntime<T : Any, V>(handler, policy, status, scheduler, clock) ` with `enqueue(command)`
  - `class MemoryCommandRuntime<T : Any, V>(allocator, typeUtil, handlers, settings, policy, faults, rescanInterval, dispatchFaults, clock)` : AutoCloseable` with `val bus: MemoryDomainCommandBus<T, V>` and `val completions: MemoryCommandStatusStore<T>`
  - `abstract class CommandBusContractTests` in the `chat-core` test jar. Stage 2 reuses it for `kafka`.

**Step 1: Write the shared contract test**

`chat-core/src/test/kotlin/com/demo/chat/test/command/CommandBusContractTests.kt`:

```kotlin
package com.demo.chat.test.command

import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CallerOutcome
import com.demo.chat.domain.command.CommandSubmission
import com.demo.chat.domain.command.CompletionRequirement
import com.demo.chat.domain.command.RequestConflictException
import com.demo.chat.service.command.CommandCompletionService
import com.demo.chat.service.command.DomainCommandBus
import com.demo.chat.service.command.DomainCommandHandler
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * The shared contract of a command bus provider. Case 22 of the spec. Stage 1
 * runs it for `memory`. Stage 2 runs it for `kafka`.
 */
abstract class CommandBusContractTests {
    class BusUnderTest(
        val bus: DomainCommandBus<Long, String>,
        val completions: CommandCompletionService<Long>,
        val close: () -> Unit,
    )

    /** Each handler must succeed at once unless the test says otherwise. */
    abstract fun start(handlers: List<DomainCommandHandler<Long, String>>): BusUnderTest

    abstract fun succeedingHandlers(): List<DomainCommandHandler<Long, String>>

    private var running: BusUnderTest? = null
    private val wait = Duration.ofSeconds(5)

    private fun started(): BusUnderTest = start(succeedingHandlers()).also { running = it }

    @AfterEach
    fun stop() {
        running?.close?.invoke()
    }

    private fun submission(requestId: String, text: String = "hello") = CommandSubmission(10L, requestId, 10L, 100L, text)

    @Test
    fun `a submitted command completes its requirement`() {
        val sut = started()
        val receipt = sut.bus.submit(submission("contract-1")).block(wait)!!
        val result = sut.completions.await(receipt.commandId, CompletionRequirement(setOf(BackendId.PERSISTENCE)), wait).block(wait)!!
        assertThat(result.outcome).isEqualTo(CallerOutcome.COMPLETED)
    }

    @Test
    fun `a repeated request recovers its receipt`() {
        val sut = started()
        val first = sut.bus.submit(submission("contract-2")).block(wait)!!
        assertThat(sut.bus.submit(submission("contract-2")).block(wait)).isEqualTo(first)
    }

    @Test
    fun `a changed intent under one request is a conflict`() {
        val sut = started()
        sut.bus.submit(submission("contract-3")).block(wait)
        assertThatThrownBy { sut.bus.submit(submission("contract-3", "changed")).block(wait) }
            .isInstanceOf(RequestConflictException::class.java)
    }

    @Test
    fun `status reports every obligation`() {
        val sut = started()
        val receipt = sut.bus.submit(submission("contract-4")).block(wait)!!
        sut.completions.await(receipt.commandId, CompletionRequirement(setOf(BackendId.PERSISTENCE)), wait).block(wait)
        val status = sut.completions.status(receipt.commandId).block(wait)!!
        assertThat(status.backends.keys).containsAll(listOf(BackendId.PERSISTENCE))
        assertThat(status.receipt).isEqualTo(receipt)
    }
}
```

**Step 2: Write the failing runtime tests**

`MemoryCommandBusContractTests.kt`:

```kotlin
package com.demo.chat.test.service.composite.command

import com.demo.chat.config.CommandBusSettings
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CompletionRequirement
import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.composite.command.memory.MemoryCommandRuntime
import com.demo.chat.service.core.KeyAllocator
import com.demo.chat.test.command.CommandBusContractTests
import java.time.Duration

class MemoryCommandBusContractTests : CommandBusContractTests() {
    override fun succeedingHandlers(): List<DomainCommandHandler<Long, String>> =
        CommandFixtures.PIU.map { ScriptedHandler(it) }

    override fun start(handlers: List<DomainCommandHandler<Long, String>>): BusUnderTest {
        val runtime = MemoryCommandRuntime(
            KeyAllocator(LongKeyGenerator(1), CommandFixtures.ROOTS), TypeUtil.LongUtil, handlers,
            CommandBusSettings(CompletionRequirement(setOf(BackendId.PERSISTENCE)), Duration.ofSeconds(5), Duration.ofSeconds(30)),
        )
        return BusUnderTest(runtime.bus, runtime.completions) { runtime.close() }
    }
}
```

`MemoryCommandRuntimeTests.kt`:

```kotlin
package com.demo.chat.test.service.composite.command

import com.demo.chat.config.CommandBusSettings
import com.demo.chat.domain.DefinitiveRefusalException
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.BackendId.INDEX
import com.demo.chat.domain.command.BackendId.PERSISTENCE
import com.demo.chat.domain.command.BackendId.PUBSUB
import com.demo.chat.domain.command.BackendState
import com.demo.chat.domain.command.CommandSubmission
import com.demo.chat.domain.command.CompletionRequirement
import com.demo.chat.domain.command.UncertainOutcomeException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.command.BackendExecutionPolicy
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.command.HandlerDescriptor
import com.demo.chat.service.command.SafeRepeatContract
import com.demo.chat.service.composite.command.memory.AdmissionFaults
import com.demo.chat.service.composite.command.memory.DispatchFaults
import com.demo.chat.service.composite.command.memory.MemoryCommandRuntime
import com.demo.chat.service.core.KeyAllocator
import com.demo.chat.test.service.composite.command.CommandFixtures.waitUntil
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import java.io.IOException
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MemoryCommandRuntimeTests {
    private val fast = BackendExecutionPolicy(firstBackoff = Duration.ofMillis(5), recoveryInterval = Duration.ofMillis(50))
    private val settings = CommandBusSettings(CompletionRequirement(setOf(PERSISTENCE, INDEX)), Duration.ofSeconds(5), Duration.ofMillis(50))
    private val runtimes = mutableListOf<MemoryCommandRuntime<Long, String>>()

    private fun runtime(
        handlers: List<DomainCommandHandler<Long, String>>,
        faults: AdmissionFaults = AdmissionFaults.NONE,
        rescan: Duration = Duration.ofMillis(100),
    ) = MemoryCommandRuntime(
        KeyAllocator(LongKeyGenerator(1), CommandFixtures.ROOTS), TypeUtil.LongUtil, handlers, settings, fast, faults, rescan,
    ).also { runtimes += it }

    @AfterEach
    fun close() = runtimes.forEach { it.close() }

    private fun submit(rt: MemoryCommandRuntime<Long, String>, requestId: String, room: Long = 100L) =
        rt.bus.submit(CommandSubmission(10L, requestId, 10L, room, "text $requestId")).block()!!

    private fun stateOf(rt: MemoryCommandRuntime<Long, String>, commandId: String, backend: BackendId) =
        rt.completions.status(commandId).block()!!.backends[backend]!!

    @Test
    fun `a persistent transient error ends uncertain and schedules recovery`() {
        val p = ScriptedHandler(PERSISTENCE) { _, _ -> Mono.error(IOException("reset")) }
        val rt = runtime(listOf(p, ScriptedHandler(INDEX), ScriptedHandler(PUBSUB)))
        val receipt = submit(rt, "r-transient")
        waitUntil { stateOf(rt, receipt.commandId, PERSISTENCE).state == BackendState.UNCERTAIN }
        assertThat(p.attemptsOf(receipt.commandId)).isGreaterThanOrEqualTo(5)
        assertThat(stateOf(rt, receipt.commandId, PERSISTENCE).nextRecoveryAt).isNotNull
    }

    @Test
    fun `the initial cycle stops at five attempts before recovery starts`() {
        val slowRecovery = BackendExecutionPolicy(firstBackoff = Duration.ofMillis(5), recoveryInterval = Duration.ofSeconds(30))
        val p = ScriptedHandler(PERSISTENCE) { _, _ -> Mono.error(IOException("reset")) }
        val rt = MemoryCommandRuntime(
            KeyAllocator(LongKeyGenerator(1), CommandFixtures.ROOTS), TypeUtil.LongUtil,
            listOf(p, ScriptedHandler(INDEX), ScriptedHandler(PUBSUB)), settings, slowRecovery,
        ).also { runtimes += it }
        val receipt = submit(rt, "r-five")
        waitUntil { stateOf(rt, receipt.commandId, PERSISTENCE).state == BackendState.UNCERTAIN }
        assertThat(p.attemptsOf(receipt.commandId)).isEqualTo(5)
    }

    @Test
    fun `a transient error then success completes once`() {
        val p = ScriptedHandler(PERSISTENCE) { _, attempt -> if (attempt == 1) Mono.error(IOException("reset")) else Mono.empty() }
        val rt = runtime(listOf(p, ScriptedHandler(INDEX), ScriptedHandler(PUBSUB)))
        val receipt = submit(rt, "r-retry")
        waitUntil { stateOf(rt, receipt.commandId, PERSISTENCE).state == BackendState.SUCCEEDED }
        assertThat(p.attemptsOf(receipt.commandId)).isEqualTo(2)
    }

    @Test
    fun `a definitive error is not retried`() {
        val p = ScriptedHandler(PERSISTENCE) { _, _ -> Mono.error(DefinitiveRefusalException("refused")) }
        val rt = runtime(listOf(p, ScriptedHandler(INDEX), ScriptedHandler(PUBSUB)))
        val receipt = submit(rt, "r-definitive")
        waitUntil { stateOf(rt, receipt.commandId, PERSISTENCE).state == BackendState.FAILED }
        assertThat(p.attemptsOf(receipt.commandId)).isEqualTo(1)
    }

    @Test
    fun `case 25 - recovery reuses the same command and creates no submission`() {
        val p = ScriptedHandler(PERSISTENCE) { _, attempt ->
            if (attempt <= 2) Mono.error(UncertainOutcomeException("maybe written")) else Mono.empty()
        }
        val rt = runtime(listOf(p, ScriptedHandler(INDEX), ScriptedHandler(PUBSUB)))
        val receipt = submit(rt, "r-recover")
        waitUntil { stateOf(rt, receipt.commandId, PERSISTENCE).state == BackendState.SUCCEEDED }
        val calls = p.calls.filter { it.commandId == receipt.commandId }
        assertThat(calls).hasSize(3)
        assertThat(calls.map { it.message.key.id }.toSet()).containsExactly(receipt.messageKey.id)
        assertThat(calls.map { it.message.key.timestamp }.toSet()).hasSize(1)
        assertThat(calls.map { it.message.data }.toSet()).containsExactly("text r-recover")
        assertThat(rt.bus.committedMappings()).isEqualTo(1)
    }

    @Test
    fun `case 9 and 26 - an uncertain command holds its room for that backend only, and recovery releases it`() {
        val release = CountDownLatch(1)
        val p = ScriptedHandler(PERSISTENCE) { command, _ ->
            if (command.requestId == "r-first" && release.count > 0) Mono.error(UncertainOutcomeException("held"))
            else Mono.empty()
        }
        val i = ScriptedHandler(INDEX)
        val rt = runtime(listOf(p, i, ScriptedHandler(PUBSUB)))
        val first = submit(rt, "r-first", room = 100L)
        val second = submit(rt, "r-second", room = 100L)
        val other = submit(rt, "r-other", room = 200L)

        waitUntil { stateOf(rt, first.commandId, PERSISTENCE).state == BackendState.UNCERTAIN }
        waitUntil { stateOf(rt, other.commandId, PERSISTENCE).state == BackendState.SUCCEEDED }
        waitUntil { stateOf(rt, second.commandId, INDEX).state == BackendState.SUCCEEDED }
        assertThat(p.attemptsOf(second.commandId)).describedAs("the held room for P").isZero()

        release.countDown()
        waitUntil { stateOf(rt, second.commandId, PERSISTENCE).state == BackendState.SUCCEEDED }
        assertThat(stateOf(rt, first.commandId, PERSISTENCE).state).isEqualTo(BackendState.SUCCEEDED)
        val order = p.calls.map { it.requestId }.filter { it != "r-other" }.distinct()
        assertThat(order).containsExactly("r-first", "r-second")
    }

    @Test
    fun `case 26 - a definitive refusal during recovery releases the room`() {
        val p = ScriptedHandler(PERSISTENCE) { command, attempt ->
            when {
                command.requestId != "r-refused" -> Mono.empty()
                attempt == 1 -> Mono.error(UncertainOutcomeException("maybe"))
                else -> Mono.error(DefinitiveRefusalException("refused in recovery"))
            }
        }
        val rt = runtime(listOf(p, ScriptedHandler(INDEX), ScriptedHandler(PUBSUB)))
        val refused = submit(rt, "r-refused")
        val next = submit(rt, "r-next")
        waitUntil { stateOf(rt, refused.commandId, PERSISTENCE).state == BackendState.FAILED }
        waitUntil { stateOf(rt, next.commandId, PERSISTENCE).state == BackendState.SUCCEEDED }
    }

    @Test
    fun `case 13 - delayed accepted work still runs, because TTL is disabled`() {
        val gate = CountDownLatch(1)
        val p = ScriptedHandler(PERSISTENCE) { command, _ ->
            if (command.requestId == "r-slow") Mono.fromCallable { gate.await(5, TimeUnit.SECONDS) }.then() else Mono.empty()
        }
        val rt = runtime(listOf(p, ScriptedHandler(INDEX), ScriptedHandler(PUBSUB)))
        submit(rt, "r-slow")
        val delayed = submit(rt, "r-delayed")
        Thread.sleep(500)
        gate.countDown()
        waitUntil { stateOf(rt, delayed.commandId, PERSISTENCE).state == BackendState.SUCCEEDED }
    }

    @Test
    fun `per-room staging order holds while an earlier entry of the room is hidden`() {
        val staged = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holding = object : AdmissionFaults {
            override fun afterStaging() {
                if (Thread.currentThread().name.startsWith("held-")) {
                    staged.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }
        }
        val p = ScriptedHandler(PERSISTENCE)
        val rt = runtime(listOf(p, ScriptedHandler(INDEX), ScriptedHandler(PUBSUB)), holding)
        val held = Executors.newSingleThreadExecutor { Thread(it, "held-admission") }
        val first = held.submit(Callable { submit(rt, "r-a", room = 100L) })
        staged.await(5, TimeUnit.SECONDS)
        val second = submit(rt, "r-b", room = 100L)
        val other = submit(rt, "r-c", room = 200L)

        waitUntil { stateOf(rt, other.commandId, PERSISTENCE).state == BackendState.SUCCEEDED }
        assertThat(p.attemptsOf(second.commandId)).describedAs("room 100 waits behind its hidden entry").isZero()
        assertThat(p.calls.map { it.requestId }).doesNotContain("r-a")

        release.countDown()
        val a = first.get(5, TimeUnit.SECONDS)
        waitUntil { stateOf(rt, second.commandId, PERSISTENCE).state == BackendState.SUCCEEDED }
        assertThat(p.calls.map { it.requestId }.filter { it != "r-c" }).containsExactly("r-a", "r-b")
        assertThat(stateOf(rt, a.commandId, PERSISTENCE).state).isEqualTo(BackendState.SUCCEEDED)
        held.shutdownNow()
    }

    @Test
    fun `case 30 - the periodic rescan dispatches a command whose notification failed`() {
        val failing = object : AdmissionFaults {
            override fun beforeNotify() = throw IllegalStateException("injected notification failure")
        }
        val p = ScriptedHandler(PERSISTENCE)
        val rt = runtime(listOf(p, ScriptedHandler(INDEX), ScriptedHandler(PUBSUB)), failing, Duration.ofMillis(100))
        val receipt = submit(rt, "r-lost-signal")
        waitUntil(Duration.ofSeconds(3)) { stateOf(rt, receipt.commandId, PERSISTENCE).state == BackendState.SUCCEEDED }
    }

    @Test
    fun `review 3 - a long attempt is marked uncertain, is never cancelled, and nothing overlaps it`() {
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        val p = ScriptedHandler(PERSISTENCE) { _, _ ->
            Mono.fromFuture(CompletableFuture.supplyAsync {
                maxInFlight.accumulateAndGet(inFlight.incrementAndGet()) { a, b -> maxOf(a, b) }
                Thread.sleep(400)
                inFlight.decrementAndGet()
            }).then()
        }
        val watchdog = BackendExecutionPolicy(
            firstBackoff = Duration.ofMillis(5), recoveryInterval = Duration.ofMillis(50), attemptTimeout = Duration.ofMillis(100),
        )
        val rt = MemoryCommandRuntime(
            KeyAllocator(LongKeyGenerator(1), CommandFixtures.ROOTS), TypeUtil.LongUtil,
            listOf(p, ScriptedHandler(INDEX), ScriptedHandler(PUBSUB)), settings, watchdog,
        ).also { runtimes += it }
        val first = submit(rt, "r-long")
        val second = submit(rt, "r-after")
        waitUntil { stateOf(rt, first.commandId, PERSISTENCE).state == BackendState.UNCERTAIN }
        assertThat(p.attemptsOf(first.commandId)).isEqualTo(1)
        waitUntil { stateOf(rt, second.commandId, PERSISTENCE).state == BackendState.SUCCEEDED }
        assertThat(stateOf(rt, first.commandId, PERSISTENCE).state).isEqualTo(BackendState.SUCCEEDED)
        assertThat(p.attemptsOf(first.commandId)).isEqualTo(1)
        assertThat(maxInFlight.get()).describedAs("attempts in flight at once").isEqualTo(1)
    }

    @Test
    fun `review 4 - an enqueue failure keeps the entry, and a rescan queues each remaining obligation once`() {
        val failOnce = AtomicBoolean(true)
        val faults = object : DispatchFaults {
            override fun beforeEnqueue(backend: BackendId) {
                if (backend == INDEX && failOnce.getAndSet(false)) throw IllegalStateException("injected enqueue failure")
            }
        }
        val p = ScriptedHandler(PERSISTENCE)
        val i = ScriptedHandler(INDEX)
        val u = ScriptedHandler(PUBSUB)
        val rt = MemoryCommandRuntime(
            KeyAllocator(LongKeyGenerator(1), CommandFixtures.ROOTS), TypeUtil.LongUtil,
            listOf(p, i, u), settings, fast, AdmissionFaults.NONE, Duration.ofMillis(100), faults,
        ).also { runtimes += it }
        val receipt = submit(rt, "r-enqueue")
        waitUntil { listOf(PERSISTENCE, INDEX, PUBSUB).all { stateOf(rt, receipt.commandId, it).state == BackendState.SUCCEEDED } }
        assertThat(listOf(p, i, u).map { it.attemptsOf(receipt.commandId) }).containsExactly(1, 1, 1)
    }

    @Test
    fun `case 27 - a handler without a safe-repeat contract fails construction`() {
        val unsafe = object : DomainCommandHandler<Long, String> {
            override val descriptor = HandlerDescriptor(PERSISTENCE, setOf(ChatDomain.MESSAGE), CommandFixtures.descriptor(PERSISTENCE).operations, null)
            override fun handle(command: com.demo.chat.domain.command.AcceptedCommand<Long, String>) = Mono.empty<Void>()
        }
        assertThatThrownBy { runtime(listOf(unsafe, ScriptedHandler(INDEX), ScriptedHandler(PUBSUB))) }
            .hasMessageContaining("declares no safe-repeat contract")
    }

    @Test
    fun `case 27 - an unsupported contract version fails construction`() {
        val wrong = object : DomainCommandHandler<Long, String> {
            override val descriptor = HandlerDescriptor(
                PERSISTENCE, setOf(ChatDomain.MESSAGE), CommandFixtures.descriptor(PERSISTENCE).operations,
                SafeRepeatContract("message-persistence", 9),
            )
            override fun handle(command: com.demo.chat.domain.command.AcceptedCommand<Long, String>) = Mono.empty<Void>()
        }
        assertThatThrownBy { runtime(listOf(wrong, ScriptedHandler(INDEX), ScriptedHandler(PUBSUB))) }
            .hasMessageContaining("version 9")
    }

    @Test
    fun `case 27 - an inactive vector provider does not prevent construction`() {
        val rt = runtime(listOf(ScriptedHandler(PERSISTENCE), ScriptedHandler(INDEX), ScriptedHandler(PUBSUB)))
        val receipt = submit(rt, "r-no-vector")
        assertThat(rt.completions.status(receipt.commandId).block()!!.backends.keys).doesNotContain(BackendId.VECTOR)
    }

    @Test
    fun `a requirement on a backend with no handler fails construction`() {
        assertThatThrownBy { runtime(listOf(ScriptedHandler(PERSISTENCE), ScriptedHandler(PUBSUB))) }
            .hasMessageContaining("INDEX")
    }
}
```

**Step 3: Run the tests to verify that they fail**

Run: `mvn -o -B -pl chat-core,chat-service-composite test -Dtest='MemoryCommandRuntimeTests,MemoryCommandBusContractTests' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t11.log 2>&1; echo "exit $?"`
Expected: `exit 1` with `Unresolved reference 'MemoryCommandRuntime'`.

**Step 4: Write the dispatcher**

`CommandDispatcher.kt`:

```kotlin
package com.demo.chat.service.composite.command.memory

import com.demo.chat.domain.command.BackendId
import org.slf4j.LoggerFactory
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.scheduler.Scheduler
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/** A test seam before each queue insertion. Production uses [NONE]. */
interface DispatchFaults {
    fun beforeEnqueue(backend: BackendId) {}

    companion object {
        val NONE = object : DispatchFaults {}
    }
}

/**
 * Moves committed entries from the dispatch log to the backend runtimes.
 *
 * The scan keeps staging order. For each room it stops at a hidden entry,
 * and it continues when that entry commits or rolls back. A [signal] starts
 * a scan, and a periodic rescan covers a lost signal. Decision 7 of the spec.
 */
class CommandDispatcher<T : Any, V>(
    private val log: DispatchLog<T, V>,
    private val runtimes: Map<BackendId, BackendRuntime<T, V>>,
    private val scheduler: Scheduler,
    rescanInterval: Duration,
    private val faults: DispatchFaults = DispatchFaults.NONE,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(CommandDispatcher::class.java)
    private val pending = AtomicBoolean(false)
    private val draining = AtomicBoolean(false)
    private val periodic: Disposable = Flux.interval(rescanInterval, rescanInterval, scheduler).subscribe { signal() }

    fun signal() {
        pending.set(true)
        if (draining.compareAndSet(false, true)) scheduler.schedule { drain() }
    }

    private fun drain() {
        try {
            while (pending.getAndSet(false)) scan()
        } finally {
            draining.set(false)
            if (pending.get()) signal()
        }
    }

    private fun scan() {
        val blocked = HashSet<T>()
        val entries = log.iterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            val room = entry.command.orderingKey
            when (entry.marker.state) {
                CommitMarker.State.ROLLED_BACK -> entries.remove()
                CommitMarker.State.UNCOMMITTED -> blocked.add(room)
                CommitMarker.State.COMMITTED -> if (room !in blocked) {
                    if (dispatchAll(entry)) entries.remove() else blocked.add(room)
                }
            }
        }
    }

    /**
     * Queues each obligation that no queue holds yet. The entry leaves the log
     * only after every obligation is queued. A failure keeps the entry and
     * holds its room, so a rescan queues the rest in order.
     */
    private fun dispatchAll(entry: DispatchEntry<T, V>): Boolean {
        for (backend in entry.command.obligations) {
            if (backend in entry.dispatched) continue
            try {
                faults.beforeEnqueue(backend)
                runtimes.getValue(backend).enqueue(entry.command)
                entry.dispatched.add(backend)
            } catch (e: Exception) {
                logger.warn("Queueing ${entry.command.commandId} for $backend failed. A rescan retries it.", e)
                return false
            }
        }
        return true
    }

    override fun close() = periodic.dispose()
}
```

**Step 5: Write the backend runtime**

`BackendRuntime.kt`:

```kotlin
package com.demo.chat.service.composite.command.memory

import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.domain.command.BackendState
import com.demo.chat.service.command.BackendExecutionPolicy
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.command.FailureClass
import reactor.core.Exceptions
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.util.retry.Retry
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs one handler. Decision 12 of the spec.
 *
 * Each room has a FIFO queue. A room handles one command at a time. A
 * retrying or uncertain command holds the later commands of its room. Only a
 * success or a definitive failure releases them. Other rooms continue.
 */
class BackendRuntime<T : Any, V>(
    private val handler: DomainCommandHandler<T, V>,
    private val policy: BackendExecutionPolicy,
    private val status: MemoryCommandStatusStore<T>,
    private val scheduler: Scheduler,
    private val clock: Clock = Clock.systemUTC(),
) {
    private sealed interface Result {
        data object Succeeded : Result
        data class Failed(val reason: String) : Result
        data class Uncertain(val reason: String) : Result
    }

    private class Room<T, V> {
        val queue = ConcurrentLinkedQueue<AcceptedCommand<T, V>>()
        val busy = AtomicBoolean(false)
    }

    private val backend = handler.descriptor.backend
    private val rooms = ConcurrentHashMap<T, Room<T, V>>()

    fun enqueue(command: AcceptedCommand<T, V>) {
        val room = rooms.computeIfAbsent(command.orderingKey) { Room() }
        room.queue.add(command)
        pump(room)
    }

    private fun pump(room: Room<T, V>) {
        if (!room.busy.compareAndSet(false, true)) return
        val command = room.queue.poll()
        if (command == null) {
            room.busy.set(false)
            if (room.queue.isNotEmpty()) pump(room)
            return
        }
        initialCycle(command).subscribe { settle(room, command, it) }
    }

    /**
     * One attempt. The watchdog marks a long attempt uncertain and cancels
     * nothing, because a cancel does not prove that the backend work stopped.
     * This Mono ends only when the handler ends. So no later attempt, and no
     * later command of the room, overlaps it.
     */
    private fun attempt(command: AcceptedCommand<T, V>): Mono<Void> = Mono.create { sink ->
        status.update(command.commandId, backend) { it.copy(attempts = it.attempts + 1) }
        val watchdog = scheduler.schedule({
            status.update(command.commandId, backend) {
                it.copy(
                    state = BackendState.UNCERTAIN,
                    reason = "An attempt runs longer than ${policy.attemptTimeout}. No later attempt starts until it ends.",
                )
            }
        }, policy.attemptTimeout.toMillis(), TimeUnit.MILLISECONDS)
        Mono.defer { handler.handle(command) }.subscribe(
            null,
            { error -> watchdog.dispose(); sink.error(error) },
            { watchdog.dispose(); sink.success() },
        )
    }

    private fun initialCycle(command: AcceptedCommand<T, V>): Mono<Result> =
        attempt(command)
            .retryWhen(
                Retry.backoff(policy.retriesAfterFirst, policy.firstBackoff)
                    .filter { policy.classify(it) == FailureClass.TRANSIENT }
                    .scheduler(scheduler)
            )
            .then(Mono.just<Result>(Result.Succeeded))
            .onErrorResume { Mono.just(terminalOf(it)) }

    private fun recoveryAttempt(command: AcceptedCommand<T, V>): Mono<Result> =
        attempt(command)
            .then(Mono.just<Result>(Result.Succeeded))
            .onErrorResume { error ->
                Mono.just(
                    if (policy.classify(error) == FailureClass.DEFINITIVE) Result.Failed(reasonOf(error))
                    else Result.Uncertain(reasonOf(error))
                )
            }

    private fun terminalOf(error: Throwable): Result =
        if (Exceptions.isRetryExhausted(error)) Result.Uncertain(reasonOf(error.cause ?: error))
        else when (policy.classify(error)) {
            FailureClass.DEFINITIVE -> Result.Failed(reasonOf(error))
            FailureClass.TRANSIENT, FailureClass.UNCERTAIN -> Result.Uncertain(reasonOf(error))
        }

    private fun settle(room: Room<T, V>, command: AcceptedCommand<T, V>, result: Result) {
        when (result) {
            Result.Succeeded -> {
                status.update(command.commandId, backend) { it.copy(state = BackendState.SUCCEEDED, reason = null, nextRecoveryAt = null) }
                release(room)
            }
            is Result.Failed -> {
                status.update(command.commandId, backend) { it.copy(state = BackendState.FAILED, reason = result.reason, nextRecoveryAt = null) }
                release(room)
            }
            is Result.Uncertain -> {
                val next = clock.instant().plus(policy.recoveryInterval)
                status.update(command.commandId, backend) {
                    it.copy(state = BackendState.UNCERTAIN, reason = result.reason, nextRecoveryAt = next)
                }
                Mono.delay(policy.recoveryInterval, scheduler)
                    .then(recoveryAttempt(command))
                    .subscribe { settle(room, command, it) }
            }
        }
    }

    private fun release(room: Room<T, V>) {
        room.busy.set(false)
        pump(room)
    }

    private fun reasonOf(error: Throwable): String = "${error.javaClass.simpleName}: ${error.message}"
}
```

**Step 6: Write the runtime assembly**

`MemoryCommandRuntime.kt`:

```kotlin
package com.demo.chat.service.composite.command.memory

import com.demo.chat.config.CommandBusSettings
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.command.BackendExecutionPolicy
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.command.SafeRepeatContracts
import com.demo.chat.service.core.KeyAllocator
import reactor.core.scheduler.Schedulers
import java.time.Clock
import java.time.Duration

/**
 * The Stage 1 `memory` command bus in one process. Construction is the
 * startup check of each active handler: a missing or unsupported safe-repeat
 * contract fails it before admission accepts any command. Case 27.
 */
class MemoryCommandRuntime<T : Any, V>(
    allocator: KeyAllocator<T>,
    typeUtil: TypeUtil<T>,
    handlers: List<DomainCommandHandler<T, V>>,
    settings: CommandBusSettings,
    policy: BackendExecutionPolicy = BackendExecutionPolicy(recoveryInterval = settings.recoveryInterval),
    faults: AdmissionFaults = AdmissionFaults.NONE,
    rescanInterval: Duration = Duration.ofSeconds(1),
    dispatchFaults: DispatchFaults = DispatchFaults.NONE,
    clock: Clock = Clock.systemUTC(),
) : AutoCloseable {
    private val scheduler = Schedulers.newBoundedElastic(16, Int.MAX_VALUE, "command-runtime")
    private val log = DispatchLog<T, V>()
    val completions = MemoryCommandStatusStore<T>()
    private val dispatcher: CommandDispatcher<T, V>
    val bus: MemoryDomainCommandBus<T, V>

    init {
        handlers.forEach { SafeRepeatContracts.requireSupported(it.descriptor) }
        val duplicated = handlers.groupBy { it.descriptor.backend }.filterValues { it.size > 1 }.keys
        check(duplicated.isEmpty()) { "More than one handler is active for $duplicated." }
        val active = handlers.map { it.descriptor.backend }.toSet()
        val missing = settings.requirement.backends - active
        check(missing.isEmpty()) { "The completion requirement names $missing, and no handler is active for it." }

        val runtimes = handlers.associate { it.descriptor.backend to BackendRuntime(it, policy, completions, scheduler, clock) }
        dispatcher = CommandDispatcher(log, runtimes, scheduler, rescanInterval, dispatchFaults)
        bus = MemoryDomainCommandBus(allocator, typeUtil, completions, log, active, policy, dispatcher::signal, faults, clock = clock)
    }

    override fun close() {
        dispatcher.close()
        scheduler.dispose()
    }
}
```

**Step 7: Run the tests to verify that they pass**

Run: `mvn -o -B -pl chat-core,chat-service-composite test -Dtest='MemoryCommandRuntimeTests,MemoryCommandBusContractTests' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t11.log 2>&1; echo "exit $?"; grep "Tests run:" /tmp/t11.log | tail -2`
Expected: `exit 0`. `MemoryCommandRuntimeTests` reports 16 tests, and `MemoryCommandBusContractTests` reports 4, all with 0 failures.

Run the class three times more. Each run must pass. A failure in one run is a timing defect and must be fixed before the commit.

**Step 8: Run four mutation checks**

Run Step 7 after each change, then restore by absolute path:

1. In `CommandDispatcher.scan`, change `CommitMarker.State.UNCOMMITTED -> blocked.add(room)` to `CommitMarker.State.UNCOMMITTED -> Unit`. Expected: `per-room staging order holds` fails.
2. In `BackendRuntime.settle`, add `release(room)` at the end of the `Uncertain` branch. Expected: `case 9 and 26` fails.
3. Change `Retry.backoff(policy.retriesAfterFirst` to `Retry.backoff(policy.initialAttempts.toLong()`. Expected: `the initial cycle stops at five attempts` fails with 6.
4. In `MemoryCommandRuntime`, delete `handlers.forEach { SafeRepeatContracts.requireSupported(it.descriptor) }`. Expected: both contract cases of case 27 fail.
5. In `BackendRuntime.attempt`, add `.timeout(policy.attemptTimeout, scheduler)` after the `Mono.create` block. Expected: `review 3` fails, because a retry overlaps the running attempt.
6. In `CommandDispatcher.scan`, call `entries.remove()` before `dispatchAll(entry)`. Expected: `review 4` fails, because the remaining obligations are lost.
7. In `dispatchAll`, delete `if (backend in entry.dispatched) continue`. Expected: `review 4` fails, because `P` runs twice.

**Step 9: Commit**

```bash
git add chat-core/src/test/kotlin/com/demo/chat/test/command/CommandBusContractTests.kt chat-service-composite/src
git commit -m "Dispatch, execute, and recover commands on the memory bus (<task-11-issue>)"
```

---
### Task 12: Memory Pubsub room sinks, emit result, and callback thread

**Files:**
- Modify: `chat-messaging-memory/src/main/kotlin/com/demo/chat/pubsub/memory/impl/MemoryTopicPubSubService.kt`
- Create: `chat-messaging-memory/src/main/kotlin/com/demo/chat/pubsub/memory/impl/PublicationExceptions.kt`
- Test: `chat-messaging-memory/src/test/kotlin/com/demo/chat/test/messaging/MemoryPubSubEmitTests.kt`

**Interfaces:**
- Consumes: `NotFoundException`, `ChatException`, `NoEffectRefusal`.
- Produces:
  - Each room sink is `onBackpressureBuffer(256, false)` with one internal subscriber. The sink survives when every external listener leaves, and an empty room never fills its buffer.
  - `sendMessage` succeeds only on `EmitResult.OK`. Decision 13 does not change.
  - `PublicationRetryableException` extends `IOException`, so the policy classifies it as transient. `PublicationRefusedException` implements `NoEffectRefusal`, so the policy classifies it as definitive.
  - `listenTo` delivers through `publishOn(Schedulers.boundedElastic())`.
  - `close(topicId)` releases the internal subscriber of that room. `close()` releases every room. Spring calls `close()` at shutdown, because a `@Bean` infers a public no-argument `close` as its destroy method.
  - `internal fun hasRoomSink(topic: T): Boolean`, `internal fun roomSinkCount(): Int`, and `internal fun drainOf(topic: T): Disposable?`, for tests in the same module.

The owner found the defect on 2026-10-08. The old sink used `multicast().onBackpressureBuffer()`, whose auto-cancel is on. After the last listener of a room left, the sink stayed cancelled. A new listener completed at once, and each send returned `FAIL_CANCELLED`. The internal subscriber keeps the sink open and requests without a limit, so the buffer fills only behind a slow external listener. The publication log supplies replay for a listener that arrives later.

Classification of the other emit results, as decision 13 requires:

| Emit result | Exception | Class |
|---|---|---|
| `FAIL_OVERFLOW` | `PublicationRetryableException` | transient. A slow external listener fills the buffer. |
| `FAIL_NON_SERIALIZED` | `PublicationRetryableException` | transient |
| `FAIL_ZERO_SUBSCRIBER` | `PublicationRefusedException` | definitive. It cannot occur while the internal subscriber exists. |
| `FAIL_TERMINATED` | `PublicationRefusedException` | definitive |
| `FAIL_CANCELLED` | `PublicationRefusedException` | definitive |

**Step 1: Write the failing test**

```kotlin
package com.demo.chat.test.messaging

import com.demo.chat.domain.Message
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.pubsub.memory.impl.MemoryTopicPubSubService
import com.demo.chat.pubsub.memory.impl.PublicationRetryableException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.reactivestreams.Subscription
import reactor.core.publisher.BaseSubscriber
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MemoryPubSubEmitTests {
    private val pubsub = MemoryTopicPubSubService<Long, String>()
    private fun message(id: Long, room: Long = 1L) = Message.create(SimpleMessageKey(id, 9L, 10L, room), "m$id", true)

    @AfterEach
    fun shutdown() = pubsub.close()

    @Test
    fun `a subscriber callback runs off the emitting thread`() {
        pubsub.open(1L).block()
        val callbackThread = CompletableFuture<String>()
        pubsub.listenTo(1L).subscribe { callbackThread.complete(Thread.currentThread().name) }
        val emitter = Executors.newSingleThreadExecutor { Thread(it, "emitter-thread") }
        emitter.submit { pubsub.sendMessage(message(1L)).block() }.get(5, TimeUnit.SECONDS)
        assertThat(callbackThread.get(5, TimeUnit.SECONDS)).isNotEqualTo("emitter-thread")
        emitter.shutdownNow()
    }

    @Test
    fun `a send to a room that is not open fails with not found`() {
        StepVerifier.create(pubsub.sendMessage(message(2L, room = 404L))).expectError(NotFoundException::class.java).verify()
    }

    @Test
    fun `a listener that reconnects after the last listener left receives new messages`() {
        pubsub.open(5L).block()
        val first = pubsub.listenTo(5L).map { it.key.id }.next().toFuture()
        pubsub.sendMessage(message(51L, room = 5L)).block()
        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(51L)

        val second = pubsub.listenTo(5L).map { it.key.id }.next().toFuture()
        Thread.sleep(100)
        StepVerifier.create(pubsub.sendMessage(message(52L, room = 5L))).verifyComplete()
        assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(52L)
    }

    @Test
    fun `an empty room accepts more than 256 messages, and a later listener receives only new ones`() {
        pubsub.open(6L).block()
        (1L..1000L).forEach { id ->
            StepVerifier.create(pubsub.sendMessage(message(id, room = 6L))).verifyComplete()
        }
        val later = pubsub.listenTo(6L).map { it.key.id }.next().toFuture()
        Thread.sleep(100)
        pubsub.sendMessage(message(1001L, room = 6L)).block()
        assertThat(later.get(5, TimeUnit.SECONDS)).isEqualTo(1001L)
    }

    @Test
    fun `a slow external listener overflows the room with a retryable error`() {
        pubsub.open(3L).block()
        pubsub.listenTo(3L).subscribe(object : BaseSubscriber<Message<Long, String>>() {
            override fun hookOnSubscribe(subscription: Subscription) = Unit
        })
        val errors = (1L..2000L).mapNotNull { id ->
            runCatching { pubsub.sendMessage(message(id, room = 3L)).block() }.exceptionOrNull()
        }
        assertThat(errors).isNotEmpty
        assertThat(errors.first()).isInstanceOf(PublicationRetryableException::class.java)
        assertThat(errors.first().message).contains("FAIL_OVERFLOW")
    }

    @Test
    fun `closing a room releases its internal subscriber, and shutdown releases every room`() {
        pubsub.open(7L).block()
        pubsub.open(8L).block()
        assertThat(pubsub.hasRoomSink(7L)).isTrue()
        val drain = pubsub.drainOf(7L)!!

        pubsub.close(7L).block(Duration.ofSeconds(5))
        assertThat(pubsub.hasRoomSink(7L)).isFalse()
        assertThat(drain.isDisposed).isTrue()
        StepVerifier.create(pubsub.sendMessage(message(71L, room = 7L))).expectError(NotFoundException::class.java).verify()

        pubsub.close()
        assertThat(pubsub.roomSinkCount()).isZero()
    }
}
```

**Step 2: Run the test to verify that it fails**

Run: `mvn -o -B -pl chat-core,chat-messaging-memory test -Dtest=MemoryPubSubEmitTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t12.log 2>&1; echo "exit $?"`
Expected: `exit 1` with `Unresolved reference 'PublicationRetryableException'`.

**Step 3: Write the implementation**

`PublicationExceptions.kt`:

```kotlin
package com.demo.chat.pubsub.memory.impl

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.NoEffectRefusal
import reactor.core.publisher.Sinks
import java.io.IOException

/** An emission that a retry can complete. It is an `IOException`, so the policy treats it as transient. */
class PublicationRetryableException(topic: Any?, val result: Sinks.EmitResult) :
    IOException("The emission to room $topic returned $result. A retry can succeed.")

/** An emission that a retry cannot complete. The sink refused it, so no effect happened. */
class PublicationRefusedException(topic: Any?, val result: Sinks.EmitResult) :
    ChatException("The emission to room $topic returned $result."), NoEffectRefusal
```

In `MemoryTopicPubSubService`:

1. Change the class declaration to `class MemoryTopicPubSubService<T : Any, V> : TopicPubSubService<T, V>, AutoCloseable {`.
2. Replace the `sinks` field with the room map and its factory:

```kotlin
    /** One room: its buffered sink and the internal subscriber that keeps the sink open. */
    private class RoomSink<T, V>(val sink: Sinks.Many<Message<T, V>>, val drain: Disposable)

    private val rooms: ConcurrentHashMap<T, RoomSink<T, V>> = ConcurrentHashMap()

    /**
     * Creates the sink and its internal subscriber together, once per room.
     * Auto-cancel is off, so the sink survives when every external listener
     * leaves. The internal subscriber requests without a limit and does no
     * work, so an empty room never fills the buffer. A slow external listener
     * can still fill it, and the send then fails as retryable.
     */
    private fun roomOf(topic: T): RoomSink<T, V> = rooms.computeIfAbsent(topic) {
        val sink = Sinks.many().multicast().onBackpressureBuffer<Message<T, V>>(Queues.SMALL_BUFFER_SIZE, false)
        RoomSink(sink, sink.asFlux().subscribe())
    }

    private fun release(topic: T) {
        rooms.remove(topic)?.let { room ->
            room.drain.dispose()
            room.sink.tryEmitComplete()
        }
    }

    internal fun hasRoomSink(topic: T): Boolean = rooms.containsKey(topic)

    internal fun roomSinkCount(): Int = rooms.size

    internal fun drainOf(topic: T): Disposable? = rooms[topic]?.drain

    /** Shutdown releases every room. A `@Bean` infers this method as its destroy method. */
    override fun close() {
        rooms.keys.toList().forEach(::release)
    }
```

3. In `open`, replace the `sinks.getOrPut(topicId) { ... }` call with `roomOf(topicId)`.
4. In `close(topicId)`, replace `sinks.remove(topicId)?.tryEmitComplete()` with `release(topicId)`.
5. Replace `sendMessage` and `listenTo`, and change `exists` to read `rooms`:

```kotlin
    /**
     * `U` succeeds only on `EmitResult.OK`. Decision 13 of the spec. `OK` means
     * the sink accepted the emission. It does not mean a recipient received it.
     */
    override fun sendMessage(message: Message<T, V>): Mono<Void> =
        topicExistsOrError(message.key.dest)
            .flatMap {
                val sink = rooms[message.key.dest]?.sink ?: return@flatMap Mono.error<Void>(NotFoundException)
                when (val result = sink.tryEmitNext(message)) {
                    Sinks.EmitResult.OK -> Mono.empty()
                    Sinks.EmitResult.FAIL_OVERFLOW, Sinks.EmitResult.FAIL_NON_SERIALIZED ->
                        Mono.error(PublicationRetryableException(message.key.dest, result))
                    else -> Mono.error(PublicationRefusedException(message.key.dest, result))
                }
            }
            .then()

    /**
     * Each subscriber receives on its own worker, so a callback never runs on
     * the thread that emits. The room coordinator relies on that. Decision 9.
     */
    override fun listenTo(topic: T): Flux<out Message<T, V>> =
        roomOf(topic).sink.asFlux().publishOn(Schedulers.boundedElastic())

    override fun exists(topic: T): Mono<Boolean> =
        Mono.fromCallable { rooms.containsKey(topic) }
```

Add the imports `reactor.core.Disposable` and `reactor.util.concurrent.Queues`. Keep `reactor.core.scheduler.Schedulers`.

`listenTo` keeps its old rule: it creates the room when no room exists. The register records that rule as a known behavior of this provider. This task does not change it.

**Step 4: Run the tests to verify that they pass**

Run: `mvn -o -B -pl chat-core,chat-messaging-memory test > /tmp/t12.log 2>&1; echo "exit $?"; grep "Tests run:" /tmp/t12.log | tail -1`
Expected: `exit 0` with 0 failures. `MemoryPubSubEmitTests` reports 6 tests, and the existing `MemoryPubSubTests` still pass.

**Step 5: Run three mutation checks**

Run Step 4 after each change, then restore the file by absolute path:

1. Change `onBackpressureBuffer<Message<T, V>>(Queues.SMALL_BUFFER_SIZE, false)` to `onBackpressureBuffer<Message<T, V>>()`, and remove the internal subscriber by replacing `sink.asFlux().subscribe()` with `Disposables.disposed()`. Expected: the reconnection test fails.
2. Keep auto-cancel off, and replace `sink.asFlux().subscribe()` with `Disposables.disposed()`. Expected: the empty-room test fails with `FAIL_OVERFLOW`.
3. In `release`, delete both `room.drain.dispose()` and `room.sink.tryEmitComplete()`. Completing the sink also disposes its internal subscriber, so the mutation must remove both lines. Expected: the cleanup test fails, because the internal subscriber stays active.

**Step 6: Commit**

```bash
git add chat-messaging-memory/src
git commit -m "Keep each memory room sink open with an internal subscriber, and report the emit result (<task-12-issue>)"
```

---

### Task 13: The room coordinator and the publication log

**Files:**
- Modify: `chat-service-composite/pom.xml` (add a test dependency)
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/publication/SerialTaskQueue.kt`
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/publication/RoomPublications.kt`
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/RoomPublicationsTests.kt`

**Interfaces:**
- Consumes: `TopicPubSubService<T, V>`, `UncertainOutcomeException`.
- Produces:
  - `class SerialTaskQueue(scheduler: Scheduler) { fun <R : Any> submit(task: () -> Mono<R>): Mono<R> }`
  - `class PublicationRecord<T, V>(commandId: String, message: Message<T, V>, sequence: Long)`
  - `interface PublicationBookkeeping { fun beforeCommit() }` with `PublicationBookkeeping.NONE`
  - `class LiveSubscription<T, V>(replay: List<Message<T, V>>, messages: Flux<Message<T, V>>, live: Disposable)` with `close()`
  - `class RoomPublications<T : Any, V>(pubsub, scheduler = Schedulers.boundedElastic(), bookkeeping = PublicationBookkeeping.NONE)` with `publish(commandId, message): Mono<Void>`, `subscribe(room): Mono<LiveSubscription<T, V>>`, `isPublished(room, commandId)`, `publicationCount(room)`, and `snapshot(room)`

**Step 1: Add the test dependency**

In `chat-service-composite/pom.xml`, after the `chat-core` test-jar dependency, add:

```xml
        <dependency>
            <groupId>com.demo</groupId>
            <artifactId>chat-messaging-memory</artifactId>
            <version>0.0.1</version>
            <scope>test</scope>
        </dependency>
```

The tests need the real memory sink, because only it emits to subscribers on the calling thread without `publishOn`. Run `just check-deps > /tmp/t13-deps.log 2>&1; echo "exit $?"` and expect `exit 0`.

**Step 2: Write the failing test**

```kotlin
package com.demo.chat.test.service.composite.command

import com.demo.chat.domain.Message
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.command.UncertainOutcomeException
import com.demo.chat.pubsub.memory.impl.MemoryTopicPubSubService
import com.demo.chat.service.composite.command.publication.PublicationBookkeeping
import com.demo.chat.service.composite.command.publication.RoomPublications
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RoomPublicationsTests {
    private val room = 100L
    private val pubsub = MemoryTopicPubSubService<Long, String>().also { it.open(room).block() }
    private fun message(id: Long) = Message.create(SimpleMessageKey(id, 9L, 10L, room), "m$id", true)

    @Test
    fun `a committed publication enters the replay of a later listener`() {
        val publications = RoomPublications(pubsub)
        publications.publish("c-1", message(1L)).block()
        val live = publications.subscribe(room).block()!!
        assertThat(live.replay.map { it.key.id }).containsExactly(1L)
        live.close()
    }

    @Test
    fun `a failed emit leaves the log and the marker unchanged`() {
        val publications = RoomPublications(pubsub)
        val closedRoom = Message.create(SimpleMessageKey(2L, 9L, 10L, 404L), "lost", true)
        assertThatThrownBy { publications.publish("c-2", closedRoom).block() }
        assertThat(publications.publicationCount(404L)).isZero()
        assertThat(publications.isPublished(404L, "c-2")).isFalse()
    }

    @Test
    fun `case 14 - a repeated publication of one command emits once`() {
        val publications = RoomPublications(pubsub)
        val heard = AtomicInteger()
        pubsub.listenTo(room).subscribe { heard.incrementAndGet() }
        publications.publish("c-3", message(3L)).block()
        publications.publish("c-3", message(3L)).block()
        CommandFixtures.waitUntil { heard.get() >= 1 }
        Thread.sleep(200)
        assertThat(heard.get()).isEqualTo(1)
        assertThat(publications.publicationCount(room)).isEqualTo(1)
    }

    @Test
    fun `case 31 - the record and the marker commit together after OK`() {
        val publications = RoomPublications(pubsub)
        publications.publish("c-4", message(4L)).block()
        assertThat(publications.isPublished(room, "c-4")).isTrue()
        assertThat(publications.publicationCount(room)).isEqualTo(1)
    }

    @Test
    fun `case 31 - a bookkeeping failure after OK is uncertain, and a repeat commits both`() {
        val failOnce = AtomicBoolean(true)
        val bookkeeping = object : PublicationBookkeeping {
            override fun beforeCommit() {
                if (failOnce.getAndSet(false)) throw IllegalStateException("injected bookkeeping failure")
            }
        }
        val publications = RoomPublications(pubsub, bookkeeping = bookkeeping)
        val heard = AtomicInteger()
        pubsub.listenTo(room).subscribe { heard.incrementAndGet() }

        assertThatThrownBy { publications.publish("c-5", message(5L)).block() }
            .isInstanceOf(UncertainOutcomeException::class.java)
        assertThat(publications.isPublished(room, "c-5")).isFalse()
        assertThat(publications.publicationCount(room)).isZero()

        publications.publish("c-5", message(5L)).block()
        assertThat(publications.isPublished(room, "c-5")).isTrue()
        assertThat(publications.publicationCount(room)).isEqualTo(1)
        CommandFixtures.waitUntil { heard.get() == 2 }
    }

    @Test
    fun `review 5 - the records and the markers of a room always agree`() {
        val flaky = AtomicInteger()
        val bookkeeping = object : PublicationBookkeeping {
            override fun beforeCommit() {
                if (flaky.incrementAndGet() % 3 == 0) throw IllegalStateException("injected bookkeeping failure")
            }
        }
        val publications = RoomPublications(pubsub, bookkeeping = bookkeeping)
        val stop = AtomicBoolean(false)
        val disagreements = AtomicInteger()
        val reader = Thread {
            while (!stop.get()) {
                val state = publications.snapshot(room)
                if (state.records.map { it.commandId }.toSet() != state.commandIds) disagreements.incrementAndGet()
            }
        }.also { it.start() }
        (1L..60L).forEach { n -> runCatching { publications.publish("c-agree-$n", message(100L + n)).block() } }
        stop.set(true)
        reader.join(5000)
        assertThat(disagreements.get()).isZero()
        assertThat(publications.publicationCount(room)).isEqualTo(40)
    }

    @Test
    fun `case 32 - a listener created inside another listener's callback gets the message once and nothing deadlocks`() {
        val publications = RoomPublications(pubsub)
        val first = publications.subscribe(room).block()!!
        val nested = CompletableFuture<List<Long>>()
        first.messages.subscribe {
            val inner = publications.subscribe(room).block(Duration.ofSeconds(2))!!
            nested.complete(inner.replay.map { m -> m.key.id })
            inner.close()
        }
        publications.publish("c-6", message(6L)).block(Duration.ofSeconds(5))
        assertThat(nested.get(5, TimeUnit.SECONDS)).containsExactly(6L)
        first.close()
    }
}
```

**Step 3: Run the test to verify that it fails**

Run: `mvn -o -B -pl chat-core,chat-messaging-memory,chat-service-composite test -Dtest=RoomPublicationsTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t13.log 2>&1; echo "exit $?"`
Expected: `exit 1` with `Unresolved reference 'RoomPublications'`.

**Step 4: Write the implementation**

`SerialTaskQueue.kt`:

```kotlin
package com.demo.chat.service.composite.command.publication

import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs one task at a time. A task submitted during another task waits until
 * that task completes, so no task reenters another. Decision 9 of the spec.
 */
class SerialTaskQueue(private val scheduler: Scheduler) {
    private val tasks = ConcurrentLinkedQueue<Runnable>()
    private val running = AtomicBoolean(false)

    fun <R : Any> submit(task: () -> Mono<R>): Mono<R> = Mono.create { sink ->
        tasks.add(Runnable {
            Mono.defer(task)
                .doFinally { release() }
                .subscribe({ sink.success(it) }, { sink.error(it) }, { sink.success() })
        })
        tryStart()
    }

    private fun tryStart() {
        if (tasks.isEmpty() || !running.compareAndSet(false, true)) return
        val next = tasks.poll()
        if (next == null) {
            running.set(false)
            tryStart()
            return
        }
        scheduler.schedule(next)
    }

    private fun release() {
        running.set(false)
        tryStart()
    }
}
```

`RoomPublications.kt`:

```kotlin
package com.demo.chat.service.composite.command.publication

import com.demo.chat.domain.Message
import com.demo.chat.domain.command.UncertainOutcomeException
import com.demo.chat.service.core.TopicPubSubService
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import java.util.concurrent.ConcurrentHashMap

class PublicationRecord<T, V>(val commandId: String, val message: Message<T, V>, val sequence: Long)

/** A test seam between `EmitResult.OK` and the bookkeeping commit. Production uses [NONE]. */
interface PublicationBookkeeping {
    fun beforeCommit() {}

    companion object {
        val NONE = object : PublicationBookkeeping {}
    }
}

/** The replay up to the boundary, and the live messages after it. */
class LiveSubscription<T, V>(
    val replay: List<Message<T, V>>,
    val messages: Flux<Message<T, V>>,
    private val live: Disposable,
) {
    fun close() = live.dispose()
}

/**
 * The room coordinator, the publication log, and the repeat markers.
 * Decisions 9 and 13 of the spec.
 *
 * One coordinator task runs each publication and each subscription boundary
 * of a room. A publication prepares its record and its repeat marker before
 * the emission. After `EmitResult.OK`, one write of the room state commits
 * both. So every reader sees both or neither.
 */
class RoomPublications<T : Any, V>(
    private val pubsub: TopicPubSubService<T, V>,
    private val scheduler: Scheduler = Schedulers.boundedElastic(),
    private val bookkeeping: PublicationBookkeeping = PublicationBookkeeping.NONE,
) {
    /** The records and the repeat markers of one room. One write replaces both together. */
    class RoomState<T, V>(val records: List<PublicationRecord<T, V>>, val commandIds: Set<String>)

    private val coordinators = ConcurrentHashMap<T, SerialTaskQueue>()
    private val states = ConcurrentHashMap<T, RoomState<T, V>>()

    private fun <R : Any> onRoom(room: T, task: () -> Mono<R>): Mono<R> =
        coordinators.computeIfAbsent(room) { SerialTaskQueue(scheduler) }.submit(task)

    private fun stateOf(room: T): RoomState<T, V> = states[room] ?: RoomState(emptyList(), emptySet())

    fun publish(commandId: String, message: Message<T, V>): Mono<Void> {
        val room = message.key.dest
        return onRoom(room) {
            val current = stateOf(room)
            if (commandId in current.commandIds) return@onRoom Mono.just(true)
            // Prepared before the emission. The commit below is one write.
            val record = PublicationRecord(commandId, message, current.records.size.toLong())
            val next = RoomState(current.records + record, current.commandIds + commandId)
            pubsub.sendMessage(message).then(Mono.fromCallable {
                try {
                    bookkeeping.beforeCommit()
                    states[room] = next
                } catch (e: Exception) {
                    throw UncertainOutcomeException(
                        "Command $commandId reached the live stream of room $room, and its bookkeeping failed.", e,
                    )
                }
                true
            })
        }.then()
    }

    fun subscribe(room: T): Mono<LiveSubscription<T, V>> = onRoom(room) {
        Mono.fromCallable {
            val buffer = Sinks.many().unicast().onBackpressureBuffer<Message<T, V>>()
            val live = pubsub.listenTo(room).subscribe(
                { buffer.tryEmitNext(it) }, { buffer.tryEmitError(it) }, { buffer.tryEmitComplete() },
            )
            val boundary = stateOf(room).records
            LiveSubscription(boundary.map { it.message }, buffer.asFlux().doFinally { live.dispose() }, live)
        }
    }

    fun isPublished(room: T, commandId: String): Boolean = commandId in stateOf(room).commandIds

    fun publicationCount(room: T): Int = stateOf(room).records.size

    /** One read of the room state, for tests of the visibility boundary. */
    fun snapshot(room: T): RoomState<T, V> = stateOf(room)
}
```

**Step 5: Run the test to verify that it passes**

Run the Step 3 command. Expected: `exit 0` and `Tests run: 7, Failures: 0, Errors: 0`.

**Step 6: Run two mutation checks**

Run Step 5 after each change, then restore by absolute path:

1. In `MemoryTopicPubSubService.listenTo`, remove `.publishOn(Schedulers.boundedElastic())`. Expected: `case 32` fails, because the nested boundary waits behind the running publication task. Rebuild `chat-messaging-memory` in the same reactor command.
2. In `RoomPublications.publish`, move `states[room] = next` before `pubsub.sendMessage(message)`. Expected: `a failed emit leaves the log and the marker unchanged` fails.
3. Split the commit into two writes: keep a separate `published` set, and add to it after `states[room] = next`. Expected: `review 5` reports disagreements.

**Step 7: Commit**

```bash
git add chat-service-composite/pom.xml chat-service-composite/src
git commit -m "Add the room coordinator and the publication log (<task-13-issue>)"
```

---

### Task 14: The four message handlers

**Files:**
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/command/handler/MessageCommandHandlers.kt`
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/MessageCommandHandlersTests.kt`

**Interfaces:**
- Consumes: `IKeyService<T>.register`, `MessagePersistence<T, V>`, `MessageIndexService<T, V, Q>`, `MessageVectorIndexer<T>`, `RoomPublications<T, V>`.
- Produces: `MessagePersistenceHandler<T, V>(keys, persistence)`, `MessageIndexHandler<T, V, Q>(index)`, `MessageVectorHandler<T, V>(indexer)`, `MessagePubSubHandler<T : Any, V>(publications)`. Each declares the supported contract of its backend.

**Step 1: Write the failing test**

```kotlin
package com.demo.chat.test.service.composite.command

import com.demo.chat.domain.KeyRootConflictException
import com.demo.chat.domain.Key
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.pubsub.memory.impl.MemoryTopicPubSubService
import com.demo.chat.service.command.BackendExecutionPolicy
import com.demo.chat.service.command.FailureClass
import com.demo.chat.service.command.SafeRepeatContracts
import com.demo.chat.service.composite.command.handler.MessageIndexHandler
import com.demo.chat.service.composite.command.handler.MessagePersistenceHandler
import com.demo.chat.service.composite.command.handler.MessagePubSubHandler
import com.demo.chat.service.composite.command.publication.RoomPublications
import com.demo.chat.test.key.FakeKeyServices
import com.demo.chat.test.service.composite.FakeMessageIndex
import com.demo.chat.test.service.composite.FakeMessagePersistence
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.IOException

class MessageCommandHandlersTests {
    private val roots = CommandFixtures.ROOTS
    private val registry = FakeKeyServices.long(roots)
    private val command = CommandFixtures.command()

    @Test
    fun `each handler declares the supported contract of its backend`() {
        val handlers = listOf(
            MessagePersistenceHandler(registry, FakeMessagePersistence()),
            MessageIndexHandler(FakeMessageIndex()),
            MessagePubSubHandler(RoomPublications(MemoryTopicPubSubService<Long, String>())),
        )
        handlers.forEach { SafeRepeatContracts.requireSupported(it.descriptor) }
    }

    @Test
    fun `case 20 - P registers the key and then stores the message`() {
        val persistence = FakeMessagePersistence()
        MessagePersistenceHandler(registry, persistence).handle(command).block()
        assertThat(registry.rootOf(command.message.key.id).block()).isEqualTo(command.message.key.root)
        assertThat(persistence.added.map { it.key.id }).containsExactly(command.message.key.id)
    }

    @Test
    fun `case 20 - a storage failure fails P after registration, and a repeat succeeds`() {
        val failing = FakeMessagePersistence(failure = IOException("store down"))
        assertThatThrownBy { MessagePersistenceHandler(registry, failing).handle(command).block() }
        assertThat(registry.rootOf(command.message.key.id).block()).isEqualTo(command.message.key.root)

        val working = FakeMessagePersistence()
        MessagePersistenceHandler(registry, working).handle(command).block()
        assertThat(working.added).hasSize(1)
    }

    @Test
    fun `case 19 - a root conflict fails P and stores nothing`() {
        registry.register(Key.of(command.message.key.id, roots.of(ChatDomain.USER).id)).block()
        val persistence = FakeMessagePersistence()
        assertThatThrownBy { MessagePersistenceHandler(registry, persistence).handle(command).block() }
            .isInstanceOf(KeyRootConflictException::class.java)
        assertThat(persistence.added).isEmpty()
        assertThat(BackendExecutionPolicy().classify(KeyRootConflictException(1L, 2L, 3L))).isEqualTo(FailureClass.DEFINITIVE)
    }

    @Test
    fun `I adds the message to the index`() {
        val index = FakeMessageIndex()
        MessageIndexHandler(index).handle(command).block()
        assertThat(index.added.map { it.key.id }).containsExactly(command.message.key.id)
    }

    @Test
    fun `review focus 2 - U on a room that is not open fails definitively`() {
        val handler = MessagePubSubHandler(RoomPublications(MemoryTopicPubSubService<Long, String>()))
        val error = runCatching { handler.handle(command).block() }.exceptionOrNull()!!
        assertThat(error).isSameAs(NotFoundException)
        assertThat(BackendExecutionPolicy().classify(error)).isEqualTo(FailureClass.DEFINITIVE)
    }
}
```

`FakeMessagePersistence` and `FakeMessageIndex` are `internal` in the same test module, so this file can import them.

**Step 2: Run the test to verify that it fails**

Run: `mvn -o -B -pl chat-core,chat-messaging-memory,chat-service-composite test -Dtest=MessageCommandHandlersTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t14.log 2>&1; echo "exit $?"`
Expected: `exit 1` with `Unresolved reference 'handler'`.

**Step 3: Write the handlers**

```kotlin
package com.demo.chat.service.composite.command.handler

import com.demo.chat.domain.Message
import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CommandOperation
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.command.HandlerDescriptor
import com.demo.chat.service.command.SafeRepeatContracts
import com.demo.chat.service.composite.command.publication.RoomPublications
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.vector.MessageVectorIndexer
import reactor.core.publisher.Mono

private fun descriptorOf(backend: BackendId) = HandlerDescriptor(
    backend, setOf(ChatDomain.MESSAGE), setOf(CommandOperation.RECORD_MESSAGE), SafeRepeatContracts.SUPPORTED.getValue(backend),
)

/** `P`: register the assigned key, then store the message. `P` succeeds only after both. Decision 2. */
class MessagePersistenceHandler<T, V>(
    private val keys: IKeyService<T>,
    private val persistence: MessagePersistence<T, V>,
) : DomainCommandHandler<T, V> {
    override val descriptor = descriptorOf(BackendId.PERSISTENCE)

    override fun handle(command: AcceptedCommand<T, V>): Mono<Void> =
        keys.register(command.message.key).then(Mono.defer { persistence.add(command.message) })
}

/** `I`: Lucene replaces by the exact key. Cassandra writes by full primary key. */
class MessageIndexHandler<T, V, Q>(private val index: MessageIndexService<T, V, Q>) : DomainCommandHandler<T, V> {
    override val descriptor = descriptorOf(BackendId.INDEX)

    override fun handle(command: AcceptedCommand<T, V>): Mono<Void> = index.add(command.message)
}

/** `V`: the indexer takes text. Every composition binds the message value to `String`. */
class MessageVectorHandler<T, V>(private val indexer: MessageVectorIndexer<T>) : DomainCommandHandler<T, V> {
    override val descriptor = descriptorOf(BackendId.VECTOR)

    @Suppress("UNCHECKED_CAST")
    override fun handle(command: AcceptedCommand<T, V>): Mono<Void> = indexer.add(command.message as Message<T, String>)
}

/** `U`: one publication task per command. Decision 13. */
class MessagePubSubHandler<T : Any, V>(private val publications: RoomPublications<T, V>) : DomainCommandHandler<T, V> {
    override val descriptor = descriptorOf(BackendId.PUBSUB)

    override fun handle(command: AcceptedCommand<T, V>): Mono<Void> =
        publications.publish(command.commandId, command.message)
}
```

**Step 4: Run the test to verify that it passes**

Run the Step 2 command. Expected: `exit 0` and `Tests run: 6, Failures: 0, Errors: 0`.

**Step 5: Commit**

```bash
git add chat-service-composite/src
git commit -m "Add the P, I, V, and U message handlers (<task-14-issue>)"
```

The safe-repeat proof against real providers is in Task 17. A fake cannot prove a provider contract.

---

### Task 15: The submitter identity adapter

**Files:**
- Create: `chat-security/src/main/kotlin/com/demo/chat/security/access/ContextSubmitterIdentity.kt`
- Create: `chat-security/src/main/kotlin/com/demo/chat/config/auth/SubmitterIdentityConfiguration.kt`
- Test: `chat-security/src/test/kotlin/com/demo/chat/test/ContextSubmitterIdentityTests.kt`

**Interfaces:**
- Consumes: `ContextIdentity<T>`, `SubmitterIdentity<T>`.
- Produces: the bean `submitterIdentity(): SubmitterIdentity<T>` under `app.service.composite.auth=true`. It answers an authenticated user, and nothing for `Anon` or for no identity.

The configuration sits in `com.demo.chat.config.auth`. `ChatApp` scans `com.demo.chat.config` alone, so another package is never discovered. `RoomOwnerGrantConfiguration` records that trap.

**Step 1: Write the failing test**

```kotlin
package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.security.access.ContextSubmitterIdentity
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.test.key.TestKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder

class ContextSubmitterIdentityTests {
    private val anon: Key<Long> = TestKeys.key(1L)
    private val user: Key<Long> = TestKeys.key(2L)
    private val roots = RootKeysFixture.ofLong(emptyMap(), admin = TestKeys.key(9999L), anon = anon)
    private val submitter = ContextSubmitterIdentity(ContextIdentity(roots), roots)

    private fun currentWith(authentication: Authentication?): Key<Long>? {
        val call = submitter.current()
        return (if (authentication == null) call
        else call.contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))).block()
    }

    @Test
    fun `an authenticated user is the submitter`() {
        val details = ChatUserDetails(User.create(user, "u", "handle", "http://u"), listOf())
        assertThat(currentWith(UsernamePasswordAuthenticationToken(details, "secret", listOf()))).isEqualTo(user)
    }

    @Test
    fun `review 1 - an anonymous caller has no submitter`() {
        val token = AnonymousAuthenticationToken("key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")))
        assertThat(currentWith(token)).isNull()
    }

    @Test
    fun `no security context gives no submitter`() {
        assertThat(currentWith(null)).isNull()
    }
}
```

**Step 2: Run the test to verify that it fails**

Run: `mvn -o -B -pl chat-core,chat-security test -Dtest=ContextSubmitterIdentityTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t15.log 2>&1; echo "exit $?"`
Expected: `exit 1` with `Unresolved reference 'ContextSubmitterIdentity'`.

**Step 3: Write the adapter and its configuration**

`ContextSubmitterIdentity.kt`:

```kotlin
package com.demo.chat.security.access

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.command.SubmitterIdentity
import reactor.core.publisher.Mono

/**
 * The sender and owner of an ordinary submission is the authenticated user.
 * `ContextIdentity` holds the rule. Every anonymous caller holds one shared
 * `Anon` key, so `Anon` owns nothing here. The spec scopes request identity to
 * an authenticated owner. An empty answer refuses the call.
 */
class ContextSubmitterIdentity<T>(
    private val identity: ContextIdentity<T>,
    private val rootKeys: RootKeys<T>,
) : SubmitterIdentity<T> {
    override fun current(): Mono<Key<T>> = identity.identity().filter { it != rootKeys.anon() }
}
```

`SubmitterIdentityConfiguration.kt`:

```kotlin
package com.demo.chat.config.auth

import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.security.access.ContextSubmitterIdentity
import com.demo.chat.service.command.SubmitterIdentity
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "app.service.composite", name = ["auth"], havingValue = "true")
open class SubmitterIdentityConfiguration<T>(private val rootKeys: RootKeys<T>) {
    @Bean
    open fun submitterIdentity(): SubmitterIdentity<T> = ContextSubmitterIdentity(ContextIdentity(rootKeys), rootKeys)
}
```

**Step 4: Run the test to verify that it passes**

Run the Step 2 command. Expected: `exit 0` and `Tests run: 3, Failures: 0, Errors: 0`.

**Step 5: Run a mutation check**

Remove `.filter { it != rootKeys.anon() }`. Run Step 4. Expected: `review 1` fails. Restore by absolute path.

**Step 6: Commit**

```bash
git add chat-security/src
git commit -m "Add the submitter identity adapter over ContextIdentity (<task-15-issue>)"
```

---

### Task 16: Switch the message service to commands

**Files:**
- Modify: `chat-core/src/main/kotlin/com/demo/chat/service/composite/ChatMessageService.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessagingServiceImpl.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/access/MessagingServiceAccess.kt`
- Modify: `chat-client-rsocket/src/main/kotlin/com/demo/chat/client/rsocket/clients/composite/MessagingClient.kt`
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/command/MessageCommandConfiguration.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/CompositeServiceBeansConfiguration.kt`
- Create: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/MessagingStack.kt`
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/command/MessageCommandServiceTests.kt`
- Modify: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessagingServiceHistoryTests.kt`
- Modify: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessagingServiceVectorTests.kt`
- Modify: `chat-client-rsocket/src/test/kotlin/com/demo/chat/test/rsocket/controller/composite/MessageControllerTests.kt`
- Modify: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/StoreWritePathTests.kt`
- Modify: `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisGrantRestartTests.kt`
- Modify: each vector deployment test that sends and then recalls. Step 9 finds them.

**Interfaces:**
- Consumes: Tasks 9 to 15.
- Produces:
  - `ChatMessageService.submit(req: MessageSubmitRequest<T, V>): Mono<out MessageSendResult<T>>`
  - `ChatMessageService.commandStatus(req: CommandStatusRequest): Mono<out CommandStatus<T>>`
  - The new `MessagingServiceImpl` constructor: `(messageIndex, messagePersistence, publications, topicIdToQuery, verifier, commandBus, completions, submitter: SubmitterIdentity<T>?, requirement, timeout, requestIds)`
  - The beans `roomPublications`, `commandBusSettings`, and `memoryCommandRuntime`

**Step 1: Write the test stack**

`MessagingStack.kt`:

```kotlin
package com.demo.chat.test.service.composite.command

import com.demo.chat.config.CommandBusSettings
import com.demo.chat.domain.Key
import com.demo.chat.domain.MapRequestConverters
import com.demo.chat.domain.Message
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CompletionRequirement
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.pubsub.memory.impl.MemoryTopicPubSubService
import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.command.SubmitterIdentity
import com.demo.chat.service.composite.command.handler.MessageIndexHandler
import com.demo.chat.service.composite.command.handler.MessagePersistenceHandler
import com.demo.chat.service.composite.command.handler.MessagePubSubHandler
import com.demo.chat.service.composite.command.memory.MemoryCommandRuntime
import com.demo.chat.service.composite.command.publication.RoomPublications
import com.demo.chat.service.composite.impl.MessagingServiceImpl
import com.demo.chat.service.core.KeyAllocator
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.test.key.FakeKeyServices
import com.demo.chat.test.service.composite.FakeMessageIndex
import com.demo.chat.test.service.composite.FakeMessagePersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/** A handler that waits on [gate] before it runs. An open gate is `Mono.empty()`. */
class GatedHandler<T, V>(private val delegate: DomainCommandHandler<T, V>, private val gate: Mono<Void>) :
    DomainCommandHandler<T, V> {
    override val descriptor = delegate.descriptor
    override fun handle(command: AcceptedCommand<T, V>): Mono<Void> = gate.then(Mono.defer { delegate.handle(command) })
}

/** Counts live subscribers, so a test can prove that a cancel disposes them. */
class CountingPubSub(private val delegate: TopicPubSubService<Long, String>) : TopicPubSubService<Long, String> by delegate {
    val subscribers = AtomicInteger()
    override fun listenTo(topic: Long): Flux<out Message<Long, String>> =
        delegate.listenTo(topic)
            .doOnSubscribe { subscribers.incrementAndGet() }
            .doFinally { subscribers.decrementAndGet() }
}

class MessagingStack(
    requirement: CompletionRequirement = CompletionRequirement(setOf(BackendId.PERSISTENCE, BackendId.INDEX)),
    timeout: Duration = Duration.ofSeconds(5),
    submitterKey: Key<Long>? = null,
    useSubmitter: Boolean = true,
) : AutoCloseable {
    val roots = CommandFixtures.ROOTS
    val registry = FakeKeyServices.long(roots)
    val user: Key<Long> = registry.register(SENDER, ChatDomain.USER)
    val room: Key<Long> = registry.register(ROOM, ChatDomain.MESSAGE_TOPIC)
    val persistence = FakeMessagePersistence()
    val index = FakeMessageIndex()
    val pubsub = CountingPubSub(MemoryTopicPubSubService<Long, String>())
    val publications = RoomPublications(pubsub)
    val persistenceGate = Sinks.empty<Void>()
    val indexGate = Sinks.empty<Void>()
    val pubsubGate = Sinks.empty<Void>()
    var gatePersistence = false
    var gateIndex = false
    var gatePubsub = false

    private fun gate(flag: () -> Boolean, sink: Sinks.Empty<Void>): Mono<Void> = Mono.defer { if (flag()) sink.asMono() else Mono.empty() }

    val runtime = MemoryCommandRuntime(
        KeyAllocator(LongKeyGenerator(1), roots), TypeUtil.LongUtil,
        listOf<DomainCommandHandler<Long, String>>(
            GatedHandler(MessagePersistenceHandler(registry, persistence), gate({ gatePersistence }, persistenceGate)),
            GatedHandler(MessageIndexHandler(index), gate({ gateIndex }, indexGate)),
            GatedHandler(MessagePubSubHandler(publications), gate({ gatePubsub }, pubsubGate)),
        ),
        CommandBusSettings(requirement, timeout, Duration.ofMillis(100)),
    )

    private val submitter = object : SubmitterIdentity<Long> {
        override fun current(): Mono<Key<Long>> = Mono.justOrEmpty(submitterKey ?: user)
    }

    val service = MessagingServiceImpl(
        messageIndex = index,
        messagePersistence = persistence,
        publications = publications,
        topicIdToQuery = MapRequestConverters()::topicIdToQuery,
        verifier = KeyVerifier(registry, roots),
        commandBus = runtime.bus,
        completions = runtime.completions,
        submitter = if (useSubmitter) submitter else null,
        requirement = requirement,
        timeout = timeout,
    )

    init {
        pubsub.open(ROOM).block()
    }

    override fun close() = runtime.close()

    companion object {
        const val SENDER = 10L
        const val ROOM = 100L
    }
}
```

**Step 2: Write the failing service test**

`MessageCommandServiceTests.kt`:

```kotlin
package com.demo.chat.test.service.composite.command

import com.demo.chat.domain.AccessDeniedException
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.MessageSubmitRequest
import com.demo.chat.domain.CommandStatusRequest
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.command.CallerOutcome
import com.demo.chat.domain.command.CommandPendingException
import com.demo.chat.domain.command.CompletionRequirement
import com.demo.chat.domain.command.RequestConflictException
import com.demo.chat.domain.command.SenderMismatchException
import com.demo.chat.domain.command.SubmitterUnavailableException
import com.demo.chat.test.service.composite.command.MessagingStack.Companion.ROOM
import com.demo.chat.test.service.composite.command.MessagingStack.Companion.SENDER
import com.demo.chat.domain.Key
import com.demo.chat.domain.MapRequestConverters
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.command.SubmitterIdentity
import com.demo.chat.service.composite.impl.MessagingServiceImpl
import com.demo.chat.service.core.KeyVerifier
import reactor.core.publisher.Mono
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration

class MessageCommandServiceTests {
    private val stacks = mutableListOf<MessagingStack>()
    private fun stack(
        requirement: CompletionRequirement = CompletionRequirement.parse("P,I"),
        timeout: Duration = Duration.ofSeconds(5),
        useSubmitter: Boolean = true,
    ) = MessagingStack(requirement, timeout, useSubmitter = useSubmitter).also { stacks += it }

    @AfterEach
    fun close() = stacks.forEach { it.close() }

    private fun submit(s: MessagingStack, requestId: String, text: String = "hello") =
        s.service.submit(MessageSubmitRequest(text, ROOM, requestId))

    @Test
    fun `a submission completes P and I and stores the message under the assigned key`() {
        val s = stack()
        val result = submit(s, "r-1").block()!!
        assertThat(result.outcome).isEqualTo(CallerOutcome.COMPLETED)
        assertThat(s.persistence.added.single().key.id).isEqualTo(result.receipt.messageKey.id)
        assertThat(result.receipt.messageKey.from).isEqualTo(SENDER)
    }

    @Test
    fun `case 3 - None answers Accepted after admission and still exposes a refusal`() {
        val s = stack(CompletionRequirement.NONE)
        assertThat(submit(s, "r-none").block()!!.outcome).isEqualTo(CallerOutcome.ACCEPTED)
        assertThatThrownBy { submit(s, "r-none", "changed").block() }.isInstanceOf(RequestConflictException::class.java)
    }

    @Test
    fun `case 10 - a timeout answers Pending, and status reports completion later`() {
        val s = stack(timeout = Duration.ofMillis(200))
        s.gatePersistence = true
        val result = submit(s, "r-slow").block()!!
        assertThat(result.outcome).isEqualTo(CallerOutcome.PENDING)
        s.persistenceGate.tryEmitEmpty()
        CommandFixtures.waitUntil {
            s.service.commandStatus(CommandStatusRequest(result.receipt.commandId)).block()!!
                .backends.values.all { it.state.name == "SUCCEEDED" }
        }
    }

    @Test
    fun `case 21 - a None receipt does not imply lookup before P registers the key`() {
        val s = stack(CompletionRequirement.NONE)
        s.gatePersistence = true
        val receipt = submit(s, "r-lookup").block()!!.receipt
        assertThatThrownBy { s.service.messageById(ByIdRequest(receipt.messageKey.id)).block() }
        s.persistenceGate.tryEmitEmpty()
        CommandFixtures.waitUntil { s.registry.rootOf(receipt.messageKey.id).block() != null }
        assertThat(s.service.messageById(ByIdRequest(receipt.messageKey.id)).block()!!.data).isEqualTo("hello")
    }

    @Test
    fun `case 28 - the legacy send rejects another sender and admits nothing`() {
        val s = stack()
        assertThatThrownBy { s.service.send(MessageSendRequest("forged", 999L, ROOM)).block() }
            .isInstanceOf(SenderMismatchException::class.java)
        assertThat(s.runtime.bus.committedMappings()).isZero()
    }

    @Test
    fun `the legacy send answers a plain key on completion`() {
        val s = stack()
        val key = s.service.send(MessageSendRequest("legacy", SENDER, ROOM)).block()!!
        assertThat(key.javaClass.simpleName).isEqualTo("SimpleKey")
        assertThat(s.persistence.added.single().key.id).isEqualTo(key.id)
    }

    @Test
    fun `the legacy send fails with the command ID when its wait ends`() {
        val s = stack(timeout = Duration.ofMillis(200))
        s.gatePersistence = true
        assertThatThrownBy { s.service.send(MessageSendRequest("slow", SENDER, ROOM)).block() }
            .isInstanceOf(CommandPendingException::class.java)
    }

    @Test
    fun `a composition without a submitter refuses submission`() {
        val s = stack(useSubmitter = false)
        assertThatThrownBy { submit(s, "r-nobody").block() }.isInstanceOf(SubmitterUnavailableException::class.java)
    }

    private fun serviceAs(s: MessagingStack, submitter: SubmitterIdentity<Long>) = MessagingServiceImpl(
        s.index, s.persistence, s.publications, MapRequestConverters()::topicIdToQuery,
        KeyVerifier(s.registry, s.roots), s.runtime.bus, s.runtime.completions, submitter,
        CompletionRequirement.parse("P,I"), Duration.ofSeconds(1),
    )

    @Test
    fun `no identity is refused`() {
        val s = stack()
        val nobody = object : SubmitterIdentity<Long> {
            override fun current(): Mono<Key<Long>> = Mono.empty()
        }
        assertThatThrownBy { serviceAs(s, nobody).submit(MessageSubmitRequest("x", ROOM, "r-empty")).block() }
            .isSameAs(AccessDeniedException)
    }

    @Test
    fun `review focus 5 - status of another owner's command is not found`() {
        val s = stack()
        val result = submit(s, "r-owned").block()!!
        val stranger = s.registry.register(77L, ChatDomain.USER)
        val asStranger = object : SubmitterIdentity<Long> {
            override fun current(): Mono<Key<Long>> = Mono.just(stranger)
        }
        assertThat(s.service.commandStatus(CommandStatusRequest(result.receipt.commandId)).block()).isNotNull
        assertThatThrownBy { serviceAs(s, asStranger).commandStatus(CommandStatusRequest(result.receipt.commandId)).block() }
            .isSameAs(NotFoundException)
    }

    @Test
    fun `review 1 - an anonymous caller, which has no submitter, submits nothing, sends nothing, and reads no status`() {
        val s = stack()
        val owned = submit(s, "r-owned-by-user").block()!!
        val anonymous = serviceAs(s, object : SubmitterIdentity<Long> {
            override fun current(): Mono<Key<Long>> = Mono.empty()
        })
        val before = s.runtime.bus.committedMappings()
        assertThatThrownBy { anonymous.submit(MessageSubmitRequest("hi", ROOM, "shared-1")).block() }.isSameAs(AccessDeniedException)
        assertThatThrownBy { anonymous.send(MessageSendRequest("hi", CommandFixtures.ROOTS.anon().id, ROOM)).block() }
            .isSameAs(AccessDeniedException)
        assertThatThrownBy { anonymous.commandStatus(CommandStatusRequest(owned.receipt.commandId)).block() }
            .isSameAs(AccessDeniedException)
        assertThat(s.runtime.bus.committedMappings()).isEqualTo(before)
    }

    @Test
    fun `case 15 - a message published before I indexes reaches a listener that starts between`() {
        val s = stack(CompletionRequirement.NONE)
        s.gateIndex = true
        val receipt = submit(s, "r-gap").block()!!.receipt
        CommandFixtures.waitUntil { s.publications.publicationCount(ROOM) == 1 }
        val heard = s.service.listenTopic(ByIdRequest(ROOM)).take(Duration.ofMillis(500)).collectList().block()!!
        assertThat(heard.map { it.key.id }).containsExactly(receipt.messageKey.id)
        s.indexGate.tryEmitEmpty()
    }

    @Test
    fun `case 16 - a message in the history and the live stream reaches the listener once`() {
        val s = stack(CompletionRequirement.NONE)
        s.gatePubsub = true
        val receipt = submit(s, "r-both").block()!!.receipt
        CommandFixtures.waitUntil { s.index.added.isNotEmpty() && s.persistence.added.isNotEmpty() }
        val listening = s.service.listenTopic(ByIdRequest(ROOM)).take(Duration.ofSeconds(1)).collectList().toFuture()
        Thread.sleep(200)
        s.pubsubGate.tryEmitEmpty()
        val heard = listening.get()
        assertThat(heard.map { it.key.id }).containsExactly(receipt.messageKey.id)
    }

    @Test
    fun `review focus 3 - a cancel during the history read disposes the live subscription`() {
        val s = stack()
        val subscription = s.service.listenTopic(ByIdRequest(ROOM)).subscribe()
        CommandFixtures.waitUntil { s.pubsub.subscribers.get() == 1 }
        subscription.dispose()
        CommandFixtures.waitUntil { s.pubsub.subscribers.get() == 0 }
    }
}
```

The tests `no identity is refused` and `review focus 5` build a second service over the same runtime, with another submitter. The stack submitter always answers the stack user.

**Step 3: Run the test to verify that it fails**

Run: `mvn -o -B -pl chat-core,chat-messaging-memory,chat-service-composite test -Dtest=MessageCommandServiceTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t16.log 2>&1; echo "exit $?"`
Expected: `exit 1`. The compiler reports that `submit` and the new constructor parameters do not exist.

**Step 4: Extend the service contract**

In `ChatMessageService`, add:

```kotlin
    /** Admits a message command and waits for the configured requirement. Decision 10 of the spec. */
    fun submit(req: MessageSubmitRequest<T, V>): Mono<out MessageSendResult<T>>

    /** The status of a command that the caller owns. Another owner's command answers not found. */
    fun commandStatus(req: CommandStatusRequest): Mono<out CommandStatus<T>>
```

In `MessagingClient`, add:

```kotlin
    override fun submit(req: MessageSubmitRequest<T, V>): Mono<out MessageSendResult<T>> =
            requester
                    .route("${prefix}message-submit")
                    .data(req)
                    .retrieveMono()

    override fun commandStatus(req: CommandStatusRequest): Mono<out CommandStatus<T>> =
            requester
                    .route("${prefix}message-command-status")
                    .data(req)
                    .retrieveMono()
```

In `MessagingServiceAccess`, add:

```kotlin
    override fun submit(req: MessageSubmitRequest<T, V>): Mono<out MessageSendResult<T>> = verifier.resolve(req.dest, ChatDomain.MESSAGE_TOPIC)
        .flatMap { authMetadataAccessBroker.hasAccessByPrincipal(Mono.from(principalPublisher()), it, "SEND") }
        .then(that.submit(req))

    override fun commandStatus(req: CommandStatusRequest): Mono<out CommandStatus<T>> = that.commandStatus(req)
```

**Step 5: Rewrite `MessagingServiceImpl`**

Replace the class body with:

```kotlin
open class MessagingServiceImpl<T : Any, V, Q>(
    private val messageIndex: MessageIndexService<T, V, Q>,
    private val messagePersistence: MessagePersistence<T, V>,
    private val publications: RoomPublications<T, V>,
    private val topicIdToQuery: Function<ByIdRequest<T>, Q>,
    private val verifier: KeyVerifier<T>,
    private val commandBus: DomainCommandBus<T, V>,
    private val completions: CommandCompletionService<T>,
    private val submitter: SubmitterIdentity<T>?,
    private val requirement: CompletionRequirement,
    private val timeout: Duration,
    private val requestIds: () -> String = { UUID.randomUUID().toString() },
) : ChatMessageService<T, V> {

    /**
     * History, then replay, then live. Decisions 8 and 9 of the spec. The
     * boundary task subscribes first. The listener drops each message ID that
     * it already emitted, for the whole subscription.
     */
    override fun listenTopic(req: ByIdRequest<T>): Flux<out Message<T, V>> =
        verifier.resolve(req.id, ChatDomain.MESSAGE_TOPIC).flatMapMany {
            publications.subscribe(req.id).flatMapMany { live ->
                history(req).collectList()
                    .flatMapMany { stored ->
                        val seen = ConcurrentHashMap.newKeySet<T>()
                        val merged = (stored + live.replay).sortedBy { it.key.timestamp }
                        Flux.concat(Flux.fromIterable(merged), live.messages).filter { seen.add(it.key.id) }
                    }
                    .doFinally { live.close() }
            }
        }

    override fun listMessages(req: ByIdRequest<T>): Flux<out Message<T, V>> =
        verifier.resolve(req.id, ChatDomain.MESSAGE_TOPIC).flatMapMany { history(req) }

    private fun history(req: ByIdRequest<T>): Flux<out Message<T, V>> =
        messageIndex
            .findBy(topicIdToQuery.apply(req))
            .collectList()
            .flatMapMany { messageKeys -> messagePersistence.byIds(messageKeys) }

    override fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>> =
        verifier.resolve(req.id, ChatDomain.MESSAGE)
            .flatMap { messagePersistence.get(it.key) }

    override fun submit(req: MessageSubmitRequest<T, V>): Mono<out MessageSendResult<T>> =
        owner().flatMap { owner -> admitAndWait(owner, req.requestId, req.dest, req.msg) }

    /**
     * The legacy adapter. It gives no retry safety, because each call creates a
     * new request ID. It rejects a `from` that is not the authenticated user.
     */
    override fun send(req: MessageSendRequest<T, V>): Mono<out Key<T>> =
        owner().flatMap { owner ->
            if (owner.id != req.from) Mono.error(SenderMismatchException(req.from, owner.id))
            else admitAndWait(owner, requestIds(), req.dest, req.msg)
        }.flatMap { result ->
            when (result.outcome) {
                CallerOutcome.COMPLETED, CallerOutcome.ACCEPTED ->
                    Mono.just(Key.of(result.receipt.messageKey.id, result.receipt.messageKey.root))
                CallerOutcome.PENDING -> Mono.error(CommandPendingException(result.receipt.commandId))
                CallerOutcome.INCOMPLETE -> Mono.error(
                    CommandIncompleteException(
                        result.receipt.commandId,
                        result.backends.filter { it.key in requirement.backends && it.value.state == BackendState.FAILED }.keys,
                    )
                )
            }
        }

    override fun commandStatus(req: CommandStatusRequest): Mono<out CommandStatus<T>> =
        owner().flatMap { owner -> completions.status(req.commandId).filter { it.owner == owner.id } }
            .switchIfEmpty(Mono.error(NotFoundException))

    private fun owner(): Mono<Key<T>> =
        (submitter?.current() ?: Mono.error(SubmitterUnavailableException()))
            .switchIfEmpty(Mono.error(AccessDeniedException))

    private fun admitAndWait(owner: Key<T>, requestId: String, dest: T, content: V): Mono<MessageSendResult<T>> =
        verifier.resolve(owner.id, ChatDomain.USER)
            .then(verifier.resolve(dest, ChatDomain.MESSAGE_TOPIC))
            .then(Mono.defer { commandBus.submit(CommandSubmission(owner.id, requestId, owner.id, dest, content)) })
            .flatMap { receipt ->
                if (requirement.backends.isEmpty()) Mono.just(MessageSendResult(receipt, CallerOutcome.ACCEPTED, emptyMap()))
                else completions.await(receipt.commandId, requirement, timeout)
            }
}
```

Update the imports. The vector indexer and `asText` leave this class, because the `V` handler owns them now.

**Step 6: Wire the beans**

`MessageCommandConfiguration.kt`:

```kotlin
package com.demo.chat.config.service.composite.command

import com.demo.chat.config.CommandBusSettings
import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.composite.command.handler.MessageIndexHandler
import com.demo.chat.service.composite.command.handler.MessagePersistenceHandler
import com.demo.chat.service.composite.command.handler.MessagePubSubHandler
import com.demo.chat.service.composite.command.handler.MessageVectorHandler
import com.demo.chat.service.composite.command.memory.MemoryCommandRuntime
import com.demo.chat.service.composite.command.publication.RoomPublications
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.KeyAllocator
import com.demo.chat.service.vector.MessageVectorIndexer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

/**
 * The Stage 1 command beans. `CommandBusValidation` admits only
 * `app.command.bus=memory`, so this configuration builds the memory runtime.
 * An inactive vector provider adds no `V` handler and no `V` obligation.
 */
@Configuration
@ConditionalOnProperty("app.service.composite")
class MessageCommandConfiguration<T : Any, V, Q>(
    private val persistenceBeans: PersistenceServiceBeans<T, V>,
    private val indexBeans: IndexServiceBeans<T, V, Q>,
    private val pubsub: PubSubServiceBeans<T, V>,
    private val keyService: IKeyService<T>,
    private val keyGenerator: IKeyGenerator<T>,
    private val rootKeys: RootKeys<T>,
    private val typeUtil: TypeUtil<T>,
    private val vectorIndexers: ObjectProvider<MessageVectorIndexer<T>>,
    private val environment: Environment,
) {
    @Bean
    fun roomPublications(): RoomPublications<T, V> = RoomPublications(pubsub.pubSubService())

    @Bean
    fun commandBusSettings(): CommandBusSettings = CommandBusSettings.from(environment)

    @Bean(destroyMethod = "close")
    fun memoryCommandRuntime(publications: RoomPublications<T, V>, settings: CommandBusSettings): MemoryCommandRuntime<T, V> =
        MemoryCommandRuntime(KeyAllocator(keyGenerator, rootKeys), typeUtil, handlers(publications), settings)

    private fun handlers(publications: RoomPublications<T, V>): List<DomainCommandHandler<T, V>> = listOfNotNull(
        MessagePersistenceHandler(keyService, persistenceBeans.messagePersistence()),
        MessageIndexHandler(indexBeans.messageIndex()),
        vectorIndexers.ifAvailable?.let { MessageVectorHandler<T, V>(it) },
        MessagePubSubHandler(publications),
    )
}
```

Run `grep -n "^package\|^interface\|^class" chat-core/src/main/kotlin/com/demo/chat/config/*Beans*.kt` and correct the three `*ServiceBeans` import packages if they differ.

In `CompositeServiceBeansConfiguration`, remove the `vectorIndexers` parameter. Add these constructor parameters:

```kotlin
    private val commandRuntime: MemoryCommandRuntime<T, V>,
    private val publications: RoomPublications<T, V>,
    private val commandSettings: CommandBusSettings,
    private val submitters: ObjectProvider<SubmitterIdentity<T>>,
```

Replace the `messageService()` bean:

```kotlin
    @Bean
    override fun messageService() = MessagingServiceImpl(
        messageIndex = indexBeans.messageIndex(),
        messagePersistence = persistenceBeans.messagePersistence(),
        publications = publications,
        topicIdToQuery = queryConverters::topicIdToQuery,
        verifier = verifier,
        commandBus = commandRuntime.bus,
        completions = commandRuntime.completions,
        submitter = submitters.ifAvailable,
        requirement = commandSettings.requirement,
        timeout = commandSettings.timeout,
    )
```

**Step 7: Update the existing service tests**

1. `MessagingServiceHistoryTests`: replace the `service` field with a `MessagingStack`. Store messages with `stack.persistence.add(...)` and `stack.index.add(...)`, and use room `MessagingStack.ROOM` in place of `100L`. Each test keeps its assertion.
2. `MessagingServiceVectorTests`: delete the tests named `send calls persistence then index then vector then pubsub`, `inactive chain stays three steps when there is no indexer`, and `vector failure stops pubsub and fails send`. The spec removes that sequence. Keep the indexer tests. Port `send from an unknown sender mints nothing and writes nothing` and `send to a destination outside MESSAGE_TOPIC mints nothing and writes nothing` to `MessagingStack`, and assert `runtime.bus.committedMappings() == 0` in place of the mint count.
3. Add to `MessageCommandServiceTests`:

```kotlin
    @Test
    fun `a vector failure does not stop U or the P and I requirement`() {
        val s = stack()
        val vector = ScriptedHandler(com.demo.chat.domain.command.BackendId.VECTOR) { _, _ ->
            reactor.core.publisher.Mono.error(com.demo.chat.domain.ChatException("vector down"))
        }
        assertThat(vector.descriptor.backend.letter).isEqualTo("V")
        val result = submit(s, "r-vector").block()!!
        assertThat(result.outcome).isEqualTo(CallerOutcome.COMPLETED)
        CommandFixtures.waitUntil { s.publications.publicationCount(ROOM) == 1 }
    }
```

4. `MessageControllerTests` in `chat-client-rsocket` builds the service at lines 150 to 160. It exercises `message-by-id` and `message-listen-topic` only. Replace the bean with:

```kotlin
        @Bean
        @Suppress("UNCHECKED_CAST")
        fun testMessagingServiceImpl(
            messageIdx: MessageIndexService<UUID, String, Map<String, String>>,
            msgPersist: MessagePersistence<UUID, String>,
            messaging: TopicPubSubService<UUID, String>,
        ) = MessagingServiceImpl<UUID, String, Map<String, String>>(
            messageIdx,
            msgPersist,
            RoomPublications(messaging),
            Function { i -> mapOf(Pair(MessageIndexService.TOPIC, i.id.toString())) },
            RSocketTestRegistry.verifier,
            Mockito.mock(DomainCommandBus::class.java) as DomainCommandBus<UUID, String>,
            Mockito.mock(CommandCompletionService::class.java) as CommandCompletionService<UUID>,
            null,
            CompletionRequirement.parse("P,I"),
            Duration.ofSeconds(5),
        )
```

Add the imports for `RoomPublications`, `DomainCommandBus`, `CommandCompletionService`, `CompletionRequirement`, `Mockito`, and `java.time.Duration`. The listen test feeds messages through the mocked `listenTo` and the mocked history. The listener now emits each message ID once. If the test feeds one ID to both, change its expected count to the number of distinct IDs.

**Step 8: Run the module tests**

Run: `mvn -o -B -pl chat-core,chat-security,chat-messaging-memory,chat-service-composite,chat-client-rsocket test > /tmp/t16.log 2>&1; echo "exit $?"; grep -E "Tests run:.*Fail" /tmp/t16.log | tail -6`
Expected: `exit 0`, with 0 failures in every listed module. `MessageCommandServiceTests` reports 15 tests.

**Step 9: Repair the deployment tests that this change breaks**

This task changes two behaviors that deployment tests read. Repair each test here, in this task.

1. Sender binding refuses a composite send with no security context. `StoreWritePathTests` and `RedisGrantRestartTests` call `composite.messageService().send(...)` directly. Add this helper to each class:

```kotlin
    private fun <R> asUser(userKey: Key<Long>, handle: String, call: Mono<R>): Mono<R> =
        call.contextWrite(
            ReactiveSecurityContextHolder.withAuthentication(
                UsernamePasswordAuthenticationToken(
                    ChatUserDetails(User.create(userKey, handle, handle, "http://$handle"), listOf()), "n/a", listOf(),
                )
            )
        )
```

Then call `asUser(Key.of(user.id, user.root), "e1sender", composite.messageService().send(MessageSendRequest("hello", user.id, room.id))).block(timeout)`. Use the real handle of the user that each test creates.

2. A send no longer waits for `V`. A test that reads a recall right after a send must wait for `V`. Add `"app.command.completion.requirement=P,I,V"` to the properties of each class that sets `app.service.core.vector` and sends. Find them with `grep -l 'app.service.core.vector' chat-deploy-*/src/test/kotlin/com/demo/chat/test/deploy/*/*.kt`, then read each one for a send.

**Step 10: Run the full reactor**

Nothing registers the startup check yet, so no composition needs the selector in this task.

Run: `DOCKER_CONFIG=$(mktemp -d) shell-scripts/build-health.sh --ci > /tmp/t16-ci.log 2>&1; echo "exit $?"; tail -8 /tmp/t16-ci.log`
Expected: `exit 0` and `reality matches docs/BUILD-HEALTH.md`. The image ID must differ from the Task 0 baseline. A failure that reads `SubmitterUnavailableException`, `Access Denied`, or `SenderMismatchException` names another direct composite send. Repair it as in Step 9, item 1, and run again. A shell container test that sends with no login now reads `Access Denied`, because `Anon` owns no submission. Log that test in as `LongUserCommandsTests` does, through `ShellDeploymentAccount`.

**Step 11: Run three mutation checks**

Run Step 8 after each change, then restore by absolute path:

1. In `listenTopic`, remove `.filter { seen.add(it.key.id) }`. Expected: `case 16` fails.
2. In `listenTopic`, use `stored` in place of `stored + live.replay`. Expected: `case 15` fails.
3. In `send`, remove the `owner.id != req.from` check. Expected: `case 28` fails.

**Step 12: Commit**

```bash
git add chat-core/src chat-service-composite/src chat-client-rsocket/src chat-deploy-memory/src/test chat-deploy-redis/src/test
git commit -m "Switch the message service to captured commands (<task-16-issue>)"
```

---

### Task 17: Selector, startup validation, and the boot proof

**Files:**
- Modify: `chat-core/src/main/kotlin/com/demo/chat/config/CommandBusValidation.kt` (add the configuration class)
- Modify: `shell-scripts/chat-build` (`CORE_SERVICE_FLAGS`)
- Modify: `shell-scripts/golden/*.flags` through `test-flags.sh --update`
- Modify: `chat-deploy-memory-integration-test/pom.xml:79`
- Modify: every test context that sets `app.service.composite`. The list is in Step 3.
- Create: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/CommandBusStartupTests.kt`
- Create: `chat-core/src/test/kotlin/com/demo/chat/test/command/SafeRepeatContractTests.kt`
- Create: `chat-core/src/test/kotlin/com/demo/chat/test/command/CompositionSafeRepeatBase.kt`
- Create: eleven `*SafeRepeatTests` classes. Step 6 lists them by module.
- Create: `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisSafeRepeatLaunch.kt`
- Create: `chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraSafeRepeatLaunch.kt`
- Modify: `docs/NODEID-CLAIM.md` (node ids 40, 41, 43, and 44)

**Interfaces:**
- Consumes: everything above.
- Produces: a startup that refuses an invalid command configuration, and the selector in every launch path.

**Step 1: Register the validation**

Append to `chat-core/src/main/kotlin/com/demo/chat/config/CommandBusValidation.kt`:

```kotlin
// The module does not enable the Kotlin all-open compiler plugin, so this class is open.
@Configuration
open class CommandBusValidationConfiguration {
    companion object {
        /** Static, as Spring requires of a `BeanFactoryPostProcessor` bean. */
        @Bean
        @JvmStatic
        fun commandBusValidation(environment: Environment): BeanFactoryPostProcessor =
            CommandBusValidationPostProcessor(environment)
    }
}
```

Add the imports `org.springframework.context.annotation.Bean` and `org.springframework.context.annotation.Configuration`. `ChatApp` scans `com.demo.chat.config`, so the check now runs in every composition that sets `app.service.composite`. Steps 2 and 3 set the selector everywhere before the next build.

**Step 2: Emit the selector from `chat-build`**

In `shell-scripts/chat-build`, add `"-Dapp.command.bus=memory",` after `"-Dapp.service.composite.auth=true",` in `CORE_SERVICE_FLAGS`. In `chat-deploy-memory-integration-test/pom.xml` line 79, add ` -Dapp.command.bus=memory` after `-Dapp.service.composite.auth=true`.

Run:

```bash
shell-scripts/test-flags.sh --update > /tmp/t17-flags.log 2>&1; echo "update exit $?"
git diff --stat shell-scripts/golden
shell-scripts/test-flags.sh > /tmp/t17-flags.log 2>&1; echo "exit $?"; tail -2 /tmp/t17-flags.log
```

Expected: the diff adds one line to each golden file of a core launch, and nothing else. The second run exits 0.

**Step 3: Add the selector to each test context**

Add `"app.command.bus=memory"` beside `"app.service.composite"` in each of these files:

```text
chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraAuthorizationMatrixTests.kt
chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraClaimBootTests.kt
chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraDeployTest.kt
chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraGrantRestartTests.kt
chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraMessageSendTests.kt
chat-deploy-kafka/src/test/kotlin/com/demo/chat/test/deploy/kafka/KafkaDeploymentTests.kt
chat-deploy-memory-integration-test/src/test/kotlin/com/demo/chat/deploy/test/security/RestToCoreBearerDeploymentTests.kt
chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/*.kt (every file that sets app.service.composite)
chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/*.kt (every file that sets app.service.composite)
chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/DeployTests.kt
chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/VectorIndexEndpointTests.kt
chat-service-composite/src/test/kotlin/com/demo/chat/test/config/VectorRecallServiceConfigurationTests.kt
chat-client-rsocket/src/test/resources/appRsocketTestProperties.yaml
chat-service-controller/src/test/resources/appRsocketTestProperties.yaml
```

A launch argument list uses `"--app.command.bus=memory"`. A YAML file uses `app: command: bus: memory` in its existing tree. Then run this check, which must print nothing:

```bash
for f in $(grep -rlE 'app\.service\.composite' --include='*.kt' --include='*.yaml' --include='*.yml' . | grep '/src/test/' | grep -v '/.worktrees/'); do grep -q 'app.command.bus\|command:' "$f" || echo "MISSING $f"; done
```

**Step 4: Write the startup test**

`CommandBusStartupTests.kt`:

```kotlin
package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder

/** Cases 23, 24, and 27 of the spec, at startup of a real composition. */
class CommandBusStartupTests {
    private fun launch(vararg extra: String) = SpringApplicationBuilder(ChatApp::class.java).run(
        "--spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "--spring.application.name=command-bus-startup", "--app.server.proto=rsocket", "--server.port=0",
        "--spring.rsocket.server.port=0", "--app.key.type=long", "--app.nodeid=1",
        "--app.service.core.key=memory", "--app.service.core.pubsub=memory", "--app.service.core.index=lucene",
        "--app.service.core.persistence=memory", "--app.service.core.secrets=memory",
        "--app.service.composite", "--app.service.composite.auth=true", "--app.controller.message",
        "--app.service.security.userdetails", "--app.users.create=true", *extra,
    )

    private fun refusal(vararg extra: String): String {
        val thrown = catchThrowable { launch(*extra).close() }
        assertThat(thrown).describedAs("expected a refused startup").isNotNull()
        return generateSequence(thrown) { it.cause }.joinToString(" | ") { it.message ?: "" }
    }

    @Test
    fun `case 23 - an unset bus refuses startup`() {
        assertThat(refusal()).contains("app.command.bus is not set")
    }

    @Test
    fun `case 23 - kafka refuses startup and names Stage 2`() {
        assertThat(refusal("--app.command.bus=kafka")).contains("Stage 2")
    }

    @Test
    fun `case 13 - a finite ttl refuses startup`() {
        assertThat(refusal("--app.command.bus=memory", "--app.command.ttl=10m")).contains("app.command.ttl=10m")
    }

    @Test
    fun `case 24 - a declared replica count above one refuses startup`() {
        assertThat(refusal("--app.command.bus=memory", "--app.command.replicas=2")).contains("does not detect other processes")
    }

    @Test
    fun `case 27 - memory with no vector provider starts`() {
        launch("--app.command.bus=memory").use { context ->
            assertThat(context.isActive).isTrue()
        }
    }
}
```

**Step 5: Write the shared safe-repeat contract**

Each supported provider in `CommandBusValidation.SUPPORTED_PROVIDERS` needs one subclass of this contract. Startup rejects every other provider value.

`chat-core/src/test/kotlin/com/demo/chat/test/command/SafeRepeatContractTests.kt`:

```kotlin
package com.demo.chat.test.command

import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.domain.command.UncertainOutcomeException
import com.demo.chat.service.command.DomainCommandHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration

/**
 * The safe-repeat contract of one handler over one real provider. Decision 12
 * of the spec, and review 7 of the plan. The runtime never overlaps two
 * attempts. A backend can still apply an earlier effect late, so the contract
 * also covers overlapping and late executions.
 */
abstract class SafeRepeatContractTests {
    protected val wait: Duration = Duration.ofSeconds(10)

    /** The handler under test, over the provider of the running composition. */
    abstract fun handler(): DomainCommandHandler<Long, String>

    /** A new command. Each call gives a new message key in one room. */
    abstract fun newCommand(text: String): AcceptedCommand<Long, String>

    /** The number of observable results of [command] in the provider. */
    abstract fun results(command: AcceptedCommand<Long, String>): Int

    /** The time an asynchronous provider needs to show its effects. */
    open val settle: Duration = Duration.ofMillis(500)

    private fun settled(command: AcceptedCommand<Long, String>): Int {
        Thread.sleep(settle.toMillis())
        return results(command)
    }

    @Test
    fun `repeated execution gives one result`() {
        val command = newCommand("repeat")
        handler().handle(command).block(wait)
        handler().handle(command).block(wait)
        assertThat(settled(command)).isEqualTo(1)
    }

    @Test
    fun `overlapping executions give one result`() {
        val command = newCommand("overlap")
        Mono.`when`(
            handler().handle(command).subscribeOn(Schedulers.boundedElastic()),
            handler().handle(command).subscribeOn(Schedulers.boundedElastic()),
        ).block(wait)
        assertThat(settled(command)).isEqualTo(1)
    }

    @Test
    fun `an earlier command that lands after a later one leaves both results`() {
        val earlier = newCommand("earlier")
        val later = newCommand("later")
        handler().handle(later).block(wait)
        handler().handle(earlier).block(wait)
        Thread.sleep(settle.toMillis())
        assertThat(results(earlier)).isEqualTo(1)
        assertThat(results(later)).isEqualTo(1)
    }

    @Test
    fun `a failure after the effect, then recovery with an unchanged command, gives one result`() {
        val command = newCommand("recover")
        handler().handle(command)
            .then(Mono.error<Void>(UncertainOutcomeException("injected after the effect")))
            .onErrorResume(UncertainOutcomeException::class.java) { Mono.empty() }
            .block(wait)
        handler().handle(command.copy()).block(wait)
        assertThat(settled(command)).isEqualTo(1)
    }
}
```

`chat-core/src/test/kotlin/com/demo/chat/test/command/CompositionSafeRepeatBase.kt`:

```kotlin
package com.demo.chat.test.command

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.RequestToQueryConverters
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CommandOperation
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.KeyAllocator
import org.junit.jupiter.api.TestInstance
import org.springframework.context.ApplicationContext
import java.time.Instant
import java.util.UUID

/**
 * Builds commands in one room of a running composition. Each subclass names
 * one provider, and it supplies the running context through [context].
 *
 * The base reads every bean from that context. It uses no field injection,
 * because two of the source fixtures build their context with
 * `SpringApplicationBuilder`, where no injection runs. Each lookup names a
 * holder type with one bean. `IKeyService` itself is ambiguous, because
 * `KeyServiceController` implements it too.
 *
 * One test instance serves the whole class, so the class uses one room and
 * a builder subclass starts one context.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class CompositionSafeRepeatBase : SafeRepeatContractTests() {
    /** The running composition. A subclass injects it or builds it. */
    abstract fun context(): ApplicationContext

    /** Letters only. The Lucene name index splits a name on a hyphen. See `CHAT-hajmhslp`. */
    abstract val roomName: String

    @Suppress("UNCHECKED_CAST")
    protected val keyService: IKeyService<Long>
        get() = (context().getBean(KeyServiceBeans::class.java) as KeyServiceBeans<Long>).keyService()

    @Suppress("UNCHECKED_CAST")
    protected val persistenceBeans: PersistenceServiceBeans<Long, String>
        get() = context().getBean(PersistenceServiceBeans::class.java) as PersistenceServiceBeans<Long, String>

    @Suppress("UNCHECKED_CAST")
    protected val indexBeans: IndexServiceBeans<Long, String, Any>
        get() = context().getBean(IndexServiceBeans::class.java) as IndexServiceBeans<Long, String, Any>

    @Suppress("UNCHECKED_CAST")
    protected val converters: RequestToQueryConverters<Any>
        get() = context().getBean(RequestToQueryConverters::class.java) as RequestToQueryConverters<Any>

    @Suppress("UNCHECKED_CAST")
    protected val pubsubBeans: PubSubServiceBeans<Long, String>
        get() = context().getBean(PubSubServiceBeans::class.java) as PubSubServiceBeans<Long, String>

    @Suppress("UNCHECKED_CAST")
    private val keyGenerator: IKeyGenerator<Long>
        get() = context().getBean(IKeyGenerator::class.java) as IKeyGenerator<Long>

    @Suppress("UNCHECKED_CAST")
    private val rootKeys: RootKeys<Long>
        get() = context().getBean(RootKeys::class.java) as RootKeys<Long>

    @Suppress("UNCHECKED_CAST")
    private val composite: CompositeServiceBeans<Long, String>
        get() = context().getBean(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>

    protected val room: Long by lazy { composite.topicService().addRoom(ByStringRequest(roomName)).block(wait)!!.id }

    override fun newCommand(text: String): AcceptedCommand<Long, String> {
        val key = KeyAllocator(keyGenerator, rootKeys).allocate(ChatDomain.MESSAGE)
        val admin = rootKeys.admin().id
        val messageKey = SimpleMessageKey(key.id, key.root, admin, room, Instant.now())
        return AcceptedCommand(
            UUID.randomUUID().toString(), admin, "sr-${UUID.randomUUID()}", key.root,
            CommandOperation.RECORD_MESSAGE, room, 1, Message.create(messageKey, "$text ${key.id}", true),
            BackendId.entries.toSet(), 1,
        )
    }
}
```

If `context().getBean` reports more than one bean for a holder type, that composition defines a second holder. Name the bean with `context().getBean("<name>", Type::class.java)`, and record the name in the subclass.

**Step 6: Write one subclass per supported provider**

Two context sources exist, and each subclass uses one of them.

**Injected context.** The source fixture is a `@SpringBootTest` class. The subclass copies its class annotations and its property list, adds `app.command.bus=memory`, and adds `@DirtiesContext`. `@DirtiesContext` closes the context after the class, so a cached context never outlives its room. The subclass reads the context through injection:

```kotlin
    @Autowired
    lateinit var applicationContext: ApplicationContext

    override fun context(): ApplicationContext = applicationContext
```

A source fixture that holds its container in a companion object, such as `RedisVectorRecallBootTests.redisStack`, keeps it there. The subclass adds its own companion with a `@DynamicPropertySource` method. That method reads the container of the source fixture by its qualified name, for example `RedisVectorRecallBootTests.redisStack`. It copies the registrations of the source fixture, so no second container starts.

**Built context.** The source fixture calls `SpringApplicationBuilder`. The subclass starts one context in `@BeforeAll` and closes it in `@AfterAll`. `CompositionSafeRepeatBase` sets `PER_CLASS`, so these methods need no companion object. The launch arguments pass as command-line arguments. `SpringApplicationBuilder.properties()` is the lowest precedence source, and `application.yml` would override a container address there.

Create `chat-deploy-redis/src/test/kotlin/com/demo/chat/test/deploy/redis/RedisSafeRepeatLaunch.kt`. It holds the launch surface of `RedisGrantRestartTests`, with the selector added:

```kotlin
package com.demo.chat.test.deploy.redis

import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext

/** The launch surface of `RedisGrantRestartTests`, for the Redis safe-repeat classes. */
object RedisSafeRepeatLaunch {
    fun start(name: String, nodeId: Int): ConfigurableApplicationContext {
        val container = RedisDeployBootTests.redis
        val args = listOf(
            "spring.application.name=$name",
            "spring.config.additional-location=classpath:/config/userinit.yml",
            "server.port=0",
            "spring.rsocket.server.port=0",
            "app.server.proto=rsocket",
            "app.key.type=long",
            "app.nodeid=$nodeId",
            "app.users.create=true",
            "app.service.core.key=redis",
            "app.service.core.persistence=redis",
            "app.service.core.pubsub=redis-pubsub",
            "app.service.core.index=lucene",
            "app.service.core.secrets=memory",
            "app.service.composite",
            "app.service.composite.auth=true",
            "app.command.bus=memory",
            "app.controller.persistence",
            "app.controller.index",
            "app.controller.key",
            "app.controller.pubsub",
            "app.controller.secrets",
            "app.controller.user",
            "app.controller.topic",
            "app.controller.message",
            "app.service.security.userdetails",
            "spring.cloud.consul.enabled=false",
            "spring.cloud.consul.discovery.enabled=false",
            "spring.cloud.consul.config.enabled=false",
            "redis-topics.host=${container.containerIpAddress}",
            "redis-topics.port=${container.getMappedPort(6379)}",
        ).map { "--$it" }
        return SpringApplicationBuilder(RedisDeployBootTests.BootApp::class.java)
            .web(WebApplicationType.NONE)
            .run(*args.toTypedArray())
    }
}
```

Create `chat-deploy-cassandra/src/test/kotlin/com/demo/chat/test/deploy/cassandra/CassandraSafeRepeatLaunch.kt`. It holds the launch surface of `CassandraMessageSendTests`, with the selector added. `CassandraContainerBase.cassandraContainer` sits in the companion object. Its first read runs the companion `init` block, which starts the container and applies the `long` keyspace:

```kotlin
package com.demo.chat.test.deploy.cassandra

import com.demo.chat.ChatApp
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext

/** The launch surface of `CassandraMessageSendTests`, for the Cassandra safe-repeat classes. */
object CassandraSafeRepeatLaunch {
    fun start(nodeId: Int): ConfigurableApplicationContext {
        val container = CassandraContainerBase.cassandraContainer
        val args = listOf(
            "spring.config.location=classpath:/application.yml",
            "spring.config.additional-location=classpath:/config/logging.yml," +
                "classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
            "server.port=0",
            "spring.rsocket.server.port=0",
            "app.key.type=long",
            "app.nodeid=$nodeId",
            "app.users.create=true",
            "app.service.core.key=cassandra",
            "app.service.core.persistence=cassandra",
            "app.service.core.index=cassandra",
            "app.service.core.pubsub=memory",
            "app.service.core.secrets=cassandra",
            "app.service.composite",
            "app.service.composite.auth=true",
            "app.command.bus=memory",
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
            "spring.cassandra.contact-points=${container.host}",
            "spring.cassandra.port=${container.getMappedPort(9042)}",
            "spring.cassandra.username=${container.username}",
            "spring.cassandra.password=${container.password}",
        ).map { "--$it" }
        return SpringApplicationBuilder(ChatApp::class.java)
            .web(WebApplicationType.NONE)
            .run(*args.toTypedArray())
    }
}
```

Each built subclass carries this lifecycle. The Redis persistence class is the example:

```kotlin
@Tag("integration")
class RedisKeyAndPersistenceSafeRepeatTests : CompositionSafeRepeatBase() {
    private lateinit var running: ConfigurableApplicationContext

    override fun context(): ApplicationContext = running

    override val roomName = "saferepeatredispersistence"

    @BeforeAll
    fun startComposition() {
        running = RedisSafeRepeatLaunch.start("redis-safe-repeat-persistence", 40)
    }

    @AfterAll
    fun stopComposition() {
        if (!::running.isInitialized) return
        running.close()
        check(!running.isActive) { "The safe-repeat context did not close." }
    }

    // The P body below.
}
```

| Subclass | Module | Provider | Context source | Node id |
|---|---|---|---|---|
| `MemoryKeyAndPersistenceSafeRepeatTests` | `chat-deploy-memory` | key and persistence `memory` | injected, the setup of `StandardUserJoinSendTests` | none |
| `LuceneIndexSafeRepeatTests` | `chat-deploy-memory` | index `lucene` | injected, the setup of `StandardUserJoinSendTests` | none |
| `MemoryPubSubSafeRepeatTests` | `chat-deploy-memory` | pubsub `memory` | injected, the setup of `StandardUserJoinSendTests` | none |
| `SimpleVectorSafeRepeatTests` | `chat-deploy-memory` | vector `simple` | injected, the setup of `MemoryVectorRecallBootTests` | none |
| `EmbeddedVectorSafeRepeatTests` | `chat-deploy-memory` | vector `embedded` | injected, the setup of `MemoryEmbeddedVectorRecallBootTests` | none |
| `RedisKeyAndPersistenceSafeRepeatTests` | `chat-deploy-redis` | key and persistence `redis` | built, `RedisSafeRepeatLaunch.start("redis-safe-repeat-persistence", 40)` | 40 |
| `RedisPubSubSafeRepeatTests` | `chat-deploy-redis` | pubsub `redis-pubsub` | built, `RedisSafeRepeatLaunch.start("redis-safe-repeat-pubsub", 41)` | 41 |
| `RedisVectorSafeRepeatTests` | `chat-deploy-redis` | vector `redis` | injected, the setup of `RedisVectorRecallBootTests` | none, its key and persistence are `memory` |
| `CassandraKeyAndPersistenceSafeRepeatTests` | `chat-deploy-cassandra` | key and persistence `cassandra` | built, `CassandraSafeRepeatLaunch.start(43)` | 43 |
| `CassandraIndexSafeRepeatTests` | `chat-deploy-cassandra` | index `cassandra` | built, `CassandraSafeRepeatLaunch.start(44)` | 44 |
| `KafkaPubSubSafeRepeatTests` | `chat-deploy-kafka` | pubsub `kafka` | injected, the setup of `KafkaDeploymentTests`, which already carries `@DirtiesContext` | none, its key and persistence are `memory` |

Add the node ids to the allocation table in `docs/NODEID-CLAIM.md`. Each Redis, Cassandra, and Kafka class carries `@Tag("integration")`, as its source does.

A `P` subclass covers the key provider and the persistence provider of one module together. A mixed composition, such as key `memory` with persistence `redis`, runs two operations in sequence, registration and then storage. Each operation has evidence in its own matched subclass.

The class bodies follow. Each subclass also declares `override val roomName`, with letters only.

`P`, for each persistence subclass:

```kotlin
    override fun handler(): DomainCommandHandler<Long, String> =
        MessagePersistenceHandler(keyService, persistenceBeans.messagePersistence())

    override fun results(command: AcceptedCommand<Long, String>): Int {
        val key = command.message.key
        if (keyService.rootOf(key.id).block(wait) != key.root) return 0
        return persistenceBeans.messagePersistence().byIds(listOf(key)).count().block(wait)!!.toInt()
    }
```

`I`, for each index subclass:

```kotlin
    override fun handler(): DomainCommandHandler<Long, String> = MessageIndexHandler(indexBeans.messageIndex())

    override fun results(command: AcceptedCommand<Long, String>): Int =
        indexBeans.messageIndex().findBy(converters.topicIdToQuery(ByIdRequest(room)))
            .filter { it.id == command.message.key.id }
            .count().block(wait)!!.toInt()
```

`U`, for each pubsub subclass. One listener lives for the whole class. Memory Pubsub keeps a sink after its last subscriber cancels, and that sink then completes at once and refuses each emit with `FAIL_CANCELLED`. So the fixture never cancels a listener between tests. It clears the received IDs before each test instead. `@BeforeEach` starts the listener on its first run, because a built subclass starts its context in `@BeforeAll`, and JUnit does not order two `@BeforeAll` methods:

```kotlin
    private val heard = CopyOnWriteArrayList<Long>()
    private var listener: Disposable? = null

    @Suppress("UNCHECKED_CAST")
    private val publications: RoomPublications<Long, String>
        get() = context().getBean(RoomPublications::class.java) as RoomPublications<Long, String>

    @BeforeEach
    fun listenOnceAndClear() {
        if (listener == null) {
            pubsubBeans.pubSubService().open(room).block(wait)
            listener = pubsubBeans.pubSubService().listenTo(room).subscribe { heard.add(it.key.id) }
            Thread.sleep(settle.toMillis())
        }
        heard.clear()
    }

    @AfterAll
    fun stopListening() {
        listener?.dispose()
    }

    override fun handler(): DomainCommandHandler<Long, String> = MessagePubSubHandler(publications)

    override fun results(command: AcceptedCommand<Long, String>): Int = heard.count { it == command.message.key.id }
```


`V`, for each vector subclass:

```kotlin
    @Suppress("UNCHECKED_CAST")
    private val indexer: MessageVectorIndexer<Long>
        get() = context().getBean(MessageVectorIndexer::class.java) as MessageVectorIndexer<Long>

    private val vectorStore: VectorStore
        get() = context().getBean(VectorStore::class.java)

    override fun handler(): DomainCommandHandler<Long, String> = MessageVectorHandler(indexer)

    override fun results(command: AcceptedCommand<Long, String>): Int =
        vectorStore.similaritySearch(SearchRequest.builder().query(command.message.data).topK(50).build())
            .count { it.id == "message:long:${command.message.key.id}" }
```

`MessageDocumentMapper.documentId` builds the id `message:<keyType>:<id>`. Every subclass uses the `long` key type.

The Redis vector subclass needs Redis Stack, as `RedisVectorRecallBootTests` does. The embedded subclass needs `--add-modules jdk.incubator.vector`. `chat-deploy-memory` already passes that flag to surefire for `MemoryEmbeddedVectorRecallBootTests`.

**Step 7: Run the evidence in the default build**

Run: `mvn -o -B clean install -DskipTests > /tmp/t17-install.log 2>&1; echo "install exit $?"; mvn -o -B -pl chat-deploy-memory test -Dtest='*SafeRepeatTests' -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t17-sr.log 2>&1; echo "exit $?"; grep "Tests run:" /tmp/t17-sr.log | tail -6`
Expected: `install exit 0`, then `exit 0`. Five classes report 4 tests each, with 0 failures. The Redis, Cassandra, and Kafka classes run in Step 9 under `--ci`.

**Step 8: Run a mutation check**

In `RoomPublications.publish`, delete `if (commandId in current.commandIds) return@onRoom Mono.just(true)`. Run the whole Step 7 sequence, the install and then the test run, so the changed class is rebuilt and the tests execute. Expected: `install exit 0`, then `MemoryPubSubSafeRepeatTests` fails `repeated execution gives one result` with 2. Restore by absolute path.

**Step 9: Run the full reactor with every provider**

Run:

```bash
DOCKER_CONFIG=$(mktemp -d) shell-scripts/build-health.sh --ci > /tmp/t17-ci.log 2>&1; echo "exit $?"; tail -8 /tmp/t17-ci.log
```

Expected: `exit 0` and `reality matches docs/BUILD-HEALTH.md`. The image ID must differ from the Task 16 run. Read `/tmp/t17-ci.log` for each `*SafeRepeatTests` class: eleven classes, 4 tests each, 0 failures. A composite test that fails startup with `no safe-repeat evidence` sets an unsupported provider. Change it to a supported value, or remove the composite from that test.

**Step 10: Commit**

```bash
git add -A shell-scripts chat-deploy-memory-integration-test/pom.xml chat-*/src/test chat-core/src docs/NODEID-CLAIM.md
git commit -m "Select the memory bus, refuse unsupported providers, and prove safe repeat (<task-17-issue>)"
```

---
### Task 18: RSocket submit and status routes

**Files:**
- Modify: `chat-service-controller/src/main/kotlin/com/demo/chat/controller/composite/mapping/MessageServiceControllerMapping.kt`
- Modify: `chat-security/src/main/kotlin/com/demo/chat/security/access/composite/MessageServiceAccess.kt`
- Test: `chat-security/src/test/kotlin/com/demo/chat/test/MessageSubmitCheckTests.kt`
- Test: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/MessageCommandRoutesTests.kt`

**Interfaces:**
- Consumes: `ChatMessageService.submit` and `commandStatus`.
- Produces: the routes `message.message-submit` and `message.message-command-status`.

**Step 1: Write the failing annotation test**

```kotlin
package com.demo.chat.test

import com.demo.chat.security.access.composite.MessageServiceAccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.access.prepost.PreAuthorize

class MessageSubmitCheckTests {
    private fun checkOf(name: String): String? =
        MessageServiceAccess::class.java.methods.single { it.name == name }.getAnnotation(PreAuthorize::class.java)?.value

    @Test
    fun `submit carries the SEND check of send`() {
        assertThat(checkOf("submit")).isEqualTo(checkOf("send"))
    }

    @Test
    fun `command status carries an explicit check, and the service checks ownership`() {
        assertThat(checkOf("commandStatus")).isEqualTo("permitAll()")
    }
}
```

**Step 2: Run the test to verify that it fails**

Run: `mvn -o -B -pl chat-core,chat-security test -Dtest=MessageSubmitCheckTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t18.log 2>&1; echo "exit $?"`
Expected: `exit 1`. `submit` carries no annotation, so the first test fails.

**Step 3: Add the checks and the routes**

In `MessageServiceAccess`, add:

```kotlin
    @PreAuthorize("@chatAccess.hasAccessToId(#req.dest, 'SEND')")
    override fun submit(req: MessageSubmitRequest<T, V>): Mono<out MessageSendResult<T>>

    /** Every caller may ask. The service answers not found for a command of another owner. */
    @PreAuthorize("permitAll()")
    override fun commandStatus(req: CommandStatusRequest): Mono<out CommandStatus<T>>
```

In `MessageServiceControllerMapping`, add:

```kotlin
    @MessageMapping("message-submit")
    override fun submit(req: MessageSubmitRequest<T, V>): Mono<out MessageSendResult<T>>
    @MessageMapping("message-command-status")
    override fun commandStatus(req: CommandStatusRequest): Mono<out CommandStatus<T>>
```

`MessageServiceController` delegates `ChatMessageService<T, V> by b.messageService()`, so it needs no change.

**Step 4: Run the annotation test to verify that it passes**

Run the Step 2 command. Expected: `exit 0` and `Tests run: 2, Failures: 0, Errors: 0`.

**Step 5: Write the transport test**

Create `MessageCommandRoutesTests.kt`. Copy the class annotations, the property list, the autowired fields, the `mapper`, and the private helpers `standardUser` and `connect` from `StandardUserJoinSendTests`. Add `"app.command.bus=memory"` to the properties and `@Autowired lateinit var runtime: MemoryCommandRuntime<Long, String>`. Then add these tests:

```kotlin
    @Test
    fun `a member submits and reads the status of its own command`() {
        val room = composite.topicService().addRoom(ByStringRequest("commandroutesroom")).block(timeout)!!
        val member = standardUser("commandmember", "commandmembersecret")
        val requester = connect("commandmember", "commandmembersecret")
        requester.route("topic.topic-join").data(MembershipRequest(member.id, room.id)).retrieveMono(Void::class.java).block(timeout)

        val result = requester.route("message.message-submit")
            .data(MessageSubmitRequest("over rsocket", room.id, "rs-1"))
            .retrieveMono(Map::class.java).block(timeout)!!
        assertThat(result["outcome"]).isEqualTo("COMPLETED")
        val commandId = (result["receipt"] as Map<*, *>)["commandId"] as String

        val status = requester.route("message.message-command-status").data(CommandStatusRequest(commandId))
            .retrieveMono(Map::class.java).block(timeout)!!
        assertThat(status["commandId"]).isEqualTo(commandId)

        val again = requester.route("message.message-submit")
            .data(MessageSubmitRequest("over rsocket", room.id, "rs-1"))
            .retrieveMono(Map::class.java).block(timeout)!!
        assertThat((again["receipt"] as Map<*, *>)["commandId"]).isEqualTo(commandId)
    }

    @Test
    fun `case 11 and review focus 5 - another user reads no status and a non-member submits nothing`() {
        val room = composite.topicService().addRoom(ByStringRequest("commandroutesdenied")).block(timeout)!!
        val owner = standardUser("commandowner", "commandownersecret")
        val ownerRequester = connect("commandowner", "commandownersecret")
        ownerRequester.route("topic.topic-join").data(MembershipRequest(owner.id, room.id)).retrieveMono(Void::class.java).block(timeout)
        val result = ownerRequester.route("message.message-submit")
            .data(MessageSubmitRequest("owned", room.id, "rs-owned"))
            .retrieveMono(Map::class.java).block(timeout)!!
        val commandId = (result["receipt"] as Map<*, *>)["commandId"] as String

        standardUser("commandstranger", "commandstrangersecret")
        val stranger = connect("commandstranger", "commandstrangersecret")
        val statusError = catchThrowable {
            stranger.route("message.message-command-status").data(CommandStatusRequest(commandId))
                .retrieveMono(Map::class.java).block(timeout)
        }
        assertThat(statusError).hasMessageContaining("Object not Found")

        val before = runtime.bus.committedMappings()
        val submitError = catchThrowable {
            stranger.route("message.message-submit").data(MessageSubmitRequest("not a member", room.id, "rs-denied"))
                .retrieveMono(Map::class.java).block(timeout)
        }
        assertThat(submitError).hasMessageContaining("Access Denied")
        assertThat(runtime.bus.committedMappings()).isEqualTo(before)
    }

    @Test
    fun `case 28 - the legacy RSocket send rejects another sender and stores nothing`() {
        val room = composite.topicService().addRoom(ByStringRequest("commandroutesforged")).block(timeout)!!
        val member = standardUser("commandforger", "commandforgersecret")
        val victim = standardUser("commandvictim", "commandvictimsecret")
        val requester = connect("commandforger", "commandforgersecret")
        requester.route("topic.topic-join").data(MembershipRequest(member.id, room.id)).retrieveMono(Void::class.java).block(timeout)

        val before = runtime.bus.committedMappings()
        val error = catchThrowable {
            requester.route("message.message-send").data(MessageSendRequest("forged", victim.id, room.id))
                .retrieveMono(Map::class.java).block(timeout)
        }
        assertThat(error).hasMessageContaining("is not the authenticated user")
        assertThat(runtime.bus.committedMappings()).isEqualTo(before)
    }
```

**Step 6: Run the transport test**

Run: `mvn -o -B clean install -DskipTests > /tmp/t18-install.log 2>&1; echo "install exit $?"; mvn -o -B -pl chat-deploy-memory test -Dtest=MessageCommandRoutesTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t18.log 2>&1; echo "exit $?"; grep "Tests run:" /tmp/t18.log | tail -1`
Expected: `install exit 0`, then `exit 0` and `Tests run: 3, Failures: 0, Errors: 0`.

**Step 7: Run a mutation check**

Change the `submit` check in `MessageServiceAccess` to `@PreAuthorize("permitAll()")`. Rebuild with the Step 6 command. Expected: `case 11` fails, because the stranger admits a command. Restore by absolute path.

**Step 8: Commit**

```bash
git add chat-service-controller/src chat-security/src chat-deploy-memory/src/test
git commit -m "Add the RSocket submit and status routes under method security (<task-18-issue>)"
```

---

### Task 19: REST submit and status routes

**Files:**
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/composite/mapping/ChatMessageServiceRestMapping.kt`
- Create: `chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/composite/mapping/SubmitStatus.kt`
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/config/WebFluxKeyVerifierConfiguration.kt` (class `KeyRefusalAdvice`)
- Test: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageSubmitRestTests.kt`

**Interfaces:**
- Consumes: `ChatMessageService.submit` and `commandStatus`, and the Task 1 exceptions.
- Produces: `POST /message/submit/{id}` with the `Idempotency-Key` header, `GET /message/command/{commandId}`, and `object SubmitStatus { fun of(outcome: CallerOutcome): Int }`.

| Result | Status |
|---|---|
| `COMPLETED` | 201 |
| `ACCEPTED`, `PENDING` | 202 |
| `INCOMPLETE` | 424 |
| `RequestConflictException` | 409 |
| `InvalidRequestIdException`, or no header | 400 |
| `SenderMismatchException` | 403 |
| `CommandPendingException` from the legacy send | 504 |
| `CommandIncompleteException` from the legacy send | 424 |
| A status of another owner, `NotFoundException` | 404 |

**Step 1: Write the failing test**

```kotlin
package com.demo.chat.test.controller.webflux.composite

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.KeyRefusalAdvice
import com.demo.chat.controller.webflux.ChatMessageServiceController
import com.demo.chat.domain.MessageSubmitRequest
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.command.CallerOutcome
import com.demo.chat.domain.command.InvalidRequestIdException
import com.demo.chat.domain.command.MessageSendResult
import com.demo.chat.domain.command.Receipt
import com.demo.chat.domain.command.RequestConflictException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.test.TestBase.Companion.anyObject
import com.demo.chat.test.TestGeneratorKeyService
import com.demo.chat.test.key.TestKeys
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

@WebFluxTest
@ContextConfiguration(
    classes = [
        TestLongCompositeServiceBeans::class,
        WebFluxTestConfiguration::class,
        LongTypeUtilConfiguration::class,
        ChatMessageServiceController::class,
        MessageSubmitRestConfiguration::class,
        KeyRefusalAdvice::class,
    ]
)
@TestPropertySource(properties = ["app.controller.message"])
class MessageSubmitRestTests {
    @Autowired lateinit var client: WebTestClient
    @Autowired lateinit var beans: CompositeServiceBeans<Long, String>
    @Autowired lateinit var registry: TestGeneratorKeyService<Long>

    private fun result(outcome: CallerOutcome) =
        MessageSendResult(Receipt("c-rest", TestKeys.message(1L, 7L, ALLOWED_ROOM)), outcome, emptyMap())

    @BeforeEach
    fun `register the rooms`() {
        registry.register(ALLOWED_ROOM, ChatDomain.MESSAGE_TOPIC)
        registry.register(DENIED_ROOM, ChatDomain.MESSAGE_TOPIC)
        clearInvocations(beans.messageService())
    }

    private fun submit(room: Long, key: String?) = client.post().uri("/message/submit/$room")
        .contentType(MediaType.TEXT_PLAIN)
        .apply { if (key != null) header("Idempotency-Key", key) }
        .bodyValue("hello")
        .exchange()

    @Test
    fun `Completed answers 201 and the header becomes the request ID`() {
        given(beans.messageService().submit(anyObject())).willReturn(Mono.just(result(CallerOutcome.COMPLETED)))
        submit(ALLOWED_ROOM, "rest-1").expectStatus().isCreated
            .expectBody(String::class.java).value { assertBody(it, "\"outcome\":\"COMPLETED\"", "c-rest") }
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(MessageSubmitRequest::class.java) as ArgumentCaptor<MessageSubmitRequest<Long, String>>
        verify(beans.messageService()).submit(captor.capture())
        org.assertj.core.api.Assertions.assertThat(captor.value.requestId).isEqualTo("rest-1")
    }

    @Test
    fun `Pending and Accepted answer 202`() {
        given(beans.messageService().submit(anyObject())).willReturn(Mono.just(result(CallerOutcome.PENDING)))
        submit(ALLOWED_ROOM, "rest-2").expectStatus().isAccepted
        given(beans.messageService().submit(anyObject())).willReturn(Mono.just(result(CallerOutcome.ACCEPTED)))
        submit(ALLOWED_ROOM, "rest-3").expectStatus().isAccepted
    }

    @Test
    fun `Incomplete answers 424`() {
        given(beans.messageService().submit(anyObject())).willReturn(Mono.just(result(CallerOutcome.INCOMPLETE)))
        submit(ALLOWED_ROOM, "rest-4").expectStatus().isEqualTo(424)
    }

    @Test
    fun `a conflict answers 409`() {
        given(beans.messageService().submit(anyObject())).willReturn(Mono.error(RequestConflictException("rest-5")))
        submit(ALLOWED_ROOM, "rest-5").expectStatus().isEqualTo(409)
    }

    @Test
    fun `review focus 1 - a missing or invalid header answers 400`() {
        submit(ALLOWED_ROOM, null).expectStatus().isBadRequest
        verify(beans.messageService(), never()).submit(anyObject())
        given(beans.messageService().submit(anyObject())).willReturn(Mono.error(InvalidRequestIdException("It holds a character outside visible ASCII.")))
        submit(ALLOWED_ROOM, "has space").expectStatus().isBadRequest
    }

    @Test
    fun `case 11 - a caller that may not send is refused with 403 and the service never runs`() {
        submit(DENIED_ROOM, "rest-6").expectStatus().isForbidden
        verify(beans.messageService(), never()).submit(anyObject())
    }

    @Test
    fun `review focus 5 - a status of another owner answers 404`() {
        given(beans.messageService().commandStatus(anyObject())).willReturn(Mono.error(NotFoundException))
        client.get().uri("/message/command/c-other").exchange().expectStatus().isNotFound
    }

    private fun assertBody(body: String, vararg parts: String) =
        org.assertj.core.api.Assertions.assertThat(body).contains(*parts)

    companion object {
        const val ALLOWED_ROOM = 12345L
        const val DENIED_ROOM = 23456L
    }
}
```

Copy `ListMessagesRestConfiguration` from `ListMessagesRestTests` into this file as `MessageSubmitRestConfiguration`. Change its broker rule to `action == "SEND"` and reference `MessageSubmitRestTests.ALLOWED_ROOM`. Copy the imports of `ListMessagesRestTests` for the configuration classes, and keep any of the imports above that the compiler confirms.

**Step 2: Run the test to verify that it fails**

Run: `mvn -o -B -pl chat-core,chat-security,chat-webflux test -Dtest=MessageSubmitRestTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t19.log 2>&1; echo "exit $?"`
Expected: `exit 1`. The routes answer 404.

**Step 3: Write the routes, the status mapping, and the advice**

`SubmitStatus.kt`:

```kotlin
package com.demo.chat.controller.webflux.composite.mapping

import com.demo.chat.domain.command.CallerOutcome

/** The REST status of each caller outcome. The body is the authority. Decision 10 of the spec. */
object SubmitStatus {
    fun of(outcome: CallerOutcome): Int = when (outcome) {
        CallerOutcome.COMPLETED -> 201
        CallerOutcome.ACCEPTED, CallerOutcome.PENDING -> 202
        CallerOutcome.INCOMPLETE -> 424
    }
}
```

In `ChatMessageServiceRestMapping`, add:

```kotlin
    /**
     * The request ID travels in `Idempotency-Key`, because the body holds the
     * message text. A retry with the same key recovers the same receipt.
     * The facade carries its own check. A facade call crosses no proxy.
     */
    @PostMapping("/submit/{id}", consumes = [MediaType.TEXT_PLAIN_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("@chatAccess.hasAccessToId(#id.key.id, 'SEND')")
    fun restSubmit(
        @Resolved(ChatDomain.MESSAGE_TOPIC) id: VerifiedKey<T>,
        @RequestBody message: String,
        @RequestHeader("Idempotency-Key") requestId: String,
    ): Mono<ResponseEntity<MessageSendResult<T>>> =
        submit(MessageSubmitRequest(message, id.key.id, requestId))
            .map { ResponseEntity.status(SubmitStatus.of(it.outcome)).body(it) }

    /** Every caller may ask. The service answers not found for a command of another owner. */
    @GetMapping("/command/{commandId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @PreAuthorize("permitAll()")
    fun restCommandStatus(@PathVariable commandId: String): Mono<out CommandStatus<T>> =
        commandStatus(CommandStatusRequest(commandId))
```

In `KeyRefusalAdvice`, add:

```kotlin
    @ExceptionHandler(RequestConflictException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun requestConflict(error: RequestConflictException): String = error.message ?: "The request conflicts."

    @ExceptionHandler(InvalidRequestIdException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalidRequestId(error: InvalidRequestIdException): String = error.message ?: "The request ID is not valid."

    @ExceptionHandler(SenderMismatchException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun senderMismatch(error: SenderMismatchException): String = error.message ?: "The sender is refused."

    /** The legacy send only. Its wait ended, and the command is still pending. */
    @ExceptionHandler(CommandPendingException::class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    fun commandPending(error: CommandPendingException): String = error.message ?: "The command is pending."

    @ExceptionHandler(CommandIncompleteException::class)
    @ResponseStatus(HttpStatus.FAILED_DEPENDENCY)
    fun commandIncomplete(error: CommandIncompleteException): String = error.message ?: "The command is incomplete."
```

**Step 4: Run the test to verify that it passes**

Run the Step 2 command. Expected: `exit 0` and `Tests run: 7, Failures: 0, Errors: 0`. Also run `ListMessagesRestTests` and `MessageRestAccessTests` in the same command, and expect 0 failures.

**Step 5: Run a mutation check**

Remove the `@PreAuthorize` line from `restSubmit`. Run Step 4. Expected: `case 11` fails, because the denied room reaches the service. Restore by absolute path.

**Step 6: Commit**

```bash
git add chat-webflux/src
git commit -m "Add the REST submit and status routes and their status codes (<task-19-issue>)"
```

---

### Task 20: Shell submit and status commands

**Files:**
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommands.kt:29-68`
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommandsRegistrar.kt:33-43`
- Modify: `docs/SHELL-COMMAND-CONTRACT.md`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/commands/PubSubSubmitTests.kt`

**Interfaces:**
- Consumes: `ChatMessageService.submit` and `commandStatus` through `MessagingClient`.
- Produces: `send` with an optional `--requestId`, and a new `command-status --commandId` command.

**Step 1: Write the failing test**

```kotlin
package com.demo.chat.test.commands

import com.demo.chat.domain.CommandStatusRequest
import com.demo.chat.domain.MessageSubmitRequest
import com.demo.chat.domain.command.CallerOutcome
import com.demo.chat.domain.command.CommandStatus
import com.demo.chat.domain.command.MessageSendResult
import com.demo.chat.domain.command.Receipt
import com.demo.chat.test.key.TestKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import reactor.core.publisher.Mono
```

Build the `PubSubCommands` instance the way `PubSubMessagesTests` builds it. Read that file and reuse its fields: the mocked `ChatMessageService` named `messages`, the composite beans, and the room lookup. Then add:

```kotlin
    @Test
    fun `send keeps a supplied request ID and prints the outcome`() {
        val receipt = Receipt("c-shell", TestKeys.message(5L, 10L, ROOM))
        given(messages.submit(anyObject())).willReturn(Mono.just(MessageSendResult(receipt, CallerOutcome.COMPLETED, emptyMap())))

        val printed = captureOut { commands.send(ROOM.toString(), "_", "hello", "shell-1") }

        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(MessageSubmitRequest::class.java) as ArgumentCaptor<MessageSubmitRequest<Long, String>>
        verify(messages).submit(captor.capture())
        assertThat(captor.value.requestId).isEqualTo("shell-1")
        assertThat(printed).contains("Request Id: shell-1", "Message Id: 5", "Outcome: COMPLETED")
    }

    @Test
    fun `send creates a request ID when none is supplied`() {
        val receipt = Receipt("c-shell-2", TestKeys.message(6L, 10L, ROOM))
        given(messages.submit(anyObject())).willReturn(Mono.just(MessageSendResult(receipt, CallerOutcome.PENDING, emptyMap())))
        val printed = captureOut { commands.send(ROOM.toString(), "_", "hello", "_") }
        assertThat(printed).containsPattern("Request Id: [0-9a-f-]{36}").contains("Outcome: PENDING")
    }

    @Test
    fun `command status prints each backend`() {
        val receipt = Receipt("c-shell-3", TestKeys.message(7L, 10L, ROOM))
        val status = CommandStatus(
            "c-shell-3", 10L, "shell-3", receipt,
            mapOf(com.demo.chat.domain.command.BackendId.PERSISTENCE to
                com.demo.chat.domain.command.BackendStatus(com.demo.chat.domain.command.BackendState.SUCCEEDED, 1)),
            2L,
        )
        given(messages.commandStatus(anyObject())).willReturn(Mono.just(status))
        val printed = captureOut { commands.commandStatus("c-shell-3") }
        assertThat(printed).contains("PERSISTENCE: SUCCEEDED")
    }

    private fun captureOut(block: () -> Unit): String {
        val original = System.out
        val buffer = java.io.ByteArrayOutputStream()
        System.setOut(java.io.PrintStream(buffer))
        try { block() } finally { System.setOut(original) }
        return buffer.toString()
    }
```

Use the `ROOM` constant and the `anyObject()` helper that `PubSubMessagesTests` uses.

**Step 2: Run the test to verify that it fails**

Run: `mvn -o -B -pl chat-core,chat-shell test -Dtest=PubSubSubmitTests -Dsurefire.failIfNoSpecifiedTests=false > /tmp/t20.log 2>&1; echo "exit $?"`
Expected: `exit 1`. `send` takes three arguments today.

**Step 3: Change the commands**

In `PubSubCommands`, replace `send` and add `commandStatus`:

```kotlin
    /**
     * [topic] is a room name or a room id. [requestId] `_` creates a new ID.
     * Repeat a send with the printed request ID to recover its receipt.
     */
    fun send(topic: String, userName: String, messageText: String, requestId: String) {
        val id = if (requestId == "_") UUID.randomUUID().toString() else requestId
        val dest: Mono<T> = when {
            topic != "_" -> Mono.just(rooms.idOf(topic))
            userName != "_" -> userService.findByUsername(ByStringRequest(userName)).collectList().flatMap { users ->
                if (users.size != 1) Mono.error(ChatException("Problem Finding a Definite User: $userName"))
                else Mono.just(users[0].key.id)
            }
            else -> throw IllegalArgumentException("send needs --topic or --userName.")
        }
        dest.flatMap { messageService.submit(MessageSubmitRequest(messageText, it, id)) }
            .doOnNext { result ->
                println("Request Id: $id")
                println("Message Id: ${result.receipt.messageKey.id}")
                println("Command Id: ${result.receipt.commandId}")
                println("Outcome: ${result.outcome}")
            }
            .block()
    }

    fun commandStatus(commandId: String) {
        messageService.commandStatus(CommandStatusRequest(commandId))
            .doOnNext { status ->
                println("Command Id: ${status.commandId}")
                status.backends.forEach { (backend, state) -> println("$backend: ${state.state}") }
            }
            .block()
    }
```

The old `send` read `identity("_")` for the sender. The server now binds the sender, so the shell sends none.

In `PubSubCommandsRegistrar.sendCommand`, add the option and pass it:

```kotlin
                CommandOption.with().longName("requestId").required(false).defaultValue("_").type(String::class.java).build(),
```

```kotlin
        .execute(Function<CommandContext, String> { ctx ->
            commands.send(ctx.optionValue("topic"), ctx.optionValue("userName"), ctx.mainValue("messageText"), ctx.optionValue("requestId")).let { "" }
        })
```

Add a bean beside `sendCommand`:

```kotlin
    @Bean
    fun commandStatusCommand(): Command = Command.builder()
        .name("command-status")
        .description("Show the status of a message command")
        .group("PubSub")
        .options(CommandOption.with().longName("commandId").required(true).type(String::class.java).build())
        .execute(Function<CommandContext, String> { ctx -> commands.commandStatus(ctx.optionValue("commandId")).let { "" } })
```

In `docs/SHELL-COMMAND-CONTRACT.md`, add `--requestId` to `send` and add `command-status`. State that `send` prints the request ID, and that a repeat with that ID recovers the same receipt.

**Step 4: Run the shell unit tests**

Run: `mvn -o -B -pl chat-core,chat-client-rsocket,chat-shell test > /tmp/t20.log 2>&1; echo "exit $?"; grep "Tests run:" /tmp/t20.log | tail -1`
Expected: `exit 0` with 0 failures. The container tests run in Task 22.

**Step 5: Commit**

```bash
git add chat-shell/src docs/SHELL-COMMAND-CONTRACT.md
git commit -m "Submit from the shell with a request ID and show command status (<task-20-issue>)"
```

---

### Task 21: Documentation

**Files:**
- Create: `docs/MESSAGE-COMMANDS.md`
- Modify: `docs/ANONYMOUS-AUTHORIZATION.md`
- Modify: `docs/VECTOR-RECALL-API.md`
- Modify: `docs/BUILD.md`
- Modify: `CHANGELOG.md`
- Modify: `drift.lock` through `drift link`

**Step 1: Write the operator and API document**

Create `docs/MESSAGE-COMMANDS.md` with this content:

````markdown
# Message commands

A message send is a captured command. Independent backend handlers run it. The caller waits for a declared set of backends.

Spec: `docs/superpowers/specs/2026-10-07-domain-command-bus-design.md`. This document describes Stage 1.

## Stage 1 limits

- The command bus is `memory`. It runs in one process.
- Stage 1 gives no crash-recovery guarantee. A restart loses request mappings, command status, and unresolved commands.
- The `replicas` check reads a declaration. It does not detect other processes. Do not run two processes with the `memory` bus against one store.
- Command TTL is disabled. Startup rejects a finite value.

## Properties

| Property | Default | Meaning |
|---|---|---|
| `app.command.bus` | none | Required. Stage 1 accepts `memory`. Startup rejects an unset value, an unknown value, and `kafka`. |
| `app.command.ttl` | `disabled` | Startup rejects a finite value. |
| `app.command.replicas` | `1` | A declaration. Startup rejects any other value. |
| `app.command.completion.requirement` | `P,I` | The backends a caller waits for. `none` waits for none. |
| `app.command.completion.timeout` | `5s` | The caller wait. A starting value, not a production measurement. |
| `app.command.recovery.interval` | `30s` | The interval between recovery attempts. A starting value, not a production measurement. |

A duration is an integer and one unit: `ms`, `s`, `m`, `h`, or `d`. Example: `30s`.

`P` is Persistence, `I` is Index, `V` is Vector, and `U` is Pubsub.

## Submit

| Transport | Route | Request ID |
|---|---|---|
| REST | `POST /message/submit/{roomId}`, body is the text | Header `Idempotency-Key` |
| RSocket | `message.message-submit` with `MessageSubmitRequest` | Field `requestId` |

A request ID has 1 to 128 visible ASCII characters. Keep it for each intentional send. Reuse it on every retry. A retry with the same text and room recovers the same receipt. A retry with other text under the same ID is a conflict.

The server binds the sender to the authenticated user. A submission carries no sender.

| Outcome | REST status | Meaning |
|---|---|---|
| `COMPLETED` | 201 | Every required backend succeeded. |
| `ACCEPTED` | 202 | The requirement is `none`. Admission committed. |
| `PENDING` | 202 | The wait ended first. Execution continues. |
| `INCOMPLETE` | 424 | A required backend failed. |

A conflict answers 409. A missing or invalid request ID answers 400.

With `none`, a message lookup can answer not found until `P` completes.

## Status

REST: `GET /message/command/{commandId}`. RSocket: `message.message-command-status`.

Only the owner reads a status. Another caller receives not found.

An anonymous caller cannot submit, cannot use the legacy send, and cannot read a status. Every anonymous caller holds one shared `Anon` key, and request identity needs an authenticated owner.

## Legacy send

`POST /message/send/{roomId}` and `message.message-send` stay as adapters.

- **They give no retry safety.** The server creates a new request ID for each call. A retry can store a second message.
- **Behavior change:** the RSocket `message-send` rejects a `from` value that is not the authenticated user. Before Stage 1 it accepted any sender.
- **Behavior change:** both legacy routes refuse an anonymous caller.
- They wait for the default requirement. A wait that ends answers 504 on REST, with the command ID. A failed required backend answers 424.

## Delivery to listeners

A listener receives the room history, the messages that `U` published before it subscribed, and the live messages. It receives each message ID once per subscription. A message whose `U` failed reaches a listener only through the history, after `I` indexes it.

## Recovery

Only a refusal before any effect is a definitive failure. Every other error is uncertain, because it can follow an effect.

Each backend makes at most five attempts, including the first. A backend that is still uncertain then retries once per recovery interval with the same command. An uncertain command holds the later commands of its room for that backend. Other rooms and other backends continue.

An attempt that runs longer than 30 seconds becomes uncertain. The runtime never cancels it, because a cancel does not prove that the backend stopped. Nothing else in that room and backend starts until it ends.

## Supported providers

Stage 1 starts only with providers that have safe-repeat evidence in CI:

| Selector | Values |
|---|---|
| `app.service.core.key` | `memory`, `redis`, `cassandra` |
| `app.service.core.persistence` | `memory`, `redis`, `cassandra` |
| `app.service.core.index` | `lucene`, `cassandra` |
| `app.service.core.vector` | `simple`, `embedded`, `redis` |
| `app.service.core.pubsub` | `memory`, `redis-pubsub`, `kafka` |

Startup rejects any other value, such as `redis-xstream`.
````

**Step 2: Update the other documents**

1. `docs/ANONYMOUS-AUTHORIZATION.md`: after the `send` row of the matrix, add a row for `submit` with the answers of `send`. Add a row for `command status` that answers "owner only" for every caller. Add one sentence under the matrix: "The RSocket `message-send` rejects a sender that is not the caller, since Stage 1."
2. `docs/VECTOR-RECALL-API.md`: in each scenario that sends and then recalls, add one sentence: "A send waits for `P` and `I` only. Set `app.command.completion.requirement=P,I,V`, or wait, before a recall reads the new message."
3. `docs/BUILD.md`: in the core launch section, state that `chat-build` emits `-Dapp.command.bus=memory`, and that a hand-written launch must set it.
4. `CHANGELOG.md`: under `## [Unreleased]`, add:

```markdown
### 2026-10-08 — Message command bus Stage 1

| Change | Reason |
|---|---|
| A send is a captured command on the `memory` bus | Independent backend handlers, caller completion waits |
| New REST and RSocket submit and status routes | Retry safety through a request ID |
| `app.command.bus` is required | Provider selection at launch |
| The legacy RSocket `message-send` rejects another sender | The sender binds to the authenticated user |
| Submit, legacy send, and status refuse an anonymous caller | Request identity needs an authenticated owner |
| Startup rejects a provider without safe-repeat evidence, such as `redis-xstream` | Every active handler needs CI evidence |
| The legacy send routes give no retry safety | Each call creates a new request ID |
```

**Step 3: Bind the new document with drift**

Run:

```bash
drift link docs/MESSAGE-COMMANDS.md chat-core/src/main/kotlin/com/demo/chat/config/CommandBusValidation.kt
drift link docs/MESSAGE-COMMANDS.md chat-core/src/main/kotlin/com/demo/chat/config/CommandBusSettings.kt
drift link docs/MESSAGE-COMMANDS.md chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessagingServiceImpl.kt
drift link docs/MESSAGE-COMMANDS.md chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/composite/mapping/SubmitStatus.kt
drift link docs/MESSAGE-COMMANDS.md chat-core/src/main/kotlin/com/demo/chat/service/command/BackendExecutionPolicy.kt
drift check > /tmp/t21-drift.log 2>&1; echo "exit $?"; tail -3 /tmp/t21-drift.log
```

Expected: `exit 0` and `ok`. Bind whole files. drift v0.7.0 cannot fingerprint a Kotlin symbol anchor.

**Step 4: Check the prose rules**

Run:

```bash
for f in docs/MESSAGE-COMMANDS.md; do echo "semicolons: $(grep -c ';' $f)"; done
git diff --check; echo "diff-check exit $?"
```

Expected: `semicolons: 0` and `diff-check exit 0`.

**Step 5: Commit**

```bash
git add docs CHANGELOG.md drift.lock
git commit -m "Document the Stage 1 message command bus (<task-21-issue>)"
```

---

### Task 22: Final gates

**Files:**
- Modify: `docs/BUILD-HEALTH.md` (recorded test counts only)

**Step 1: Run the default gate**

Run: `shell-scripts/build-health.sh > /tmp/t22-default.log 2>&1; echo "exit $?"; tail -6 /tmp/t22-default.log`
Expected: `exit 0` and `reality matches docs/BUILD-HEALTH.md`.

**Step 2: Run the CI gate with a fresh image**

Run: `DOCKER_CONFIG=$(mktemp -d) shell-scripts/build-health.sh --ci > /tmp/t22-ci.log 2>&1; echo "exit $?"; tail -8 /tmp/t22-ci.log`
Expected: `exit 0`, `reality matches docs/BUILD-HEALTH.md`, and `agent http gate: ok`. Read the image ID from the log. It must differ from the Task 0 baseline. A run without the new image ID is not evidence.

If Cassandra fails with `NoNodeAvailableException`, run `docker events --since 30m --until 0s --filter event=oom` before you treat it as a code failure. Another session can exhaust the shared Docker VM.

**Step 3: Run the launch gates**

Run:

```bash
shell-scripts/test-flags.sh > /tmp/t22-flags.log 2>&1; echo "flags exit $?"
shell-scripts/vector/gate-embedding-launch.sh > /tmp/t22-vector.log 2>&1; echo "vector exit $?"
drift check > /tmp/t22-drift.log 2>&1; echo "drift exit $?"
git diff --check master...HEAD; echo "diff-check exit $?"
```

Expected: every exit is 0. The vector gate rebuilds the index from persistence, so the send requirement does not affect it.

**Step 4: Record the counts**

Copy the module and test counts of Steps 1 and 2 into `docs/BUILD-HEALTH.md`, where it records the current counts. Commit:

```bash
git add docs/BUILD-HEALTH.md
git commit -m "Record the build health of the command bus Stage 1 branch (<stage-1-issue>)"
```

**Step 5: Report**

Run `fp comment <stage-1-issue> "..."`. Name every gate result, the image ID, and each mutation check with its result. Then use the superpowers:finishing-a-development-branch skill. Push every commit and open the pull request before the owner merges.

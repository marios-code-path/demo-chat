# Vector Index Status Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to
> implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for
> tracking. This repository forbids subagent-driven development. See
> `CLAUDE.md`.

**Goal:** Make every reader of the vector rebuild status wait on facts the code
actually promises, and give the trigger a result that says whether it started a
run.

**Architecture:** The production ordering does not change. `state.finish` still
clears `running` before the durable write, because that order stops a failed
write from leaving a run active forever. One production type is added,
`VectorIndexTriggerResult`, because a rejected trigger was otherwise
indistinguishable from an accepted one. Three readers then wait on two
conditions under two bounds: the test helper, the operator guide, and the
packaged launch gate.

**Tech Stack:** Kotlin 2.4.10, Java 25, Spring Boot 3.5.16, Project Reactor,
JUnit 5, AssertJ, Maven.

**Spec:** `docs/superpowers/specs/2026-09-16-vector-index-status-contract-design.md`

**Issue:** `CHAT-cxduiwjj`

## Global Constraints

- Write all prose in ASD-STE100 strict mode. See `CLAUDE.md`.
- Run `mvn -o -pl chat-core,<module> test`. Never run `-pl <module>` alone. A
  single-module run reads a stale `chat-core` from `~/.m2`.
- A deploy module needs the full reactor. Run `mvn -o -pl <module> -am` when the
  module reads `chat-service-composite`.
- Run `drift check` and `git diff --check` before each commit.
- Never pipe a build or a gate into `tail`. The pipeline reports the exit status
  of `tail`, which hides a failure.
- Activate miniforge before you run Python. Name `$CONDA_PREFIX/bin/python3`.
  A bare `python3` resolves to the homebrew interpreter on this machine.
- Mention `CHAT-cxduiwjj` in each commit message.
- Prefix every `fp` command with `FP_AGENT_NAME='sigma'`.
- End each commit message with
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- Each commit body below carried angle brackets while this plan was open.
  Every one is now replaced with the value that the run measured on
  2026-09-16.

---

## File Structure

### New files

| File | Responsibility |
|------|----------------|
| `chat-core/src/main/kotlin/com/demo/chat/service/vector/VectorIndexTriggerResult.kt` | The trigger answer: `accepted` beside the status |
| `chat-core/src/test/kotlin/com/demo/chat/test/vector/VectorIndexTriggerResultWireTests.kt` | The wire shape of the trigger answer, and the read payload that must not carry it |

### Modified files

| File | Change |
|------|--------|
| `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageReindexService.kt` | `start()` returns `Mono<VectorIndexTriggerResult<T>>` |
| `chat-core/src/main/kotlin/com/demo/chat/service/vector/VectorIndexState.kt` | KDoc on `running` and `complete` |
| `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageReindexServiceImpl.kt` | `start()` returns the claim result |
| `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorIndexStartupAction.kt` | One call site follows the new type |
| `chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/actuator/VectorIndexEndpoint.kt` | The write operation returns the trigger result |
| `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/VectorIndexEndpointTests.kt` | Two write tests read `accepted` |
| `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessageReindexServiceImplTests.kt` | The wait helper, the two flaky tests, and three new tests |
| `shell-scripts/vector/gate-embedding-launch.sh` | Acceptance, two bounds, timestamp correlation, and a SUCCEEDED requirement |
| `docs/VECTOR-RECALL-API.md` | The new POST answer, and both bounds in each scenario |

---

## Task 1: The trigger result and the contract KDoc

This task adds the type, changes one signature, and follows every call site. It
adds no reader behaviour. Task 2 changes the readers.

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/vector/VectorIndexTriggerResult.kt`
- Create: `chat-core/src/test/kotlin/com/demo/chat/test/vector/VectorIndexTriggerResultWireTests.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageReindexService.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/service/vector/VectorIndexState.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageReindexServiceImpl.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorIndexStartupAction.kt`
- Modify: `chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/actuator/VectorIndexEndpoint.kt`
- Modify: `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/VectorIndexEndpointTests.kt`

**Interfaces:**
- Consumes: nothing from an earlier task.
- Produces:
  - `com.demo.chat.service.vector.VectorIndexTriggerResult<T>`, a data class with
    `accepted: Boolean` and `status: VectorIndexStatus<T>`.
  - `MessageReindexService.start(): Mono<VectorIndexTriggerResult<T>>`.
  - `VectorIndexEndpoint.startVectorIndexRebuild(): Mono<VectorIndexTriggerResult<T>>`.

- [ ] **Step 1: Write the failing wire test**

Create `chat-core/src/test/kotlin/com/demo/chat/test/vector/VectorIndexTriggerResultWireTests.kt`.

```kotlin
package com.demo.chat.test.vector

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.domain.Key
import com.demo.chat.service.vector.VectorIndexPhase
import com.demo.chat.service.vector.VectorIndexStatus
import com.demo.chat.service.vector.VectorIndexTriggerResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

/**
 * The trigger answer carries accepted beside the status. The read answer does
 * not carry accepted at all.
 *
 * A rejected trigger and an accepted one both report running=true, because
 * another run holds the claim in the first case. So a reader cannot tell them
 * apart from the status. See CHAT-cxduiwjj.
 */
class VectorIndexTriggerResultWireTests {

    private val mapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .apply { registerModules(DefaultChatJacksonModules().allModules()) }

    private val status = VectorIndexStatus<Long>(
        phase = VectorIndexPhase.REBUILDING,
        activeJob = Key.funKey(500L),
    )

    @Test
    fun `the trigger answer carries accepted beside the status`() {
        val json = mapper.writeValueAsString(VectorIndexTriggerResult(true, status))

        Assertions.assertThat(json).contains("\"accepted\":true")
        Assertions.assertThat(json).contains("\"phase\":\"REBUILDING\"")
    }

    @Test
    fun `a rejected trigger carries the same status shape`() {
        val json = mapper.writeValueAsString(VectorIndexTriggerResult(false, status))

        Assertions.assertThat(json).contains("\"accepted\":false")
        Assertions.assertThat(json).contains("\"running\":true")
    }

    @Test
    fun `the status alone carries no accepted field`() {
        // The read operation answers with this type. A trigger only field
        // there would answer a question that a read never asks.
        val json = mapper.writeValueAsString(status)

        Assertions.assertThat(json).doesNotContain("accepted")
    }

    @Test
    fun `the trigger answer survives a round trip`() {
        val json = mapper.writeValueAsString(VectorIndexTriggerResult(false, status))

        val decoded = mapper.readValue(json, VectorIndexTriggerResult::class.java)

        Assertions.assertThat(decoded.accepted).isFalse()
        Assertions.assertThat(decoded.status.running).isTrue()
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

```bash
mvn -o -pl chat-core -Dtest=VectorIndexTriggerResultWireTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: a compile failure. `VectorIndexTriggerResult` does not exist.

- [ ] **Step 3: Write the type**

Create `chat-core/src/main/kotlin/com/demo/chat/service/vector/VectorIndexTriggerResult.kt`.

```kotlin
package com.demo.chat.service.vector

/**
 * The answer of one rebuild trigger.
 *
 * [accepted] states whether this call started a run. A rejected trigger means
 * another run holds the claim, and it creates no job.
 *
 * The field exists because the status cannot carry that fact. A rejected
 * trigger and an accepted one both report running=true, so a reader that saw
 * the status alone waited for a job that no call had created, and then reported
 * a missing durable record. See CHAT-cxduiwjj.
 *
 * The read operation answers with [VectorIndexStatus] and not with this type.
 * A read never asks whether it started anything.
 */
data class VectorIndexTriggerResult<T>(
    val accepted: Boolean,
    val status: VectorIndexStatus<T>,
)
```

- [ ] **Step 4: Change the service signature**

Modify `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageReindexService.kt`.

```kotlin
interface MessageReindexService<T> {
    /**
     * Starts one rebuild and returns at once.
     *
     * The answer states whether this call started a run. It never promises the
     * job key, because the run creates its job after the claim and on another
     * scheduler.
     */
    fun start(): Mono<VectorIndexTriggerResult<T>>

    fun status(): VectorIndexStatus<T>
}
```

- [ ] **Step 5: Add the contract KDoc to both flags**

Modify `chat-core/src/main/kotlin/com/demo/chat/service/vector/VectorIndexState.kt`. Replace the KDoc of `complete` and add one to `running`.

```kotlin
    /**
     * A job covers the index. The phase never decides this value.
     *
     * `claim()` moves a complete index to REBUILDING, and a repair must not
     * lower the reported coverage while it runs.
     *
     * **This value reports in-process coverage, and it does not prove that the
     * durable job holds a terminal outcome.** A failed terminal write leaves
     * this value true beside a durable RUNNING job. Under
     * app.vector.index.trust=stored a restart then reports no coverage, though
     * the previous process reported complete. IndexJob.outcome is the durable
     * fact. See CHAT-cxduiwjj.
     */
    val complete: Boolean
        get() = coveringJob != null

    /**
     * A run holds the claim in this process.
     *
     * **A false value means that the state decided the outcome. It does not
     * prove that the durable terminal IndexJob write finished.** finishRun
     * clears this flag before it writes the record, so that a failed write
     * cannot leave a run active forever. A reader that needs the durable fact
     * waits for a terminal IndexJob.outcome as well. See CHAT-cxduiwjj.
     */
    val running: Boolean
        get() = phase == VectorIndexPhase.REBUILDING
```

- [ ] **Step 6: Change the implementation**

Modify `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageReindexServiceImpl.kt`. Change the `start` body and the return type.

```kotlin
    override fun start(): Mono<VectorIndexTriggerResult<T>> = Mono.fromSupplier {
        val claim = state.claim()
        if (claim.accepted) {
            val startedAt = clock.instant()
            try {
                rebuild(claim, startedAt)
                    .subscribeOn(scheduler)
                    .subscribe(
                        {},
                        { error -> release(claim, startedAt, error) },
                    )
            } catch (error: Throwable) {
                release(claim, startedAt, error)
            }
        }
        VectorIndexTriggerResult(claim.accepted, claim.status)
    }
```

Add the import.

```kotlin
import com.demo.chat.service.vector.VectorIndexTriggerResult
```

- [ ] **Step 7: Follow the startup call site**

Modify `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorIndexStartupAction.kt`. The call discards its answer through `then()`, so the line needs no change. Read line 134 and confirm that it still compiles.

```kotlin
            reindex.start().then()
```

- [ ] **Step 8: Change the actuator write operation**

Modify `chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/actuator/VectorIndexEndpoint.kt`.

```kotlin
    /**
     * Starts one rebuild and returns at once.
     *
     * The answer carries `accepted` beside the claim snapshot. A rejected
     * trigger means another run holds the claim, and it creates no job. An
     * accepted first trigger reports `running=true` and `activeJob=null`, and
     * **it never promises the job key**. The run creates its job after the
     * claim, and on another scheduler.
     *
     * A client polls the read operation until the active job is not null, or
     * until running is false. An immediate second read does not close that
     * race, so this method performs none.
     */
    @WriteOperation
    fun startVectorIndexRebuild(): Mono<VectorIndexTriggerResult<T>> = reindex.start()
```

Add the import.

```kotlin
import com.demo.chat.service.vector.VectorIndexTriggerResult
```

- [ ] **Step 9: Change the two endpoint tests**

Modify `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/VectorIndexEndpointTests.kt`.

```kotlin
    @Test
    fun `the write operation starts one job and reports no active job`() {
        val result = endpoint().startVectorIndexRebuild().block()!!

        Assertions.assertThat(service.starts.get()).isEqualTo(1)
        Assertions.assertThat(result.accepted).isTrue()
        Assertions.assertThat(result.status.running).isTrue()
        Assertions.assertThat(result.status.activeJob).isNull()
    }

    @Test
    fun `a second write returns busy and starts no second job`() {
        val endpoint = endpoint()

        endpoint.startVectorIndexRebuild().block()
        val second = endpoint.startVectorIndexRebuild().block()!!

        Assertions.assertThat(service.starts.get()).isEqualTo(1)
        // The status cannot carry this fact. Both answers report running=true,
        // because the first run holds the claim.
        Assertions.assertThat(second.accepted).isFalse()
        Assertions.assertThat(second.status.running).isTrue()
    }
```

`RecordingReindexService` in that class implements the interface, so its
`start` changes with it. Keep the counter and the running flag.

```kotlin
        override fun start(): Mono<VectorIndexTriggerResult<Long>> = Mono.fromSupplier {
            // A second start finds a run in progress, so the claim is
            // rejected. The endpoint answers with that fact now.
            val accepted = !running
            if (accepted) {
                starts.incrementAndGet()
                running = true
            }
            VectorIndexTriggerResult(accepted, status())
        }
```

Add the import to that test file.

```kotlin
import com.demo.chat.service.vector.VectorIndexTriggerResult
```

- [ ] **Step 10: Compile every module that reads the signature**

```bash
mvn -o -B -Dmaven.test.skip=true install
```

Expected: BUILD SUCCESS. A failure here names a call site that this task missed.

- [ ] **Step 11: Run the three affected modules**

```bash
mvn -o -pl chat-core -Dtest=VectorIndexTriggerResultWireTests -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -pl chat-core,chat-service-composite clean test
mvn -o -pl chat-deploy -am clean test
```

Run each command on its own. Expected: PASS on all three. The wire test reports
4 tests.

`MessageReindexServiceImplTests` still holds the race at this point. It reads
`service.start().block()!!.running`, which no longer compiles, so Task 1 also
changes those seven call sites to `.status.running`. Task 2 fixes the race.

- [ ] **Step 12: Commit**

```bash
drift check && git diff --check
git add chat-core chat-service-composite chat-deploy
git commit -F - <<'MSG'
feat: give the rebuild trigger an accepted result (CHAT-cxduiwjj)

A rejected trigger and an accepted one both reported running=true, because
the first run holds the claim. So a reader could not tell them apart, and a
reader that waited for a new job after a rejected trigger waited for a job
that no call had created.

start() now answers with VectorIndexTriggerResult, which carries accepted
beside the status. The actuator write operation answers with the same type.
The read operation keeps its status and jobs shape, because a read never
asks whether it started anything.

Both status flags gained the contract in KDoc. running=false means the
state decided the outcome, and complete=true means the state holds a
covering job. Neither proves that the durable job write finished.

Evidence, measured on 2026-09-16.

- 4 tests pass in VectorIndexTriggerResultWireTests.
- chat-core, chat-service-composite, and chat-deploy pass.
- The full reactor installs.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Task 2: The readers wait on the durable fact

**Files:**
- Modify: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessageReindexServiceImplTests.kt`

**Interfaces:**
- Consumes: `VectorIndexTriggerResult` from Task 1.
- Produces: `awaitDurableOutcome`, a helper that every test of a finished run
  uses.

- [ ] **Step 1: Write the deterministic delayed write test**

Add to `MessageReindexServiceImplTests`. The fake store gains one field first.

```kotlin
        var finishDelay: Duration = Duration.ZERO
```

Change `FakeJobStore.finishJob` to honour it.

```kotlin
        override fun finishJob(job: IndexJob<Long>): Mono<Void> = Mono.defer {
            finishCalls += 1
            if (failFinish) {
                Mono.error(IllegalStateException("the terminal write failed"))
            } else {
                written.add(job)
                pubsub.close(job.key)
                Mono.empty()
            }
        }.delaySubscription(finishDelay)
```

Add the test.

```kotlin
    // The window this test opens is the contract. running=false says the state
    // decided the outcome, and the durable record follows later.
    @Test
    fun `running turns false before the durable record is terminal`() {
        jobStore.finishDelay = Duration.ofMillis(300)
        given(persistence.all()).willReturn(Flux.just(message(1L)))

        service.start().block()

        val decided = Flux.interval(Duration.ZERO, Duration.ofMillis(5))
            .map { service.status() }
            .filter { !it.running }
            .next()
            .block(Duration.ofSeconds(10))!!

        Assertions.assertThat(decided.running).isFalse()
        Assertions.assertThat(jobStore.written.lastOrNull()?.outcome)
            .describedAs("the durable record is not terminal yet")
            .isNotEqualTo(JobOutcome.SUCCEEDED)

        val durable = awaitDurableOutcome()
        Assertions.assertThat(durable.outcome).isEqualTo(JobOutcome.SUCCEEDED)
    }
```

- [ ] **Step 2: Run the test and confirm it fails**

```bash
mvn -o -pl chat-core,chat-service-composite -Dtest=MessageReindexServiceImplTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: a compile failure. `awaitDurableOutcome` does not exist.

- [ ] **Step 3: Write the wait helper**

Add to `MessageReindexServiceImplTests`.

```kotlin
    /**
     * Waits for the durable terminal record of the newest job.
     *
     * Two bounds, because two things can fail. The outer bound covers the run,
     * and the inner bound covers the interval between running=false and the
     * durable write. A reader that bounded only the second one would hang on a
     * run that never ends.
     *
     * The message of each failure names which bound expired, because the two
     * mean different things. An expired outer bound says the run did not
     * finish. An expired inner bound says the run ended with no durable
     * record.
     */
    private fun awaitDurableOutcome(
        outer: Duration = Duration.ofSeconds(10),
        inner: Duration = Duration.ofSeconds(5),
    ): IndexJob<Long> {
        Flux.interval(Duration.ZERO, Duration.ofMillis(10))
            .map { service.status() }
            .filter { !it.running }
            .next()
            .block(outer)
            ?: Assertions.fail<VectorIndexStatus<Long>>(
                "the outer bound expired and the run did not finish"
            )

        return Flux.interval(Duration.ZERO, Duration.ofMillis(10))
            .map { jobStore.written.lastOrNull() }
            .filter { job -> job != null && job.outcome != JobOutcome.RUNNING }
            .next()
            .block(inner)
            ?: Assertions.fail<IndexJob<Long>>(
                "the inner bound expired and the run left no durable record"
            )
    }
```

- [ ] **Step 4: Run the test and confirm it passes**

```bash
mvn -o -pl chat-core,chat-service-composite -Dtest=MessageReindexServiceImplTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the new test passes. The two racing tests may still fail, and Step 5
fixes them.

- [ ] **Step 5: Move the two racing tests onto the helper**

Replace the tail of `a live failure during the run leaves no covering job in the store`.

```kotlin
        service.start().block()
        state.invalidate("live vector add failed")

        val written = awaitDurableOutcome()
        Assertions.assertThat(written.outcome).isEqualTo(JobOutcome.FAILED)
        Assertions.assertThat(state.coveringJob()).isNull()
```

Replace the tail of `a failed terminal write finishes the run once`. That test
must not use `awaitDurableOutcome`, because a failed write writes no record.

```kotlin
        service.start().block()
        awaitFinished(service)

        // The write failed, so no durable record exists. The inner bound of a
        // reader expires here, and this test asserts the call count instead.
        Assertions.assertThat(jobStore.written).isEmpty()
        Assertions.assertThat(
            Flux.interval(Duration.ZERO, Duration.ofMillis(10))
                .map { jobStore.finishCalls }
                .filter { calls -> calls >= 1 }
                .next()
                .block(Duration.ofSeconds(5))
        ).isEqualTo(1)
```

- [ ] **Step 6: Add the missing durable record test**

```kotlin
    // The bound exists because this state is reachable. A failed terminal
    // write leaves running=false beside no durable record at all.
    @Test
    fun `a failed terminal write leaves no durable record and the inner bound expires`() {
        jobStore.failFinish = true
        given(persistence.all()).willReturn(Flux.just(message(1L)))

        service.start().block()

        val failure = Assertions.catchThrowable {
            awaitDurableOutcome(inner = Duration.ofMillis(300))
        }

        Assertions.assertThat(failure)
            .describedAs("the reader must stop and say which bound expired")
            .hasMessageContaining("inner bound")
        Assertions.assertThat(jobStore.written).isEmpty()
    }
```

- [ ] **Step 7: Add the RELEASED terminal test**

```kotlin
    // RELEASED ends a wait. It does not prove that the rebuild did its work.
    @Test
    fun `a released job is a terminal outcome`() {
        given(persistence.all()).willReturn(Flux.just(message(1L)))
        scheduler.dispose()

        service.start().block()
        val released = awaitFinished(service)

        Assertions.assertThat(released.running).isFalse()
        Assertions.assertThat(JobOutcome.RELEASED)
            .describedAs("RELEASED is terminal, and it is not SUCCEEDED")
            .isNotEqualTo(JobOutcome.SUCCEEDED)
        Assertions.assertThat(released.complete).isFalse()
    }
```

- [ ] **Step 8: Run the class twelve times**

A race needs repetition. One green run proves nothing.

```bash
FAILS=0
for i in $(seq 1 12); do
    if ! mvn -o -pl chat-core,chat-service-composite \
        -Dtest=MessageReindexServiceImplTests -Dsurefire.failIfNoSpecifiedTests=false \
        test > "/tmp/reindex-$i.log" 2>&1; then
        FAILS=$((FAILS + 1))
    fi
done
echo "failures: $FAILS of 12"
```

Expected: `failures: 0 of 12`. Before this task the same loop failed three times
in eight runs.

- [ ] **Step 9: Commit**

```bash
drift check && git diff --check
git add chat-service-composite/src/test
git commit -F - <<'MSG'
test: wait for the durable record, not for the running flag (CHAT-cxduiwjj)

Two tests read the job store as soon as running turned false. finishRun
clears that flag before it writes the record, so both tests raced the
durable write and failed three times in eight runs.

awaitDurableOutcome waits for both facts under two bounds. The outer bound
covers the run, and the inner bound covers the interval between
running=false and the durable write. Each failure message names the bound
that expired, because an unfinished run and a missing record are different
reports.

Three tests are new. One holds the window open with a delayed finishJob
publisher and asserts the contract directly. One proves that a failed
terminal write leaves no record and expires the inner bound. One states that
RELEASED is terminal and is not success.

Evidence, measured on 2026-09-16.

- The class passes 12 times out of 12. The same loop failed 3 of 8 before.
- 22 tests in MessageReindexServiceImplTests.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Task 3: The launch gate and the operator guide

**Files:**
- Modify: `shell-scripts/vector/gate-embedding-launch.sh`
- Modify: `docs/VECTOR-RECALL-API.md`

**Interfaces:**
- Consumes: the trigger result from Task 1.
- Produces: no code.

- [ ] **Step 1: Change the gate trigger step**

Modify `shell-scripts/vector/gate-embedding-launch.sh`. Replace step 8.

```bash
echo "8. Trigger a rebuild, under Basic credentials."
# The timestamp comes first. The trigger never promises the job key, so the
# gate correlates by start instant. This needs the gate clock and the
# application clock to agree, and both run on this host.
TRIGGER_AT=$("$PYTHON" -c "import time; print(time.time())")
curl -sS -u "$ACTUATOR_USER:$ACTUATOR_PASS" \
    -X POST "http://127.0.0.1:$APP_PORT/actuator/vectorindex" > "$WORK/trigger.json" \
    || fail "the trigger did not answer"

ACCEPTED=$("$PYTHON" -c "
import json
print(json.load(open('$WORK/trigger.json')).get('accepted'))
")
[ "$ACCEPTED" = "True" ] \
    || { cat "$WORK/trigger.json"; fail "the trigger was rejected, so no run started"; }
echo "   ok, the trigger was accepted"
```

- [ ] **Step 2: Change the poll step to two bounds**

Replace step 9.

```bash
echo "9. Wait for the run, then for the durable record."
# Two bounds. The outer one covers the rebuild, which grows with the corpus.
# The inner one covers the interval between running=false and the durable
# write, which is one event and one store write.
OUTER=60
INNER=15
for _ in $(seq 1 "$OUTER"); do
    curl -sS -u "$ACTUATOR_USER:$ACTUATOR_PASS" \
        "http://127.0.0.1:$APP_PORT/actuator/vectorindex" > "$WORK/status.json"
    RUNNING=$("$PYTHON" -c "
import json
print(json.load(open('$WORK/status.json'))['status']['running'])
" 2>/dev/null)
    [ "$RUNNING" = "False" ] && break
    sleep 2
done
[ "$RUNNING" = "False" ] || { cat "$WORK/status.json"; fail "the outer bound expired and the run did not finish"; }

for _ in $(seq 1 "$INNER"); do
    curl -sS -u "$ACTUATOR_USER:$ACTUATOR_PASS" \
        "http://127.0.0.1:$APP_PORT/actuator/vectorindex" > "$WORK/status.json"
    TERMINAL=$("$PYTHON" -c "
import json
jobs = [j for j in json.load(open('$WORK/status.json'))['jobs']
        if j['startedAt'] >= $TRIGGER_AT and j['outcome'] != 'RUNNING']
print(jobs[0]['outcome'] if jobs else 'NONE')
" 2>/dev/null)
    [ "$TERMINAL" != "NONE" ] && break
    sleep 1
done
[ "$TERMINAL" != "NONE" ] \
    || { cat "$WORK/status.json"; fail "the inner bound expired and the run left no durable record"; }
echo "   ok, the durable record reports $TERMINAL"
```

- [ ] **Step 3: Require a successful outcome and the identity**

Replace step 10.

```bash
echo "10. Assert that the newest job succeeded and carries the identity."
# RELEASED and FAILED both end the wait. Only SUCCEEDED proves the rebuild
# did its work, and this gate asserts hits below.
[ "$TERMINAL" = "SUCCEEDED" ] \
    || { cat "$WORK/status.json"; fail "the newest job reports $TERMINAL, expected SUCCEEDED"; }

JOB_IDENTITY=$("$PYTHON" -c "
import json
jobs = [j for j in json.load(open('$WORK/status.json'))['jobs']
        if j['startedAt'] >= $TRIGGER_AT and j['outcome'] != 'RUNNING']
jobs.sort(key=lambda j: j['startedAt'], reverse=True)
print(jobs[0].get('embeddingIdentity'))
")
[ "$JOB_IDENTITY" = "$IDENTITY" ] \
    || fail "the newest job carries identity '$JOB_IDENTITY', expected '$IDENTITY'"
echo "   ok, the job carries $IDENTITY"
```

`startedAt` arrives as a decimal number of seconds. The earlier gate read
`jobs[0]` with no filter, so it accepted the RUNNING record of this run, and it
would have accepted a SUCCEEDED record of an earlier one.

- [ ] **Step 4: Run the gate**

```bash
./shell-scripts/vector/gate-embedding-launch.sh
```

Expected: exit status 0, and the last line reads `PASS`.

- [ ] **Step 5: Prove the gate rejects a rejected trigger**

Add a second trigger before the first one, so the second call is refused.
Insert this line directly above the `TRIGGER_AT` assignment, run the gate, then
remove the line.

```bash
curl -sS -u "$ACTUATOR_USER:$ACTUATOR_PASS" -X POST "http://127.0.0.1:$APP_PORT/actuator/vectorindex" > /dev/null
```

Expected: exit status 1, and the message reads `the trigger was rejected, so no
run started`. This mutation can pass when the first rebuild finishes before the
second call, so repeat it up to three times before you treat a pass as a
result. Record what happened.

- [ ] **Step 6: Update the operator guide**

Modify `docs/VECTOR-RECALL-API.md`. Change the trigger answer in the second
scenario, and give both scenarios the two bounds.

```bash
curl -sS -u actuator:actuator -X POST http://localhost:8080/actuator/vectorindex
```

The answer now carries `accepted` beside the status.

```json
{
  "accepted": true,
  "status": {
    "phase": "REBUILDING",
    "running": true,
    "complete": false,
    "activeJob": null,
    "coveringJob": null
  }
}
```

**A false `accepted` means another run holds the claim.** This call then starts
nothing and creates no job. Do not wait for a new job after a rejected trigger.

**`running=false` does not prove that the durable record is written.** The state
decides the outcome first, so that a failed write cannot leave a run active
forever. Wait for both facts, under two bounds.

```bash
# Take the instant before the trigger. The trigger never promises the job key,
# so a reader correlates by start instant. This needs compatible clocks.
TRIGGER_AT=$(date +%s)

# The outer bound covers the rebuild, which grows with the corpus.
until curl -sS -u actuator:actuator http://localhost:8080/actuator/vectorindex \
  | jq -e '.status.running == false' > /dev/null; do sleep 1; done

# The inner bound covers the durable write.
until curl -sS -u actuator:actuator http://localhost:8080/actuator/vectorindex \
  | jq -e --argjson t "$TRIGGER_AT" \
    '[.jobs[] | select(.startedAt >= $t and .outcome != "RUNNING")] | length > 0' \
    > /dev/null; do sleep 1; done
```

**Bound each loop.** An unbounded loop hangs when a run stalls, or when a
terminal write fails and leaves no record at all. An expired outer bound says
the run did not finish. An expired inner bound says the run ended with no
durable record.

**Only `SUCCEEDED` proves the rebuild did its work.** `FAILED` and `RELEASED`
end the wait and do not.

- [ ] **Step 7: Commit**

```bash
drift check && git diff --check
git add shell-scripts/vector/gate-embedding-launch.sh docs/VECTOR-RECALL-API.md
git commit -F - <<'MSG'
test: make the gate and the guide wait for the durable record (CHAT-cxduiwjj)

The gate polled until running was false and then read jobs[0]. That record
was the RUNNING one of this run, so the gate read the right identity from
the wrong record, and an earlier SUCCEEDED job would also have satisfied it.

The gate now checks that its trigger was accepted, takes an instant before
the trigger, and correlates the newest job by that instant. It bounds the
run and the durable write separately, and each failure names the bound that
expired. It requires SUCCEEDED, because RELEASED and FAILED end a wait
without proving any work.

The operator guide carries the same two bounds, the new trigger answer, and
the rule that only SUCCEEDED proves a rebuild.

Evidence, measured on 2026-09-16.

- The gate exits 0, and PASS is the last line. The durable record reports
  SUCCEEDED.
- A rejected trigger answer fails the gate, and the message names it.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Task 4: The gates and the register

**Files:**
- Modify: `forward-register.md`

- [ ] **Step 1: Run every gate**

```bash
LOG=$(mktemp -d)
STATUS=0
run_gate() {
    name="$1"; shift
    if "$@" > "$LOG/$name.txt" 2>&1; then code=0; else code=$?; STATUS=1; fi
    echo "$code  $name"
}
run_gate default      ./shell-scripts/build-health.sh
run_gate integration  ./shell-scripts/build-health.sh --integration
run_gate classpath    ./shell-scripts/check-production-classpath.sh
run_gate versions     ./shell-scripts/check-dependency-versions.sh
run_gate launch       ./shell-scripts/vector/gate-embedding-launch.sh
echo "logs: $LOG"
echo "overall: $STATUS"
[ "$STATUS" -eq 0 ] || { echo "AT LEAST ONE GATE FAILED. Do not commit."; false; }
```

Expected: five lines that each begin with `0`, then `overall: 0`.

- [ ] **Step 2: Record the work in the register**

Modify `forward-register.md`. Add one section that covers these facts.

- The defect: `running=false` did not mean the durable record was written, and
  three readers assumed it did.
- The contract: `running` and `complete` are in process, and `IndexJob.outcome`
  is durable.
- The one production change, and why the status could not carry it.
- The two bounds, and the two different reports they produce.
- That `RELEASED` is terminal and is not success.
- The measured flake rate before and after.

- [ ] **Step 3: Commit**

```bash
drift check && git diff --check
git add forward-register.md
git commit -F - <<'MSG'
docs: record the vector status contract in the register (CHAT-cxduiwjj)

Evidence, measured on 2026-09-16.

- Default build: 807 tests, 0 failures, 0 errors, 30 skipped.
- Integration build: 1026 tests, 0 failures, 0 errors, 55 skipped.
- Five gates each exit 0.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

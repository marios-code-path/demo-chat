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

    /**
     * A long queue builds behind an uncertain command while a backend is down.
     * The handler completes on the calling thread, so a drain that releases
     * the room by a direct call nests each next command on one stack.
     */
    @Test
    fun `final review - a long queue behind a released room drains without a deep stack`() {
        val release = CountDownLatch(1)
        val p = ScriptedHandler(PERSISTENCE) { command, _ ->
            if (command.requestId == "r-head" && release.count > 0) Mono.error(UncertainOutcomeException("held"))
            else Mono.empty()
        }
        val rt = runtime(listOf(p, ScriptedHandler(INDEX), ScriptedHandler(PUBSUB)))
        val head = submit(rt, "r-head")
        waitUntil { stateOf(rt, head.commandId, PERSISTENCE).state == BackendState.UNCERTAIN }
        val queued = (1..5000).map { submit(rt, "r-queued-$it") }
        waitUntil(Duration.ofSeconds(20)) { stateOf(rt, queued.last().commandId, INDEX).state == BackendState.SUCCEEDED }

        release.countDown()
        waitUntil(Duration.ofSeconds(30)) { stateOf(rt, queued.last().commandId, PERSISTENCE).state == BackendState.SUCCEEDED }
        assertThat(queued.count { stateOf(rt, it.commandId, PERSISTENCE).state == BackendState.SUCCEEDED }).isEqualTo(5000)
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

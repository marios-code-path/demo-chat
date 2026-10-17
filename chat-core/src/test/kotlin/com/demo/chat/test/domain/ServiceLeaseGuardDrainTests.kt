package com.demo.chat.test.domain

import com.demo.chat.domain.*
import com.demo.chat.service.core.UserIndexService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import java.lang.reflect.Proxy
import java.time.Duration
import kotlin.concurrent.thread

/** A clean close drains the store writes before the release. See `CHAT-lswjobhz`, plan review item 4. */
class ServiceLeaseGuardDrainTests {

    private val store = ServiceLeaseGuardTests.FakeLeaseStore()
    private val scheduler = ManualClaimScheduler()
    private val context = GenericApplicationContext().apply { refresh() }
    private val userIndex = Proxy.newProxyInstance(
        UserIndexService::class.java.classLoader, arrayOf(UserIndexService::class.java)
    ) { _, _, _ -> null }

    private fun guard(gate: StoreWriteGate) = ServiceLeaseGuard(
        store, { mapOf("userIndex" to userIndex) }, gate, NodeId(7), RuntimeOwnerId.generate("test"),
        NodeIdClaimProperties(null, null, null, null), context, scheduler, clock = { 0L },
    )

    /** Starts a write that ends when the test emits. The write adds `write-end` to the store calls. */
    private fun activeWrite(gate: StoreWriteGate): Sinks.Empty<Void> {
        val done = Sinks.empty<Void>()
        gate.admit { done.asMono().doOnSuccess { store.calls.add("write-end") } }.subscribe()
        return done
    }

    /**
     * Waits until the guard has closed the gate. A probe write passes an open
     * gate and is refused by a closed one. The wait has no fixed delay, so a
     * slow machine only makes it longer.
     */
    private fun awaitClosed(gate: StoreWriteGate) {
        val end = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (System.nanoTime() < end) {
            val refused = runCatching { gate.admit { Mono.empty<Void>() }.block() }.exceptionOrNull()
            if (refused is StoreWriteRefusedException) return
            Thread.sleep(10)
        }
        throw AssertionError("the guard did not close the write gate within 30 s")
    }

    @Test
    fun `stop waits for an active write and then releases`() {
        val gate = StoreWriteGate(Duration.ofSeconds(60))
        val guard = guard(gate).apply { start() }
        val done = activeWrite(gate)

        val stopper = thread { guard.stop() }
        awaitClosed(gate)
        assertThat(store.calls).describedAs("the calls while the write is active").doesNotContain("release:index:user")

        done.tryEmitEmpty()
        stopper.join(30_000)
        assertThat(store.calls).endsWith("write-end", "release:index:user")
    }

    @Test
    fun `destroy drains before it releases`() {
        val gate = StoreWriteGate(Duration.ofSeconds(60))
        val guard = guard(gate).apply { start() }
        val done = activeWrite(gate)

        val destroyer = thread { guard.destroy() }
        awaitClosed(gate)
        assertThat(store.calls).doesNotContain("release:index:user")

        done.tryEmitEmpty()
        destroyer.join(30_000)
        assertThat(store.calls).endsWith("write-end", "release:index:user")
    }

    @Test
    fun `a write after the close starts is refused`() {
        val gate = StoreWriteGate(Duration.ofSeconds(5))
        guard(gate).apply { start() }.stop()

        StepVerifier.create(gate.admit { Mono.empty<Void>() }).expectError(StoreWriteRefusedException::class.java).verify()
    }

    @Test
    fun `a drain timeout keeps the leases`() {
        val gate = StoreWriteGate(Duration.ofMillis(300))
        val guard = guard(gate).apply { start() }
        gate.admit { Mono.never<Void>() }.subscribe()

        guard.stop()

        assertThat(store.calls.filter { it.startsWith("release") }).isEmpty()
        assertThat(scheduler.periodic == null).describedAs("the renew loop is gone after the timeout").isTrue()
        guard.destroy()
        assertThat(store.calls.filter { it.startsWith("release") }).describedAs("a second drain").isEmpty()
    }
}

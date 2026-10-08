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
import reactor.test.StepVerifier
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
        StepVerifier.create(bus.submit(submission(text = "changed")))
            .expectError(RequestConflictException::class.java)
            .verify()
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
        StepVerifier.create(bus().submit(submission(requestId = "has space")))
            .expectError(InvalidRequestIdException::class.java)
            .verify()
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

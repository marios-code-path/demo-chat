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
        assertThat(waiting.get(2, TimeUnit.SECONDS)!!.outcome).isEqualTo(CallerOutcome.COMPLETED)
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

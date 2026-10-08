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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
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
        StepVerifier.create(sut.bus.submit(submission("contract-3", "changed")))
            .expectError(RequestConflictException::class.java)
            .verify(wait)
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

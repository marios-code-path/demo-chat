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

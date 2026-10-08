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

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

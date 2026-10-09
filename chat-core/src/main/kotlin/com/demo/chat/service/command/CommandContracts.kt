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

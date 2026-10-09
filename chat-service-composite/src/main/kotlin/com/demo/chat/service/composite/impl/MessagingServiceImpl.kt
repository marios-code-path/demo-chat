package com.demo.chat.service.composite.impl

import com.demo.chat.domain.AccessDeniedException
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.CommandStatusRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.MessageSubmitRequest
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.command.BackendState
import com.demo.chat.domain.command.CallerOutcome
import com.demo.chat.domain.command.CommandIncompleteException
import com.demo.chat.domain.command.CommandPendingException
import com.demo.chat.domain.command.CommandStatus
import com.demo.chat.domain.command.CommandSubmission
import com.demo.chat.domain.command.CompletionRequirement
import com.demo.chat.domain.command.MessageSendResult
import com.demo.chat.domain.command.SenderMismatchException
import com.demo.chat.domain.command.SubmitterUnavailableException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.command.CommandCompletionService
import com.demo.chat.service.command.DomainCommandBus
import com.demo.chat.service.command.SubmitterIdentity
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.command.publication.RoomPublications
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessagePersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

open class MessagingServiceImpl<T : Any, V, Q>(
    private val messageIndex: MessageIndexService<T, V, Q>,
    private val messagePersistence: MessagePersistence<T, V>,
    private val publications: RoomPublications<T, V>,
    private val topicIdToQuery: Function<ByIdRequest<T>, Q>,
    private val verifier: KeyVerifier<T>,
    private val commandBus: DomainCommandBus<T, V>,
    private val completions: CommandCompletionService<T>,
    private val submitter: SubmitterIdentity<T>?,
    private val requirement: CompletionRequirement,
    private val timeout: Duration,
    private val requestIds: () -> String = { UUID.randomUUID().toString() },
) : ChatMessageService<T, V> {

    /**
     * History, then replay, then live. Decisions 8 and 9 of the spec. The
     * boundary task subscribes first. The listener drops each message ID that
     * it already emitted, for the whole subscription.
     */
    override fun listenTopic(req: ByIdRequest<T>): Flux<out Message<T, V>> =
        verifier.resolve(req.id, ChatDomain.MESSAGE_TOPIC).flatMapMany {
            publications.subscribe(req.id).flatMapMany { live ->
                history(req).collectList()
                    .flatMapMany { stored ->
                        val seen = ConcurrentHashMap.newKeySet<T>()
                        val merged = (stored + live.replay).sortedBy { it.key.timestamp }
                        Flux.concat(Flux.fromIterable(merged), live.messages).filter { seen.add(it.key.id) }
                    }
                    .doFinally { live.close() }
            }
        }

    /** The history half of [listenTopic], with no listener. The topic resolves first. D7. */
    override fun listMessages(req: ByIdRequest<T>): Flux<out Message<T, V>> =
        verifier.resolve(req.id, ChatDomain.MESSAGE_TOPIC).flatMapMany { history(req) }

    private fun history(req: ByIdRequest<T>): Flux<out Message<T, V>> =
        messageIndex
            .findBy(topicIdToQuery.apply(req))
            .collectList()
            .flatMapMany { messageKeys -> messagePersistence.byIds(messageKeys) }

    override fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>> =
        verifier.resolve(req.id, ChatDomain.MESSAGE)
            .flatMap { messagePersistence.get(it.key) }

    override fun submit(req: MessageSubmitRequest<T, V>): Mono<out MessageSendResult<T>> =
        owner().flatMap { owner -> admitAndWait(owner, req.requestId, req.dest, req.msg) }

    /**
     * The legacy adapter. It gives no retry safety, because each call creates a
     * new request ID. It rejects a `from` that is not the authenticated user.
     */
    override fun send(req: MessageSendRequest<T, V>): Mono<out Key<T>> =
        owner().flatMap { owner ->
            if (owner.id != req.from) Mono.error(SenderMismatchException(req.from, owner.id))
            else admitAndWait(owner, requestIds(), req.dest, req.msg)
        }.flatMap { result ->
            when (result.outcome) {
                CallerOutcome.COMPLETED, CallerOutcome.ACCEPTED ->
                    Mono.just(Key.of(result.receipt.messageKey.id, result.receipt.messageKey.root))
                CallerOutcome.PENDING -> Mono.error(CommandPendingException(result.receipt.commandId))
                CallerOutcome.INCOMPLETE -> Mono.error(
                    CommandIncompleteException(
                        result.receipt.commandId,
                        result.backends.filter { it.key in requirement.backends && it.value.state == BackendState.FAILED }.keys,
                    )
                )
            }
        }

    override fun commandStatus(req: CommandStatusRequest): Mono<out CommandStatus<T>> =
        owner().flatMap { owner -> completions.status(req.commandId).filter { it.owner == owner.id } }
            .switchIfEmpty(Mono.error(NotFoundException))

    private fun owner(): Mono<Key<T>> =
        (submitter?.current() ?: Mono.error(SubmitterUnavailableException()))
            .switchIfEmpty(Mono.error(AccessDeniedException))

    private fun admitAndWait(owner: Key<T>, requestId: String, dest: T, content: V): Mono<MessageSendResult<T>> =
        verifier.resolve(owner.id, ChatDomain.USER)
            .then(verifier.resolve(dest, ChatDomain.MESSAGE_TOPIC))
            .then(Mono.defer { commandBus.submit(CommandSubmission(owner.id, requestId, owner.id, dest, content)) })
            .flatMap { receipt ->
                if (requirement.backends.isEmpty()) Mono.just(MessageSendResult(receipt, CallerOutcome.ACCEPTED, emptyMap()))
                else completions.await(receipt.commandId, requirement, timeout)
            }
}

package com.demo.chat.service.composite.impl

import com.demo.chat.domain.AccessDeniedException
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.CommandStatusRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyInputException
import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageImportRequest
import com.demo.chat.domain.MessagePointerRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.MessageSubmitRequest
import com.demo.chat.domain.NoSuchPointerTargetException
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.PointerMessage
import com.demo.chat.domain.PointerToPointerException
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.BackendState
import com.demo.chat.domain.command.CallerOutcome
import com.demo.chat.domain.command.CommandIncompleteException
import com.demo.chat.domain.command.CommandOperation
import com.demo.chat.domain.command.CommandPendingException
import com.demo.chat.domain.command.CommandStatus
import com.demo.chat.domain.command.CommandSubmission
import com.demo.chat.domain.command.CompletionRequirement
import com.demo.chat.domain.command.MessageSendResult
import com.demo.chat.domain.command.SenderMismatchException
import com.demo.chat.domain.command.SubmitterUnavailableException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.command.CommandCompletionService
import com.demo.chat.service.command.DomainCommandBus
import com.demo.chat.service.command.ImportedMessageId
import com.demo.chat.service.command.PointerTargetAccess
import com.demo.chat.service.command.SubmitterIdentity
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.command.publication.RoomPublications
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.core.TopicPersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
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
    private val typeUtil: TypeUtil<T>,
    private val requestIds: () -> String = { UUID.randomUUID().toString() },
    private val rootKeys: RootKeys<T> = RootKeys(),
    private val clock: Clock = Clock.systemUTC(),
    private val pointerTargets: PointerTargetAccess<T>? = null,
    private val topicPersistence: TopicPersistence<T>? = null,
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
                        val merged = (stored + live.replay).sortedWith(Comparator(::compareMessages))
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
            .flatMapMany { messageKeys ->
                messagePersistence.byIds(messageKeys).collectList().flatMapMany { messages ->
                    Flux.fromIterable(messages.sortedWith(Comparator(::compareMessages)))
                }
            }

    private fun compareMessages(left: Message<T, V>, right: Message<T, V>): Int =
        left.key.timestamp.compareTo(right.key.timestamp).let { byTime ->
            if (byTime != 0) byTime else typeUtil.compare(left.key.id, right.key.id)
        }

    override fun messageById(req: ByIdRequest<T>): Mono<out Message<T, V>> =
        verifier.resolve(req.id, ChatDomain.MESSAGE)
            .flatMap { messagePersistence.get(it.key) }

    override fun submit(req: MessageSubmitRequest<T, V>): Mono<out MessageSendResult<T>> =
        owner().flatMap { owner -> admitAndWait(owner, req.requestId, req.dest, req.msg) }

    /**
     * The checks of the spec, steps 1 and 3 to 7. Step 2, `FORWARD` on the
     * host room, is the `@PreAuthorize` of `MessageServiceAccess`. See
     * `CHAT-erkoxybm`.
     */
    override fun pointer(req: MessagePointerRequest<T>): Mono<out MessageSendResult<T>> =
        owner().flatMap { owner ->
            pointerTarget(req).flatMap { target ->
                // A pointer carries no content. Every composition binds V to String.
                @Suppress("UNCHECKED_CAST")
                admitAndWait(owner, req.requestId, req.dest, "" as V, CommandOperation.RECORD_POINTER, target)
            }
        }

    /**
     * The target key, when the caller can read it. A missing target and an
     * unreadable target give the same error. The id converts exactly, because
     * a JSON body cannot carry the key type.
     */
    private fun pointerTarget(req: MessagePointerRequest<T>): Mono<Key<T>> = Mono.defer {
        val domain = ChatDomain.parse(req.targetDomain)?.takeIf { it in POINTER_TARGETS }
            ?: return@defer Mono.error<Key<T>>(
                KeyInputException("A pointer target domain must be Message or MessageTopic. The value is ${req.targetDomain}.")
            )
        val access = pointerTargets ?: return@defer Mono.error<Key<T>>(AccessDeniedException)
        val targetId = typeUtil.exactFrom(req.targetId as Any)
        val missing = NoSuchPointerTargetException(domain)
        verifier.resolve(targetId, domain)
            .onErrorMap(KeyVerificationException::class.java) { missing }
            .flatMap<Key<T>> { verified ->
                if (domain == ChatDomain.MESSAGE_TOPIC) {
                    // The registry keeps the key of a deleted room, so the store row decides.
                    val topic = topicPersistence?.get(verified.key) ?: Mono.empty()
                    topic.flatMap { access.canSubscribe(verified.key.id) }.filter { it }.map { verified.key }
                } else {
                    // A Cassandra removal keeps the row and sets record to false.
                    messagePersistence.get(verified.key).filter { it.record }.flatMap { target ->
                        access.canSubscribe(target.key.dest).filter { it }.flatMap {
                            if (target is PointerMessage<*>) Mono.error<Key<T>>(PointerToPointerException())
                            else Mono.just(verified.key)
                        }
                    }
                }
            }
            .switchIfEmpty(Mono.error(missing))
    }

    override fun importMessage(req: MessageImportRequest<T, V>): Mono<out Message<T, V>> =
        owner().flatMap { owner ->
            if (owner.id != rootKeys.admin().id) Mono.error(AccessDeniedException)
            else importAsAdmin(req)
        }

    /**
     * The id is derived from the room id and the room-scoped source id. A live
     * row under that id answers a rerun, and nothing is submitted. Only a `UUID`
     * key type holds the derived id, so another key type is refused before
     * admission. See `CHAT-bwzjstoh`.
     *
     * The read and the submission are not atomic. Two concurrent imports of one
     * source can both miss the read. They share one request id, so the bus of
     * one server process admits one command for both.
     */
    private fun importAsAdmin(req: MessageImportRequest<T, V>): Mono<out Message<T, V>> = Mono.defer {
        when {
            req.messageId.isBlank() -> Mono.error(IllegalArgumentException("An imported message needs a source message ID."))
            req.from == rootKeys.anon().id -> Mono.error(AccessDeniedException)
            req.timestamp.isAfter(clock.instant()) -> Mono.error(IllegalArgumentException("An imported message time cannot be in the future."))
            else -> {
                val timestamp = req.timestamp.truncatedTo(ChronoUnit.MILLIS)
                verifier.resolve(req.from, ChatDomain.USER)
                    .then(verifier.resolve(req.dest, ChatDomain.MESSAGE_TOPIC))
                    .then(Mono.fromCallable { ImportedMessageId.deriveFor(typeUtil, req.dest, req.messageId) })
                    .flatMap { derived -> importDerived(req, timestamp, derived) }
            }
        }
    }

    private fun importDerived(req: MessageImportRequest<T, V>, timestamp: Instant, derived: T): Mono<out Message<T, V>> =
        // The registry refuses an id that P never registered, so that id has no row.
        verifier.resolve(derived, ChatDomain.MESSAGE)
            .onErrorResume(KeyVerificationException::class.java) { Mono.empty() }
            .flatMap { verified -> messagePersistence.get(verified.key) }
            // A Cassandra removal keeps the row and sets record to false.
            .filter { it.record }
            .map { it as Message<T, V> }
            .switchIfEmpty(Mono.defer {
                val submission = CommandSubmission(
                    owner = rootKeys.admin().id,
                    requestId = "import:${req.messageId}",
                    sender = req.from,
                    dest = req.dest,
                    content = req.msg,
                    operation = CommandOperation.IMPORT_MESSAGE,
                    timestamp = timestamp,
                    publish = req.publish,
                    messageId = derived,
                )
                commandBus.submit(submission)
                    .flatMap { receipt ->
                        val waitFor = requirement.copy(
                            backends = if (req.publish) requirement.backends else requirement.backends - BackendId.PUBSUB
                        )
                        val stored = Message.create(receipt.messageKey, req.msg, true)
                        if (waitFor.backends.isEmpty()) Mono.just(stored)
                        else completions.await(receipt.commandId, waitFor, timeout).flatMap { result ->
                            when (result.outcome) {
                                CallerOutcome.COMPLETED, CallerOutcome.ACCEPTED -> Mono.just(stored)
                                CallerOutcome.PENDING -> Mono.error(CommandPendingException(result.receipt.commandId))
                                CallerOutcome.INCOMPLETE -> Mono.error(
                                    CommandIncompleteException(
                                        result.receipt.commandId,
                                        result.backends.filter { it.key in waitFor.backends && it.value.state == BackendState.FAILED }.keys,
                                    )
                                )
                            }
                        }
                    }
            })

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

    private fun admitAndWait(
        owner: Key<T>,
        requestId: String,
        dest: T,
        content: V,
        operation: CommandOperation = CommandOperation.RECORD_MESSAGE,
        target: Key<T>? = null,
    ): Mono<MessageSendResult<T>> {
        // A pointer has no V obligation, so the caller does not wait for V. CHAT-erkoxybm.
        val waitFor = if (operation == CommandOperation.RECORD_POINTER)
            requirement.copy(backends = requirement.backends - BackendId.VECTOR) else requirement
        return verifier.resolve(owner.id, ChatDomain.USER)
            .then(verifier.resolve(dest, ChatDomain.MESSAGE_TOPIC))
            .then(Mono.defer {
                commandBus.submit(CommandSubmission(owner.id, requestId, owner.id, dest, content, operation, target = target))
            })
            .flatMap { receipt ->
                if (waitFor.backends.isEmpty()) Mono.just(MessageSendResult(receipt, CallerOutcome.ACCEPTED, emptyMap()))
                else completions.await(receipt.commandId, waitFor, timeout)
            }
    }

    private companion object {
        val POINTER_TARGETS = setOf(ChatDomain.MESSAGE, ChatDomain.MESSAGE_TOPIC)
    }
}

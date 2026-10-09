package com.demo.chat.test.service.composite.command

import com.demo.chat.config.CommandBusSettings
import com.demo.chat.domain.Key
import com.demo.chat.domain.MapRequestConverters
import com.demo.chat.domain.Message
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CompletionRequirement
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.pubsub.memory.impl.MemoryTopicPubSubService
import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.command.SubmitterIdentity
import com.demo.chat.service.composite.command.handler.MessageIndexHandler
import com.demo.chat.service.composite.command.handler.MessagePersistenceHandler
import com.demo.chat.service.composite.command.handler.MessagePubSubHandler
import com.demo.chat.service.composite.command.memory.MemoryCommandRuntime
import com.demo.chat.service.composite.command.publication.RoomPublications
import com.demo.chat.service.composite.impl.MessagingServiceImpl
import com.demo.chat.service.core.KeyAllocator
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.test.key.FakeKeyServices
import com.demo.chat.test.service.composite.FakeMessageIndex
import com.demo.chat.test.service.composite.FakeMessagePersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/** A handler that waits on [gate] before it runs. An open gate is `Mono.empty()`. */
class GatedHandler<T, V>(private val delegate: DomainCommandHandler<T, V>, private val gate: Mono<Void>) :
    DomainCommandHandler<T, V> {
    override val descriptor = delegate.descriptor
    override fun handle(command: AcceptedCommand<T, V>): Mono<Void> = gate.then(Mono.defer { delegate.handle(command) })
}

/** Counts live subscribers, so a test can prove that a cancel disposes them. */
class CountingPubSub(private val delegate: TopicPubSubService<Long, String>) : TopicPubSubService<Long, String> by delegate {
    val subscribers = AtomicInteger()
    override fun listenTo(topic: Long): Flux<out Message<Long, String>> =
        delegate.listenTo(topic)
            .doOnSubscribe { subscribers.incrementAndGet() }
            .doFinally { subscribers.decrementAndGet() }
}

internal class MessagingStack(
    requirement: CompletionRequirement = CompletionRequirement(setOf(BackendId.PERSISTENCE, BackendId.INDEX)),
    timeout: Duration = Duration.ofSeconds(5),
    submitterKey: Key<Long>? = null,
    useSubmitter: Boolean = true,
) : AutoCloseable {
    val roots = CommandFixtures.ROOTS
    val registry = FakeKeyServices.long(roots)
    val user: Key<Long> = registry.register(SENDER, ChatDomain.USER)
    val room: Key<Long> = registry.register(ROOM, ChatDomain.MESSAGE_TOPIC)
    val persistence = FakeMessagePersistence()
    val index = FakeMessageIndex()
    val pubsub = CountingPubSub(MemoryTopicPubSubService<Long, String>())
    val publications = RoomPublications(pubsub)
    val persistenceGate = Sinks.empty<Void>()
    val indexGate = Sinks.empty<Void>()
    val pubsubGate = Sinks.empty<Void>()
    var gatePersistence = false
    var gateIndex = false
    var gatePubsub = false

    private fun gate(flag: () -> Boolean, sink: Sinks.Empty<Void>): Mono<Void> = Mono.defer { if (flag()) sink.asMono() else Mono.empty() }

    val runtime = MemoryCommandRuntime(
        KeyAllocator(LongKeyGenerator(1), roots), TypeUtil.LongUtil,
        listOf<DomainCommandHandler<Long, String>>(
            GatedHandler(MessagePersistenceHandler(registry, persistence), gate({ gatePersistence }, persistenceGate)),
            GatedHandler(MessageIndexHandler(index), gate({ gateIndex }, indexGate)),
            GatedHandler(MessagePubSubHandler(publications), gate({ gatePubsub }, pubsubGate)),
        ),
        CommandBusSettings(requirement, timeout, Duration.ofMillis(100)),
    )

    private val submitter = object : SubmitterIdentity<Long> {
        override fun current(): Mono<Key<Long>> = Mono.justOrEmpty(submitterKey ?: user)
    }

    val service = MessagingServiceImpl(
        messageIndex = index,
        messagePersistence = persistence,
        publications = publications,
        topicIdToQuery = MapRequestConverters()::topicIdToQuery,
        verifier = KeyVerifier(registry, roots),
        commandBus = runtime.bus,
        completions = runtime.completions,
        submitter = if (useSubmitter) submitter else null,
        requirement = requirement,
        timeout = timeout,
    )

    init {
        pubsub.open(ROOM).block()
    }

    override fun close() = runtime.close()

    companion object {
        const val SENDER = 10L
        const val ROOM = 100L
    }
}

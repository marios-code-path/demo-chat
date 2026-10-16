package com.demo.chat.test.service.composite.command

import com.demo.chat.config.CommandBusSettings
import com.demo.chat.domain.Key
import com.demo.chat.domain.MapRequestConverters
import com.demo.chat.domain.Message
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.domain.command.CommandSubmission
import com.demo.chat.domain.command.CompletionRequirement
import com.demo.chat.domain.command.Receipt
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.pubsub.memory.impl.MemoryTopicPubSubService
import com.demo.chat.service.UUIDKeyGenerator
import com.demo.chat.service.command.DomainCommandBus
import com.demo.chat.service.command.SubmitterIdentity
import com.demo.chat.service.composite.command.handler.MessageIndexHandler
import com.demo.chat.service.composite.command.handler.MessagePersistenceHandler
import com.demo.chat.service.composite.command.handler.MessagePubSubHandler
import com.demo.chat.service.composite.command.memory.MemoryCommandRuntime
import com.demo.chat.service.composite.command.publication.RoomPublications
import com.demo.chat.service.composite.impl.MessagingServiceImpl
import com.demo.chat.service.core.KeyAllocator
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.test.key.FakeKeyServices
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** A message store over `UUID` keys. `FakeMessagePersistence` holds `Long` keys only. */
internal class UuidMessageStore : MessagePersistence<UUID, String> {
    val added = CopyOnWriteArrayList<Message<UUID, String>>()

    override fun key(): Mono<out Key<UUID>> = Mono.error(UnsupportedOperationException("The bus assigns message keys."))
    override fun add(ent: Message<UUID, String>): Mono<Void> = Mono.fromRunnable { added.add(ent) }
    override fun rem(key: Key<UUID>): Mono<Void> = Mono.empty()
    override fun get(key: Key<UUID>): Mono<out Message<UUID, String>> =
        Mono.defer { Mono.justOrEmpty(added.firstOrNull { it.key.id == key.id }) }
    override fun all(): Flux<out Message<UUID, String>> = Flux.defer { Flux.fromIterable(added.toList()) }
    override fun byIds(keys: List<Key<UUID>>): Flux<out Message<UUID, String>> =
        Flux.defer { Flux.fromIterable(added.filter { message -> keys.any { it.id == message.key.id } }) }
}

/** A message index over `UUID` keys. `FakeMessageIndex` holds `Long` keys only. */
internal class UuidMessageIndex : MessageIndexService<UUID, String, Map<String, String>> {
    val added = CopyOnWriteArrayList<Message<UUID, String>>()

    override fun add(entity: Message<UUID, String>): Mono<Void> = Mono.fromRunnable { added.add(entity) }
    override fun rem(key: Key<UUID>): Mono<Void> = Mono.empty()
    override fun findBy(query: Map<String, String>): Flux<out Key<UUID>> =
        Flux.defer { Flux.fromIterable(added.filter { it.key.dest.toString() == query["topic"] }.map { it.key }) }
    override fun findUnique(query: Map<String, String>): Mono<out Key<UUID>> = findBy(query).singleOrEmpty()
}

/** Counts each submission, so a test can prove that a call submitted nothing. */
internal class CountingBus<T, V>(private val delegate: DomainCommandBus<T, V>) : DomainCommandBus<T, V> {
    val submissions = AtomicInteger()

    override fun submit(submission: CommandSubmission<T, V>): Mono<Receipt<T>> =
        Mono.defer { submissions.incrementAndGet(); delegate.submit(submission) }
}

/**
 * The composite over `UUID` keys, called as Admin. An import derives its id
 * from a room-scoped source id, and only a `UUID` key type holds that id. See
 * `CHAT-bwzjstoh`. [gatePersistence] holds `P` open until [persistenceGate]
 * completes.
 */
internal class UuidImportStack(timeout: Duration = Duration.ofSeconds(5)) : AutoCloseable {
    val roots = FakeKeyServices.uuidRoots()
    val registry = FakeKeyServices.uuid(roots)
    val sender: Key<UUID> = registry.register(UUID(0x7007L, 10L), ChatDomain.USER)
    val room: Key<UUID> = registry.register(UUID(0x7007L, 100L), ChatDomain.MESSAGE_TOPIC)
    val persistence = UuidMessageStore()
    val index = UuidMessageIndex()
    private val pubsub = MemoryTopicPubSubService<UUID, String>()
    val publications = RoomPublications(pubsub)
    val persistenceGate = Sinks.empty<Void>()
    var gatePersistence = false

    val runtime = MemoryCommandRuntime(
        KeyAllocator(UUIDKeyGenerator(1), roots), UUIDUtil(),
        listOf(
            GatedHandler(
                MessagePersistenceHandler(registry, persistence),
                Mono.defer { if (gatePersistence) persistenceGate.asMono() else Mono.empty() },
            ),
            MessageIndexHandler(index),
            MessagePubSubHandler(publications),
        ),
        CommandBusSettings(CompletionRequirement.parse("P,I"), timeout, Duration.ofMillis(100)),
    )

    val bus = CountingBus(runtime.bus)

    val admin = MessagingServiceImpl(
        index, persistence, publications, MapRequestConverters()::topicIdToQuery,
        KeyVerifier(registry, roots), bus, runtime.completions,
        object : SubmitterIdentity<UUID> {
            override fun current(): Mono<Key<UUID>> = Mono.just(roots.admin())
        },
        CompletionRequirement.parse("P,I"), timeout, UUIDUtil(),
        rootKeys = roots,
    )

    init {
        pubsub.open(room.id).block()
    }

    /** A room-scoped source id, the form that the importer sends. */
    fun sourceId(sourceUuid: UUID): String = "${room.id}:$sourceUuid"

    override fun close() = runtime.close()
}

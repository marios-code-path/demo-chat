package com.demo.chat.test.service.composite

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.NotFoundException
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.TopicPersistence
import com.demo.chat.service.core.TopicPubSubService
import reactor.core.publisher.Flux
import java.time.Duration
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks

/**
 * In-memory doubles for the composite vector tests.
 *
 * They are internal rather than private, because three tasks use them. A
 * private class cannot leave its own file.
 *
 * A double that takes [calls] appends its own name on each write, so a test
 * can assert the order in which services were called.
 */
internal class FakeTopicPersistence : TopicPersistence<Long> {
    val saved = mutableListOf<MessageTopic<Long>>()
    private var nextId = 500L
    override fun key(): Mono<out Key<Long>> = Mono.fromSupplier { Key.funKey(nextId++) }
    override fun add(ent: MessageTopic<Long>): Mono<Void> = Mono.fromRunnable { saved.add(ent) }
    override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { saved.removeIf { it.key == key } }
    // Every read defers. A real store reads when a caller subscribes, and a
    // fake that reads at assembly time would hand back a value from before an
    // earlier write, which makes correct serialization look broken.
    override fun get(key: Key<Long>): Mono<out MessageTopic<Long>> =
        Mono.defer { Mono.justOrEmpty(saved.firstOrNull { it.key == key }) }
    override fun all(): Flux<out MessageTopic<Long>> = Flux.defer { Flux.fromIterable(saved.toList()) }
}

internal class FakeTopicIndex : TopicIndexService<Long, Map<String, String>> {
    val saved = mutableListOf<MessageTopic<Long>>()
    override fun add(entity: MessageTopic<Long>): Mono<Void> = Mono.fromRunnable { saved.add(entity) }
    override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { saved.removeIf { it.key == key } }
    override fun findBy(query: Map<String, String>): Flux<out Key<Long>> =
        Flux.fromIterable(saved.filter { it.data == query["name"] }.map { it.key })
    override fun findUnique(query: Map<String, String>): Mono<out Key<Long>> = findBy(query).singleOrEmpty()
}

/**
 * Delivers to listeners, and refuses a topic nobody opened.
 *
 * A double that only records a send cannot show that a subscriber received
 * anything. The memory backend also answers a send on an unopened topic with
 * Object not Found, so this one does the same and keeps the open call
 * load bearing.
 */
internal class FakePubSub(private val calls: MutableList<String>? = null) : TopicPubSubService<Long, String> {
    val opened = mutableListOf<Long>()
    val sent = mutableListOf<Message<Long, String>>()
    private val sinks = mutableMapOf<Long, Sinks.Many<Message<Long, String>>>()

    override fun open(topicId: Long): Mono<Void> = Mono.fromRunnable {
        opened.add(topicId)
        sinks.getOrPut(topicId) { Sinks.many().replay().all() }
    }

    override fun close(topicId: Long): Mono<Void> = Mono.fromRunnable { sinks.remove(topicId) }
    override fun getByUser(uid: Long): Flux<Long> = Flux.empty()
    override fun getUsersBy(topicId: Long): Flux<Long> = Flux.empty()
    override fun subscribe(member: Long, topic: Long): Mono<Void> = Mono.empty()
    override fun unSubscribe(member: Long, topic: Long): Mono<Void> = Mono.empty()
    override fun unSubscribeAll(member: Long): Mono<Void> = Mono.empty()
    override fun unSubscribeAllIn(topic: Long): Mono<Void> = Mono.empty()

    override fun sendMessage(message: Message<Long, String>): Mono<Void> = Mono.defer {
        calls?.add("pubsub")
        val sink = sinks[message.key.dest]
            ?: return@defer Mono.error(NotFoundException)
        sent.add(message)
        sink.tryEmitNext(message)
        Mono.empty()
    }

    override fun listenTo(topic: Long): Flux<out Message<Long, String>> =
        sinks[topic]?.asFlux() ?: Flux.error(NotFoundException)

    override fun exists(topic: Long): Mono<Boolean> = Mono.just(sinks.containsKey(topic))
}

/**
 * The read waits on [readDelay] before it answers, so a caller can hold one
 * operation inside its read and start a second one.
 */
internal class FakeKeyValueStore(private val readDelay: Mono<Void> = Mono.empty()) : KeyValueStore<Long, Any> {
    val values = linkedMapOf<Long, KeyValuePair<Long, Any>>()
    override fun key(): Mono<out Key<Long>> = Mono.empty()
    override fun add(ent: KeyValuePair<Long, Any>): Mono<Void> = Mono.fromRunnable { values[ent.key.id] = ent }
    override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { values.remove(key.id) }
    override fun get(key: Key<Long>): Mono<out KeyValuePair<Long, Any>> =
        readDelay.then(Mono.defer { Mono.justOrEmpty(values[key.id]) })
    override fun all(): Flux<out KeyValuePair<Long, Any>> =
        Flux.defer { Flux.fromIterable(values.values.toList()) }
}

/**
 * [delay] holds the write open. A synchronous double cannot tell a chain that
 * waits for each step from one that starts them all at once, because both
 * subscribe in the same order.
 */
internal class FakeMessagePersistence(
    private val calls: MutableList<String>? = null,
    private val delay: Duration = Duration.ZERO,
    private val failure: Throwable? = null,
) : MessagePersistence<Long, String> {
    val added = mutableListOf<Message<Long, String>>()
    private var nextId = 900L
    override fun key(): Mono<out Key<Long>> = Mono.fromSupplier { Key.funKey(nextId++) }
    override fun add(ent: Message<Long, String>): Mono<Void> =
        Mono.delay(delay).then(
            Mono.defer {
                calls?.add("persistence")
                if (failure != null) {
                    Mono.error(failure)
                } else {
                    added.add(ent)
                    Mono.empty()
                }
            }
        )
    override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
    override fun get(key: Key<Long>): Mono<out Message<Long, String>> =
        Mono.defer { Mono.justOrEmpty(added.firstOrNull { it.key.id == key.id }) }
    override fun all(): Flux<out Message<Long, String>> = Flux.defer { Flux.fromIterable(added.toList()) }
    override fun byIds(keys: List<Key<Long>>): Flux<out Message<Long, String>> =
        Flux.defer { Flux.fromIterable(added.filter { message -> keys.any { it.id == message.key.id } }) }
}

internal class FakeMessageIndex(
    private val calls: MutableList<String>? = null,
    private val failOn: String? = null,
) : MessageIndexService<Long, String, Map<String, String>> {
    val added = mutableListOf<Message<Long, String>>()

    /** Fails every write, whatever the constructor said. */
    var failEvery = false

    override fun add(entity: Message<Long, String>): Mono<Void> = Mono.defer {
        calls?.add("index")
        if (failEvery || failOn == "index") {
            Mono.error(IllegalStateException("index is down"))
        } else {
            added.add(entity)
            Mono.empty()
        }
    }
    override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
    override fun findBy(query: Map<String, String>): Flux<out Key<Long>> =
        Flux.fromIterable(added.filter { it.key.dest.toString() == query["topic"] }.map { it.key })
    override fun findUnique(query: Map<String, String>): Mono<out Key<Long>> = findBy(query).singleOrEmpty()
}

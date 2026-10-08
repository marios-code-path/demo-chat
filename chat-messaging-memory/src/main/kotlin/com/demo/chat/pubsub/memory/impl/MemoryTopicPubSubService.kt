package com.demo.chat.pubsub.memory.impl

import com.demo.chat.domain.Message
import com.demo.chat.domain.NotFoundException
import com.demo.chat.service.core.TopicPubSubService
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import reactor.util.concurrent.Queues
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of TopicPubSubService.
 *
 * Uses Reactor [Sinks.Many] with multicast + backpressure buffering for per-topic
 * fan-out — the same reactive pattern as the Kafka implementation. This is the
 * correct best-practice for an in-memory RSocket pub/sub backend: messages are
 * pushed to all subscribers through a hot stream, and backpressure is handled
 * by buffering rather than dropping.
 *
 * Membership tracking uses [ConcurrentHashMap] with [newKeySet] for thread-safe
 * concurrent access — identical to the Kafka implementation.
 *
 * This replaces the legacy [ExampleReactiveStreamManager] which used deprecated
 * [DirectProcessor] / [ReplayProcessor] and had known resource-leak issues
 * (disposable management was broken).
 */
class MemoryTopicPubSubService<T : Any, V> : TopicPubSubService<T, V>, AutoCloseable {

    /** One room: its buffered sink and the internal subscriber that keeps the sink open. */
    private class RoomSink<T, V>(val sink: Sinks.Many<Message<T, V>>, val drain: Disposable)

    private val rooms: ConcurrentHashMap<T, RoomSink<T, V>> = ConcurrentHashMap()

    /**
     * Creates the sink and its internal subscriber together, once per room.
     * Auto-cancel is off, so the sink survives when every external listener
     * leaves. The internal subscriber requests without a limit and does no
     * work, so an empty room never fills the buffer. A slow external listener
     * can still fill it, and the send then fails as retryable.
     */
    private fun roomOf(topic: T): RoomSink<T, V> = rooms.computeIfAbsent(topic) {
        val sink = Sinks.many().multicast().onBackpressureBuffer<Message<T, V>>(Queues.SMALL_BUFFER_SIZE, false)
        RoomSink(sink, sink.asFlux().subscribe())
    }

    private fun release(topic: T) {
        rooms.remove(topic)?.let { room ->
            room.drain.dispose()
            room.sink.tryEmitComplete()
        }
    }

    internal fun hasRoomSink(topic: T): Boolean = rooms.containsKey(topic)

    internal fun roomSinkCount(): Int = rooms.size

    internal fun drainOf(topic: T): Disposable? = rooms[topic]?.drain

    /** Shutdown releases every room. A `@Bean` infers this method as its destroy method. */
    override fun close() {
        rooms.keys.toList().forEach(::release)
    }
    private val topicMembers: MutableMap<T, MutableSet<T>> = ConcurrentHashMap()
    private val memberTopics: MutableMap<T, MutableSet<T>> = ConcurrentHashMap()

    private fun topicExistsOrError(topicId: T): Mono<Boolean> =
        exists(topicId)
            .filter { it }
            .switchIfEmpty(Mono.error(NotFoundException))

    // --- TopicInventoryService ---

    override fun open(topicId: T): Mono<Void> =
        Mono.fromCallable {
            roomOf(topicId)
            topicMembers.getOrPut(topicId) { ConcurrentHashMap.newKeySet() }
        }.then()

    override fun close(topicId: T): Mono<Void> =
        unSubscribeAllIn(topicId)
            .then(Mono.fromCallable {
                release(topicId)
                topicMembers.remove(topicId)
            }.then())

    override fun getByUser(uid: T): Flux<T> =
        Flux.defer { Flux.fromIterable(memberTopics[uid] ?: emptySet()) }

    override fun getUsersBy(topicId: T): Flux<T> =
        Flux.defer { Flux.fromIterable(topicMembers[topicId] ?: emptySet()) }

    // --- PubSubService ---

    override fun subscribe(member: T, topic: T): Mono<Void> =
        topicExistsOrError(topic)
            .map {
                topicMembers.getOrPut(topic) { ConcurrentHashMap.newKeySet() }.add(member)
                memberTopics.getOrPut(member) { ConcurrentHashMap.newKeySet() }.add(topic)
            }.then()

    override fun unSubscribe(member: T, topic: T): Mono<Void> =
        Mono.fromCallable {
            topicMembers[topic]?.remove(member)
            memberTopics[member]?.remove(topic)
        }.then()

    override fun unSubscribeAll(member: T): Mono<Void> =
        Flux.fromIterable(memberTopics[member]?.toSet() ?: emptySet())
            .flatMap { topic -> unSubscribe(member, topic) }
            .subscribeOn(Schedulers.parallel())
            .then()

    override fun unSubscribeAllIn(topic: T): Mono<Void> =
        Flux.fromIterable(topicMembers[topic]?.toSet() ?: emptySet())
            .flatMap { member -> unSubscribe(member, topic) }
            .subscribeOn(Schedulers.parallel())
            .then()

    /**
     * `U` succeeds only on `EmitResult.OK`. Decision 13 of the spec. `OK` means
     * the sink accepted the emission. It does not mean a recipient received it.
     */
    override fun sendMessage(message: Message<T, V>): Mono<Void> =
        topicExistsOrError(message.key.dest)
            .flatMap {
                val sink = rooms[message.key.dest]?.sink ?: return@flatMap Mono.error<Void>(NotFoundException)
                when (val result = sink.tryEmitNext(message)) {
                    Sinks.EmitResult.OK -> Mono.empty()
                    Sinks.EmitResult.FAIL_OVERFLOW, Sinks.EmitResult.FAIL_NON_SERIALIZED ->
                        Mono.error(PublicationRetryableException(message.key.dest, result))
                    else -> Mono.error(PublicationRefusedException(message.key.dest, result))
                }
            }
            .then()

    /**
     * Each subscriber receives on its own worker, so a callback never runs on
     * the thread that emits. The room coordinator relies on that. Decision 9.
     */
    override fun listenTo(topic: T): Flux<out Message<T, V>> =
        roomOf(topic).sink.asFlux().publishOn(Schedulers.boundedElastic())

    override fun exists(topic: T): Mono<Boolean> =
        Mono.fromCallable { rooms.containsKey(topic) }
}

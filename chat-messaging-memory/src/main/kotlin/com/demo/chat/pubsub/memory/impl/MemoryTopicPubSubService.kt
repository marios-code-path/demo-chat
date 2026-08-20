package com.demo.chat.pubsub.memory.impl

import com.demo.chat.domain.Message
import com.demo.chat.domain.NotFoundException
import com.demo.chat.service.core.TopicPubSubService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
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
class MemoryTopicPubSubService<T, V> : TopicPubSubService<T, V> {

    private val sinks: MutableMap<T, Sinks.Many<Message<T, V>>> = ConcurrentHashMap()
    private val topicMembers: MutableMap<T, MutableSet<T>> = ConcurrentHashMap()
    private val memberTopics: MutableMap<T, MutableSet<T>> = ConcurrentHashMap()

    private fun topicExistsOrError(topicId: T): Mono<Boolean> =
        exists(topicId)
            .filter { it }
            .switchIfEmpty(Mono.error(NotFoundException))

    // --- TopicInventoryService ---

    override fun open(topicId: T): Mono<Void> =
        Mono.fromCallable {
            sinks.getOrPut(topicId) {
                Sinks.many().multicast().onBackpressureBuffer()
            }
            topicMembers.getOrPut(topicId) { ConcurrentHashMap.newKeySet() }
        }.then()

    override fun close(topicId: T): Mono<Void> =
        unSubscribeAllIn(topicId)
            .then(Mono.fromCallable {
                sinks.remove(topicId)?.tryEmitComplete()
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

    override fun sendMessage(message: Message<T, V>): Mono<Void> =
        topicExistsOrError(message.key.dest)
            .map {
                sinks[message.key.dest]?.tryEmitNext(message)
            }.then()

    override fun listenTo(topic: T): Flux<out Message<T, V>> =
        sinks.getOrPut(topic) {
            Sinks.many().multicast().onBackpressureBuffer()
        }.asFlux()

    override fun exists(topic: T): Mono<Boolean> =
        Mono.fromCallable { sinks.containsKey(topic) }
}

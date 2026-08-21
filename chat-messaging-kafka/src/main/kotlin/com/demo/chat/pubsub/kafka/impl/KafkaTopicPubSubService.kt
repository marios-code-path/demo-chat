package com.demo.chat.pubsub.kafka.impl

import com.demo.chat.domain.Message
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import reactor.kafka.receiver.KafkaReceiver
import reactor.kafka.receiver.ReceiverOptions
import java.util.concurrent.ConcurrentHashMap

class KafkaTopicPubSubService<T, V>(
    private val producer: ReactiveKafkaProducerTemplate<String, Message<T, V>>,
    private val admin: KafkaTopicAdmin<T>,
    private val typeUtil: TypeUtil<T>,
    private val receiverOptions: ReceiverOptions<String, Message<T, V>>,
) : TopicPubSubService<T, V> {

    private val sinks: MutableMap<T, Sinks.Many<Message<T, V>>> = ConcurrentHashMap()
    private val consumers: MutableMap<T, Disposable> = ConcurrentHashMap()
    private val topicMembers: MutableMap<T, MutableSet<T>> = ConcurrentHashMap()
    private val memberTopics: MutableMap<T, MutableSet<T>> = ConcurrentHashMap()

    private fun topicExistsOrError(topicId: T): Mono<Boolean> =
        exists(topicId)
            .filter { it }
            .switchIfEmpty(Mono.error(NotFoundException))

    // --- TopicInventoryService ---

    override fun open(topicId: T): Mono<Void> =
        admin.create(topicId)
            .then(Mono.fromCallable {
                val sink = sinks.getOrPut(topicId) {
                    Sinks.many().multicast().onBackpressureBuffer()
                }
                topicMembers.getOrPut(topicId) { ConcurrentHashMap.newKeySet() }

                if (!consumers.containsKey(topicId)) {
                    val topicName = typeUtil.toString(topicId)
                    val options = receiverOptions.subscription(setOf(topicName))
                    val disposable = KafkaReceiver.create(options)
                        .receive()
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe { record ->
                            sink.tryEmitNext(record.value())
                            record.receiverOffset().acknowledge()
                        }
                    consumers[topicId] = disposable
                }
            }.subscribeOn(Schedulers.boundedElastic()))
            .then()

    override fun close(topicId: T): Mono<Void> =
        unSubscribeAllIn(topicId)
            .then(Mono.fromCallable {
                consumers.remove(topicId)?.dispose()
                sinks.remove(topicId)?.tryEmitComplete()
                topicMembers.remove(topicId)
            }.subscribeOn(Schedulers.boundedElastic()))
            .then(admin.delete(topicId))

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
            .flatMap {
                producer.send(typeUtil.toString(message.key.dest), message)
            }.then()

    override fun listenTo(topic: T): Flux<out Message<T, V>> =
        sinks.getOrPut(topic) {
            Sinks.many().multicast().onBackpressureBuffer()
        }.asFlux()

    override fun exists(topic: T): Mono<Boolean> =
        admin.exists(topic)
}

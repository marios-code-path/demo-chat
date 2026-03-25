package com.demo.chat.pubsub.kafka.impl

import com.demo.chat.domain.Message
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap

class KafkaTopicPubSubService<T, V>(
    private val producer: ReactiveKafkaProducerTemplate<String, Message<T, V>>,
    private val admin: KafkaTopicAdmin<T>,
    private val typeUtil: TypeUtil<T>,
) : TopicPubSubService<T, V> {

    // topic → multicast sink
    private val sinks: MutableMap<T, Sinks.Many<Message<T, V>>> = ConcurrentHashMap()

    // topic → set of member ids
    private val topicMembers: MutableMap<T, MutableSet<T>> = ConcurrentHashMap()

    // member → set of topic ids
    private val memberTopics: MutableMap<T, MutableSet<T>> = ConcurrentHashMap()

    // --- TopicInventoryService ---

    override fun open(topicId: T): Mono<Void> = TODO()

    override fun close(topicId: T): Mono<Void> = TODO()

    override fun getByUser(uid: T): Flux<T> = TODO()

    override fun getUsersBy(topicId: T): Flux<T> = TODO()

    // --- PubSubService ---

    override fun subscribe(member: T, topic: T): Mono<Void> = TODO()

    override fun unSubscribe(member: T, topic: T): Mono<Void> = TODO()

    override fun unSubscribeAll(member: T): Mono<Void> = TODO()

    override fun unSubscribeAllIn(topic: T): Mono<Void> = TODO()

    override fun sendMessage(message: Message<T, V>): Mono<Void> = TODO()

    override fun listenTo(topic: T): Flux<out Message<T, V>> = TODO()

    override fun exists(topic: T): Mono<Boolean> = TODO()
}

package com.demo.chat.service.core

import com.demo.chat.domain.Message
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * This publish-subscribe service achieves the following:
 *  handle subscription logic per topic
 *  send message to topic
 *  listen to topic
 */
interface PubSubService<T, V> {
    fun subscribe(member: T, topic: T): Mono<Void>
    fun unSubscribe(member: T, topic: T): Mono<Void>
    fun unSubscribeAll(member: T): Mono<Void>
    fun unSubscribeAllIn(topic: T): Mono<Void>
    fun sendMessage(message: Message<T, V>): Mono<Void>
    fun listenTo(topic: T): Flux<out Message<T, V>>
    fun exists(topic: T): Mono<Boolean>
}

/**
 * Topic Exchange: achieve the following
 *  add/remove topic
 *  per topic user inventory
 *  per user topic inventory
 */
interface TopicInventoryService<T> {
    fun open(topicId: T): Mono<Void>
    fun close(topicId: T): Mono<Void>
    fun getByUser(uid: T): Flux<T>
    fun getUsersBy(topicId: T): Flux<T>
}

/**
 * Combined (Publish Subscribe + Topic Exchange)
 */
interface TopicPubSubService<T, V>
    : PubSubService<T, V>, TopicInventoryService<T>
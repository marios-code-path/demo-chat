package com.demo.chat.security.access.core

import com.demo.chat.domain.Message
import com.demo.chat.service.core.PubSubService
import com.demo.chat.service.core.TopicInventoryService
import org.springframework.security.access.prepost.PreAuthorize
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface PubSubAccess<T, V> : PubSubService<T, V> {

    @PreAuthorize("@chatAccess.hasAccessTo(#member, #topic, 'SUBSCRIBE')")
    override fun subscribe(member: T, topic: T): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessTo(#member, #topic, 'SUBSCRIBE')")
    override fun unSubscribe(member: T, topic: T): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessTo(#member, 'UNSUBALL')")
    override fun unSubscribeAll(member: T): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessTo(#topic, 'UNSUBALLIN')")
    override fun unSubscribeAllIn(topic: T): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessTo(#message.key.id, 'SEND')")
    override fun sendMessage(message: Message<T, V>): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessTo(#topic, 'SUBSCRIBE')")
    override fun listenTo(topic: T): Flux<out Message<T, V>>

    //@PreAuthorize("@chatAccess.hasAccessToDomain('Message', 'SUBSCRIBE')")
    @PreAuthorize("@chatAccess.hasAccessTo(#topic, 'SUBSCRIBE')")
    override fun exists(topic: T): Mono<Boolean>
}

interface TopicInventoryAccess<T> : TopicInventoryService<T> {

    @PreAuthorize("@chatAccess.hasAccessTo(#topicId, 'OPEN')")
    override fun open(topicId: T): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessTo(#topicId, 'CLOSE')")
    override fun close(topicId: T): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessTo(#uid, 'TOPICS')")
    override fun getByUser(uid: T): Flux<T>

    @PreAuthorize("@chatAccess.hasAccessTo(#topicId, 'GET')")
    override fun getUsersBy(topicId: T): Flux<T>
}
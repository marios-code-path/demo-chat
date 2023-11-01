package com.demo.chat.controller.core.access

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

    @PreAuthorize("@chatAccess.hasAccessToEntity(#message, 'SEND')")
    override fun sendMessage(message: Message<T, V>): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessTo(#topic, 'SUBSCRIBE')")
    override fun listenTo(topic: T): Flux<out Message<T, V>>

    @PreAuthorize("@chatAccess.hasAccessToDomain(T(com.demo.chat.domain.Message), 'SUBSCRIBE')")
    override fun exists(topic: T): Mono<Boolean>
}

interface TopicInventoryAccess<T> : TopicInventoryService<T> {

    @PreAuthorize("@chatAccess.hasAccessTo(T(com.demo.chat.domain.MessageTopic), #topicId, 'OPEN')")
    override fun open(topicId: T): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessTo(T(com.demo.chat.domain.MessageTopic), #topicId, 'CLOSE')")
    override fun close(topicId: T): Mono<Void>

    @PreAuthorize("@chatAccess.hasAccessTo(T(com.demo.chat.domain.User), #uid, 'TOPICS')")
    override fun getByUser(uid: T): Flux<T>

    @PreAuthorize("@chatAccess.hasAccessTo(T(com.demo.chat.domain.MessageTopic), #topicId, 'GET')")
    override fun getUsersBy(topicId: T): Flux<T>
}
package com.demo.chat.service

import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicMessageKey
import reactor.core.publisher.*
import java.util.*

interface ChatTopicService {
    fun add(id: UUID): Mono<Void>
    fun rem(id: UUID): Mono<Void>
    fun subscribe(member: UUID, id: UUID): Mono<Void>
    fun unSubscribe(member: UUID, id: UUID): Mono<Void>
    fun unSubscribeAll(member: UUID): Mono<Void> // then closes
    fun unSubscribeAllIn(id: UUID): Mono<Void>
    fun sendMessage(topicMessage: Message<TopicMessageKey, Any>): Mono<Void>
    fun receiveOn(id: UUID): Flux<out Message<TopicMessageKey, Any>>
    fun getTopicsByUser(uid: UUID): Flux<UUID>
    fun getUsersBy(id: UUID): Flux<UUID>
    fun exists(id: UUID): Mono<Boolean>

    // receive externally bound resources by attaching a processor to that resources flux
    // 1.  attach to some external source (like a redis pubsub channel)
    // 2.  create FluxProcessor that will handle fanout from our source to any subscriber fluxes
    // 3.  subscribe our processor to the source
    fun receiveSourcedEvents(id: UUID): Flux<out Message<TopicMessageKey, Any>>
}

interface ChatTopicServiceAdmin {
    fun getProcessor(id: UUID): FluxProcessor<out Message<TopicMessageKey, Any>, out Message<TopicMessageKey, Any>>
}

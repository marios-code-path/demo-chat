package com.demo.chat.service.messaging

import com.demo.chat.domain.Message
import com.demo.chat.service.ChatTopicMessagingService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class MessageChannelTopicMessagingService<T, V> : ChatTopicMessagingService<T, V> {

    override fun subscribe(member: T, topic: T): Mono<Void> {
        TODO("Not yet implemented")
    }

    override fun unSubscribe(member: T, topic: T): Mono<Void> {
        TODO("Not yet implemented")
    }

    override fun unSubscribeAll(member: T): Mono<Void> {
        TODO("Not yet implemented")
    }

    override fun unSubscribeAllIn(topic: T): Mono<Void> {
        TODO("Not yet implemented")
    }

    override fun sendMessage(message: Message<T, V>): Mono<Void> {
        TODO("Not yet implemented")
    }

    override fun receiveOn(topic: T): Flux<out Message<T, V>> {
        TODO("Not yet implemented")
    }

    override fun sourceOf(topic: T): Flux<out Message<T, V>> {
        TODO("Not yet implemented")
    }

    override fun exists(topic: T): Mono<Boolean> {
        TODO("Not yet implemented")
    }

    override fun add(id: T): Mono<Void> {
        TODO("Not yet implemented")
    }

    override fun rem(id: T): Mono<Void> {
        TODO("Not yet implemented")
    }

    override fun getByUser(uid: T): Flux<T> {
        TODO("Not yet implemented")
    }

    override fun getUsersBy(id: T): Flux<T> {
        TODO("Not yet implemented")
    }

}
package com.demo.chat.service.dummy

import com.demo.chat.domain.Message
import com.demo.chat.service.core.PubSubService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty

open class DummyPubSubService<T, V> : PubSubService<T, V> {
    override fun subscribe(member: T, topic: T): Mono<Void> = empty()

    override fun unSubscribe(member: T, topic: T): Mono<Void> = empty()

    override fun unSubscribeAll(member: T): Mono<Void> = empty()

    override fun unSubscribeAllIn(topic: T): Mono<Void> = empty()

    override fun sendMessage(message: Message<T, V>): Mono<Void> = empty()

    override fun listenTo(topic: T): Flux<out Message<T, V>> = Flux.empty()

    override fun exists(topic: T): Mono<Boolean> = empty()
}
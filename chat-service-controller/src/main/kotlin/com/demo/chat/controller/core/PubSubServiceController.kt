package com.demo.chat.controller.core

import com.demo.chat.MemberTopicRequest
import com.demo.chat.domain.Message
import com.demo.chat.controller.core.mapping.PubSubServiceMapping
import com.demo.chat.service.PubSubService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class PubSubServiceController<T, V>(private val that: PubSubService<T, V>) : PubSubServiceMapping<T, V>, PubSubService<T, V> by that {
    override fun subscribeOne(req: MemberTopicRequest<T>): Mono<Void> = subscribe(req.member, req.topic)

    override fun unSubscribeOne(req: MemberTopicRequest<T>): Mono<Void> = unSubscribe(req.member, req.topic)

    override fun unSubscribe(member: T, topic: T): Mono<Void> = that.unSubscribe(member, topic)

    override fun unSubscribeAll(member: T): Mono<Void> = that.unSubscribeAll(member)

    override fun unSubscribeAllIn(topic: T): Mono<Void> = that.unSubscribeAllIn(topic)

    override fun sendMessage(message: Message<T, V>): Mono<Void> = that.sendMessage(message)

    override fun receiveOn(topic: T): Flux<out Message<T, V>> = that.receiveOn(topic)

    override fun exists(topic: T): Mono<Boolean> = that.exists(topic)

    override fun add(id: T): Mono<Void> = that.add(id)

    override fun rem(id: T): Mono<Void> = that.rem(id)

    override fun getByUser(uid: T): Flux<T> = that.getByUser(uid)

    override fun getUsersBy(id: T): Flux<T> = that.getUsersBy(id)
}
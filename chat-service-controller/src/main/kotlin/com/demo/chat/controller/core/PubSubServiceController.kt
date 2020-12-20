package com.demo.chat.controller.core

import com.demo.chat.MemberTopicRequest
import com.demo.chat.domain.Message
import com.demo.chat.service.PubSubService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class PubSubServiceController<T, V>(private val that: PubSubService<T, V>) : PubSubService<T, V> {
    @MessageMapping("subscribe")
    fun subscribeOne(req: MemberTopicRequest<T>): Mono<Void> = subscribe(req.member, req.topic)

    override fun subscribe(member: T, topic: T): Mono<Void> = that.subscribe(member, topic)

    @MessageMapping("unsubscribe")
    fun unSubscribeOne(req: MemberTopicRequest<T>): Mono<Void> = unSubscribe(req.member, req.topic)

    override fun unSubscribe(member: T, topic: T): Mono<Void> = that.unSubscribe(member, topic)

    @MessageMapping("unSubscribeAll")
    override fun unSubscribeAll(member: T): Mono<Void> = that.unSubscribeAll(member)

    @MessageMapping("unSubscribeAllIn")
    override fun unSubscribeAllIn(topic: T): Mono<Void> = that.unSubscribeAllIn(topic)

    @MessageMapping("sendMessage")
    override fun sendMessage(message: Message<T, V>): Mono<Void> = that.sendMessage(message)

    @MessageMapping("receiveOn")
    override fun receiveOn(topic: T): Flux<out Message<T, V>> = that.receiveOn(topic)

    @MessageMapping("exists")
    override fun exists(topic: T): Mono<Boolean> = that.exists(topic)

    @MessageMapping("add")
    override fun add(id: T): Mono<Void> = that.add(id)

    @MessageMapping("rem")
    override fun rem(id: T): Mono<Void> = that.rem(id)

    @MessageMapping("getByUser")
    override fun getByUser(uid: T): Flux<T> = that.getByUser(uid)

    @MessageMapping("getUsersBy")
    override fun getUsersBy(id: T): Flux<T> = that.getUsersBy(id)
}
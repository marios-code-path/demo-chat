package com.demo.chat.controller.core.mapping

import com.demo.chat.MemberTopicRequest
import com.demo.chat.domain.Message
import com.demo.chat.service.PubSubService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface PubSubServiceMapping<T, V> : PubSubService<T, V> {
    @MessageMapping("subscribe")
    fun subscribeOne(req: MemberTopicRequest<T>): Mono<Void>

    @MessageMapping("unsubscribe")
    fun unSubscribeOne(req: MemberTopicRequest<T>): Mono<Void>

    @MessageMapping("unSubscribeAll")
    override fun unSubscribeAll(member: T): Mono<Void>

    @MessageMapping("unSubscribeAllIn")
    override fun unSubscribeAllIn(topic: T): Mono<Void>

    @MessageMapping("sendMessage")
    override fun sendMessage(message: Message<T, V>): Mono<Void>

    @MessageMapping("receiveOn")
    override fun receiveOn(topic: T): Flux<out Message<T, V>>

    @MessageMapping("exists")
    override fun exists(topic: T): Mono<Boolean>

    @MessageMapping("add")
    override fun add(id: T): Mono<Void>

    @MessageMapping("rem")
    override fun rem(id: T): Mono<Void>

    @MessageMapping("getByUser")
    override fun getByUser(uid: T): Flux<T>

    @MessageMapping("getUsersBy")
    override fun getUsersBy(id: T): Flux<T>
}
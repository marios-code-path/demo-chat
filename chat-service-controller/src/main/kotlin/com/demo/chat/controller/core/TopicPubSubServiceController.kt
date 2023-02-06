package com.demo.chat.controller.core

import com.demo.chat.domain.MemberTopicRequest
import com.demo.chat.domain.Message
import com.demo.chat.controller.core.mapping.TopicPubSubServiceMapping
import com.demo.chat.service.core.TopicPubSubService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class TopicPubSubServiceController<T, V>(private val that: TopicPubSubService<T, V>) : TopicPubSubServiceMapping<T, V>, TopicPubSubService<T, V> by that {

    override fun subscribeOne(req: MemberTopicRequest<T>): Mono<Void> = subscribe(req.member, req.topic)

    override fun unSubscribeOne(req: MemberTopicRequest<T>): Mono<Void> = unSubscribe(req.member, req.topic)

    override fun unSubscribe(member: T, topic: T): Mono<Void> = that.unSubscribe(member, topic)

    override fun unSubscribeAll(member: T): Mono<Void> = that.unSubscribeAll(member)

    override fun unSubscribeAllIn(topic: T): Mono<Void> = that.unSubscribeAllIn(topic)

    override fun sendMessage(message: Message<T, V>): Mono<Void> = that.sendMessage(message)

    override fun listenTo(topic: T): Flux<out Message<T, V>> = that.listenTo(topic)

    override fun exists(topic: T): Mono<Boolean> = that.exists(topic)

    override fun open(topicId: T): Mono<Void> = that.open(topicId)

    override fun close(topicId: T): Mono<Void> = that.close(topicId)

    override fun getByUser(uid: T): Flux<T> = that.getByUser(uid)

    override fun getUsersBy(topicId: T): Flux<T> = that.getUsersBy(topicId)
}
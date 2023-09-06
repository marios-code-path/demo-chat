package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.MemberTopicRequest
import com.demo.chat.domain.Message
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


interface TopicPubSubRestMapping<T, V> : TopicPubSubService<T, V> {
    @PostMapping("/subscribe")
    fun subscribeOne(req: MemberTopicRequest<T>): Mono<Void>

    @PostMapping("/unsubscribe")
    fun unSubscribeOne(req: MemberTopicRequest<T>): Mono<Void>

    @PostMapping("/unSubscribeAll")
    override fun unSubscribeAll(member: T): Mono<Void>

    @PostMapping("/unSubscribeAllIn")
    override fun unSubscribeAllIn(topic: T): Mono<Void>

    @PostMapping("/sendMessage")
    override fun sendMessage(message: Message<T, V>): Mono<Void>

    @GetMapping("/listen")
    override fun listenTo(topic: T): Flux<out Message<T, V>>

    @GetMapping("/exists")
    override fun exists(topic: T): Mono<Boolean>

    @PutMapping("/add")
    override fun open(topicId: T): Mono<Void>

    @DeleteMapping("/rem")
    override fun close(topicId: T): Mono<Void>

    @GetMapping("/getByUser")
    override fun getByUser(uid: T): Flux<T>

    @GetMapping("/getUsersBy")
    override fun getUsersBy(topicId: T): Flux<T>
}


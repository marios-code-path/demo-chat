package com.demo.chat.service.dummy

import com.demo.chat.service.core.TopicInventoryService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty

open class DummyTopicInventoryService<T> : TopicInventoryService<T> {
    override fun open(topicId: T): Mono<Void> = empty()

    override fun close(topicId: T): Mono<Void> = empty()

    override fun getByUser(uid: T): Flux<T> = Flux.empty()

    override fun getUsersBy(topicId: T): Flux<T> = Flux.empty()
}
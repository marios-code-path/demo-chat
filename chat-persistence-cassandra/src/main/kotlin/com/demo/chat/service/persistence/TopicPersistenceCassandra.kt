package com.demo.chat.service.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.NotFoundException
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.service.IKeyService
import com.demo.chat.service.TopicPersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class TopicPersistenceCassandra<T>(private val keyService: IKeyService<T>,
                                        private val roomRepo: TopicRepository<T>)
    : TopicPersistence<T> {
    override fun all(): Flux<out MessageTopic<T>> = roomRepo.findAll()

    override fun get(key: Key<T>): Mono<out MessageTopic<T>> = roomRepo.findByKeyId(key.id)

    override fun key(): Mono<out Key<T>> = keyService.key(MessageTopic::class.java)

    override fun add(ent: MessageTopic<T>): Mono<Void> =
            roomRepo
                    .add(ent)
                    .then()

    override fun rem(key: Key<T>): Mono<Void> =
            roomRepo
                    .findByKeyId(key.id)
                    .switchIfEmpty(Mono.error(NotFoundException))
                    .flatMap { roomRepo.rem(key) }
}
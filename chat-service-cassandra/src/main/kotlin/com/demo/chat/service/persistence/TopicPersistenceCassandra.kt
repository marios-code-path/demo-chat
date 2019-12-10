package com.demo.chat.service.persistence

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.service.TopicPersistence
import com.demo.chat.service.UUIDKeyService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class TopicPersistenceCassandra(private val keyService: UUIDKeyService,
                                     private val roomRepo: TopicRepository)
    : TopicPersistence {
    override fun all(): Flux<out EventTopic> = roomRepo.findAll()

    override fun get(key: UUIDKey): Mono<out EventTopic> = roomRepo.findByKeyId(key.id)

    override fun key(): Mono<UUIDKey> = keyService.id(TopicKey::class.java)

    override fun add(eventTopic: EventTopic): Mono<Void> =
            roomRepo
                    .add(eventTopic)
                    .then()

    override fun rem(key: UUIDKey): Mono<Void> =
            roomRepo
                    .findByKeyId(key.id)
                    .flatMap {
                        when (it) {
                            null -> Mono.error(TopicNotFoundException)
                            else -> roomRepo.rem(key)
                        }
                    }
}
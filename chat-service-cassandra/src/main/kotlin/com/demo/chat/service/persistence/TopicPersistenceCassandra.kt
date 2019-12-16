package com.demo.chat.service.persistence

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.service.TopicPersistence
import com.demo.chat.service.UUIDKeyService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

open class TopicPersistenceCassandra(private val keyService: UUIDKeyService,
                                     private val roomRepo: TopicRepository)
    : TopicPersistence<UUID> {
    override fun all(): Flux<out MessageTopic<UUID>> = roomRepo.findAll()

    override fun get(key: Key<UUID>): Mono<out MessageTopic<UUID>> = roomRepo.findByKeyId(key.id)

    override fun key(): Mono<out Key<UUID>> = keyService.id(Key::class.java)

    override fun add(messageTopic: MessageTopic<UUID>): Mono<Void> =
            roomRepo
                    .add(messageTopic)
                    .then()

    override fun rem(key: Key<UUID>): Mono<Void> =
            roomRepo
                    .findByKeyId(key.id)
                    .flatMap {
                        when (it) {
                            null -> Mono.error(TopicNotFoundException)
                            else -> roomRepo.rem(key)
                        }
                    }
}
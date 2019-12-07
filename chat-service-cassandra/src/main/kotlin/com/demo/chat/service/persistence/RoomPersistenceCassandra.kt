package com.demo.chat.service.persistence

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatRoomRepository
import com.demo.chat.service.RoomPersistence
import com.demo.chat.service.KeyService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class RoomPersistenceCassandra(private val keyService: KeyService,
                                    private val roomRepo: ChatRoomRepository)
    : RoomPersistence {
    override fun all(): Flux<out Topic> = roomRepo.findAll()

    override fun get(key: EventKey): Mono<out Topic> = roomRepo.findByKeyId(key.id)

    override fun key(): Mono<EventKey> = keyService.id(TopicKey::class.java)

    override fun add(topic: Topic): Mono<Void> =
            roomRepo
                    .add(topic)
                    .then()

    override fun rem(key: EventKey): Mono<Void> =
            roomRepo
                    .findByKeyId(key.id)
                    .flatMap {
                        when (it) {
                            null -> Mono.error(RoomNotFoundException)
                            else -> roomRepo.rem(key)
                        }
                    }
}
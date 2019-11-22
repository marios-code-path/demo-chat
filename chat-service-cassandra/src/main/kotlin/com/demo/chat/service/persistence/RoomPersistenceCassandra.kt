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
    override fun all(): Flux<out Room> = roomRepo.findAll()

    override fun get(key: EventKey): Mono<out Room> = roomRepo.findByKeyId(key.id)

    override fun key(): Mono<EventKey> = keyService.id(RoomKey::class.java)

    override fun add(room: Room): Mono<Void> =
            roomRepo
                    .add(room)
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
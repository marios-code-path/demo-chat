package com.demo.chat.service.persistence

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatRoomNameRepository
import com.demo.chat.repository.cassandra.ChatRoomRepository
import com.demo.chat.service.ChatRoomPersistence
import com.demo.chat.service.KeyService
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class ChatRoomPersistenceCassandra(private val keyService: KeyService,
                                        private val roomRepo: ChatRoomRepository)
    : ChatRoomPersistence {
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
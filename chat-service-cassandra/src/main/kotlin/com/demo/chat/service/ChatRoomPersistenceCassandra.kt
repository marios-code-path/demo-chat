package com.demo.chat.service

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatRoomRepository
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

open class ChatRoomPersistenceCassandra(private val keyService: KeyService,
                                        private val roomRepo: ChatRoomRepository)
    : ChatRoomPersistence<Room, RoomKey> {
    val logger = LoggerFactory.getLogger(this::class.simpleName)

    override fun key(name: String): Mono<out RoomKey> =
            keyService.key(RoomKey::class.java) {
                RoomKey.create(it.id, name)
            }

    override fun getById(id: UUID): Mono<ChatRoom> =
            roomRepo
                    .findByKeyId(id)

    override fun getAll(activeOnly: Boolean): Flux<ChatRoom> =
            roomRepo.findAll()
                    .filter {
                        activeOnly == it.active
                    }

    override fun add(key: RoomKey): Mono<Void> =
            roomRepo
                    .add(ChatRoom(
                            ChatRoomKey(
                                    key.id,
                                    key.name
                            ),
                            emptySet(),
                            true,
                            Instant.now()
                    ))
                    .then()

    override fun size(roomId: UUID): Mono<Int> =
            roomRepo
                    .findByKeyId(roomId)
                    .switchIfEmpty(Mono.error(RoomNotFoundException))
                    .handle { it, sink ->
                        when (it) {
                            null -> sink.error(RoomNotFoundException)
                            else -> {
                                var size = when (it.members) {
                                    null -> 0
                                    else -> it.members.size
                                }
                                sink.next(size)
                            }
                        }
                    }

    override fun members(roomId: UUID): Mono<Set<UUID>> =
            roomRepo
                    .findByKeyId(roomId)
                    .flatMap { room ->
                        when (room) {
                            null -> Mono.error(RoomNotFoundException)
                            else -> Mono.just(room.members!!)       // Is this reasonable ? (!!)
                        }
                    }

    override fun rem(key: RoomKey): Mono<Void> =
            roomRepo
                    .findByKeyId(key.id)
                    .flatMap {
                        when (it) {
                            null -> Mono.error(RoomNotFoundException)
                            else -> roomRepo.rem(key)
                        }
                    }

    override fun addMember(uid: UUID, roomId: UUID): Mono<Void> =
            verifyRoom(roomId)
                    .then(roomRepo.join(uid, roomId))

    override fun remMember(uid: UUID, roomId: UUID): Mono<Void> =
            verifyRoom(roomId)
                    .then(roomRepo.leave(uid, roomId))

    private fun verifyRoom(roomId: UUID): Mono<Void> =
            roomRepo.findByKeyId(roomId)
                    .switchIfEmpty(Mono.error(RoomNotFoundException))
                    .then()

}
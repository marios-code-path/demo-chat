package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatRoomRepository
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

interface KeyService {
    fun id(): UUID
}
open class ChatRoomPersistenceCassandra(private val roomRepo: ChatRoomRepository)
    : ChatRoomPersistence<Room, RoomKey> {
    override fun key(name: String): Mono<out RoomKey> =
            Mono.just(ChatRoomKey(UUIDs.timeBased(), name))

    val logger = LoggerFactory.getLogger(this::class.simpleName)

    override fun getById(id: UUID): Mono<ChatRoom> =
            roomRepo
                    .findByKeyRoomId(id)

    override fun getAll(activeOnly: Boolean): Flux<ChatRoom> =
            roomRepo.findAll()
                    .filter {
                        activeOnly == it.active
                    }

    override fun add(key: RoomKey): Mono<Void> =
            roomRepo
                    .saveRoom(ChatRoom(
                            ChatRoomKey(
                                    key.roomId,
                                    key.name
                            ),
                            emptySet(),
                            true,
                            Instant.now()
                    ))
                    .then()

    override fun size(roomId: UUID): Mono<Int> =
            roomRepo
                    .findByKeyRoomId(roomId)
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
                    .findByKeyRoomId(roomId)
                    .flatMap { room ->
                        when (room) {
                            null -> Mono.error(RoomNotFoundException)
                            else -> Mono.just(room.members!!)       // Is this reasonable ? (!!)
                        }
                    }

    override fun rem(key: RoomKey): Mono<Void> =
            roomRepo
                    .findByKeyRoomId(key.roomId)
                    .flatMap {
                        when (it) {
                            null -> Mono.error(RoomNotFoundException)
                            else -> roomRepo.remRoom(key)
                        }
                    }

    override fun addMember(uid: UUID, roomId: UUID): Mono<Void> =
            verifyRoom(roomId)
                    .then(roomRepo.joinRoom(uid, roomId))

    override fun remMember(uid: UUID, roomId: UUID): Mono<Void> =
            verifyRoom(roomId)
                    .then(roomRepo.leaveRoom(uid, roomId))

    private fun verifyRoom(roomId: UUID): Mono<Void> =
            roomRepo.findByKeyRoomId(roomId)
                    .switchIfEmpty(Mono.error(RoomNotFoundException))
                    .then()

}
package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.ChatRoom
import com.demo.chat.domain.ChatRoomKey
import com.demo.chat.domain.RoomKey
import com.demo.chat.domain.RoomNotFoundException
import com.demo.chat.repository.cassandra.ChatRoomRepository
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

open class ChatRoomServiceCassandra(private val roomRepo: ChatRoomRepository)
    : ChatRoomService<ChatRoom, RoomKey> {
    val logger = LoggerFactory.getLogger(this::class.simpleName)

    override fun getRoomById(id: UUID): Mono<ChatRoom> =
            roomRepo
                    .findByKeyRoomId(id)

    override fun getRooms(activeOnly: Boolean): Flux<ChatRoom> =
            roomRepo.findAll()
                    .filter {
                        activeOnly == it.active
                    }

    override fun createRoom(name: String): Mono<RoomKey> =
            roomRepo
                    .saveRoom(ChatRoom(
                            ChatRoomKey(UUIDs.timeBased(), name),
                            emptySet(),
                            true,
                            Instant.now()))
                    .map {
                        it.key
                    }

    override fun roomSize(roomId: UUID): Mono<Int> =
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

    override fun roomMembers(roomId: UUID): Mono<Set<UUID>> =
            roomRepo
                    .findByKeyRoomId(roomId)
                    .flatMap { room ->
                        when (room) {
                            null -> Mono.error(RoomNotFoundException)
                            else -> Mono.just(room.members!!)       // Is this reasonable ? (!!)
                        }
                    }

    override fun deleteRoom(roomId: UUID): Mono<Void> =
            roomRepo
                    .findByKeyRoomId(roomId)
                    .flatMap {
                        when (it) {
                            null -> Mono.error(RoomNotFoundException)
                            else -> roomRepo.deactivateRoom(roomId)
                        }
                    }

    override fun joinRoom(uid: UUID, roomId: UUID): Mono<Void> =
            verifyRoom(roomId)
                    .then(roomRepo.joinRoom(uid, roomId))

    override fun leaveRoom(uid: UUID, roomId: UUID): Mono<Void> =
            verifyRoom(roomId)
                    .then(roomRepo.leaveRoom(uid, roomId))

    private fun verifyRoom(roomId: UUID): Mono<Void> =
            roomRepo.findByKeyRoomId(roomId)
                    .switchIfEmpty(Mono.error(RoomNotFoundException))
                    .then()

}
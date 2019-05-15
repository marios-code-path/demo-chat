package com.demo.chat.service.cassandra

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatRoomRepository
import com.demo.chat.service.ChatRoomService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import java.time.Instant
import java.util.*

class ChatRoomServiceCassandra(val roomRepo: ChatRoomRepository) : ChatRoomService<RoomKey> {

    override fun createRoom(name: String): Mono<RoomKey> =
            roomRepo
                    .insert(ChatRoom(
                            ChatRoomKey(UUIDs.timeBased(), name),
                            emptySet(),
                            Instant.now()))
                    .map {
                        it.key
                    }

    override fun roomSize(roomId: UUID): Mono<Int> =
            roomRepo
                    .findByKeyRoomId(roomId)
                    .switchIfEmpty(Mono.error(ChatException("Room not there")))
                    .map {
                        when (it) {
                            null -> throw ChatException("Room not Found")
                            else -> {
                                it.members?.size
                            }
                        }
                    }

    override fun roomMembers(roomId: UUID): Mono<Set<UUID>> =
            roomRepo
                    .findByKeyRoomId(roomId)
                    .map {
                        when (it) {
                            null -> throw ChatException("Room not Found")
                            else -> {
                                it.members
                            }
                        }
                    }

    override fun deleteRoom(roomId: UUID): Mono<Void> =
            roomRepo
                    .findByKeyRoomId(roomId)
                    .flatMap {
                        when (it) {
                            null -> throw ChatException("Room Not Found")
                            else -> {
                                roomRepo.delete(it)
                            }
                        }
                    }

    override fun joinRoom(uid: UUID, roomId: UUID): Mono<Void> =
            verifyRoom(roomId)
                    .then(roomRepo.joinRoom(uid, roomId))

    override fun leaveRoom(uid: UUID, roomId: UUID): Mono<Void> =
            verifyRoom(roomId)
                    .then(roomRepo.leaveRoom(uid, roomId))

    private fun verifyRoom( roomId: UUID): Mono<Void> =
            Flux.from(
                    roomRepo.findByKeyRoomId(roomId).switchIfEmpty { Mono.error(RoomNotFoundException) }
            ).then()
}
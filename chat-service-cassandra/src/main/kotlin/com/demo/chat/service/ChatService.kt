package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.*
import com.demo.chat.repository.*
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import reactor.core.publisher.toFlux
import java.sql.Time
import java.time.Instant
import java.time.LocalTime
import java.util.*

open class ChatException(msg: String) : Exception(msg)
object UserNotFoundException : ChatException("User not Found")
object RoomNotFoundException : ChatException("Room not Found")

@Component
class ChatService(val userRepo: ChatUserRepository,
                  val roomRepo: ChatRoomRepository,
                  val messageRepo: ChatMessageRepository,
                  val messageRoomRepo: ChatMessageRoomRepository,
                  val messageUserRepo: ChatMessageUserRepository) {


    fun newUser(handle: String, name: String): Mono<ChatUser> =
            userRepo
                    .insert(ChatUser(UUIDs.timeBased(),
                            handle,
                            name,
                            Instant.now()
                    ))

    fun newRoom(uid: UUID, name: String): Mono<ChatRoom> = userRepo.findById(uid)
            .switchIfEmpty { Mono.error(ChatException("user not found")) }
            .flatMap {
                roomRepo
                        .insert(ChatRoom(UUIDs.timeBased(), name, emptySet(), Instant.now()))
            }

    fun joinRoom(uid: UUID, roomId: UUID): Mono<Boolean> = Mono
            .from(findRoomAndUser(uid, roomId))
            .flatMap {
                roomRepo.joinRoom(uid, roomId)
            }
            .defaultIfEmpty(false)


    fun leaveRoom(uid: UUID, roomId: UUID): Mono<Boolean> = Mono
            .from(findRoomAndUser(uid, roomId))
            .flatMap {
                roomRepo.leaveRoom(uid, roomId)
            }
            .defaultIfEmpty(false)

    fun sendMessage(uid: UUID, roomId: UUID, messageText: String): Mono<ChatMessage> = Mono
            .from(findRoomAndUser(uid, roomId))
            .flatMap {
                messageRepo
                        .insert(ChatMessage(ChatMessageKey(UUIDs.timeBased(),
                                uid,
                                roomId,
                                Instant.now()),
                                messageText,
                                true))
            }

    fun getMessagesForRoom(uid: UUID, roomId: UUID): Flux<ChatMessageRoom> = Mono
            .from(findRoomAndUser(uid, roomId))
            .toFlux()
            .flatMap {
                messageRoomRepo.findById(roomId)
            }

    private fun findRoomAndUser(uid: UUID, roomId: UUID) = Flux.zip(
            userRepo.findById(uid).switchIfEmpty { Mono.error(UserNotFoundException) },
            roomRepo.findById(roomId).switchIfEmpty { Mono.error(RoomNotFoundException) })

}
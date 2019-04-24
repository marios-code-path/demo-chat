package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.*
import com.demo.chat.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import java.time.Instant
import java.util.*


@Component
class ChatServiceCassandra(val userRepo: ChatUserRepository,
                           val roomRepo: ChatRoomRepository,
                           val messageRepo: ChatMessageRepository,
                           val messageRoomRepo: ChatMessageRoomRepository,
                           val messageUserRepo: ChatMessageUserRepository)
    : ChatService<ChatUser, ChatRoom, ChatMessage> {

    val logger = LoggerFactory.getLogger("CHAT-SERVICE-CASSANDRA")

    override fun newUser(handle: String, name: String): Mono<ChatUser> = userRepo
            .saveUser(ChatUser(
                    ChatUserKey(UUIDs.timeBased(), handle),
                    name,
                    Instant.now()))

    override fun newRoom(uid: UUID, name: String): Mono<ChatRoom> = userRepo
            .findByKeyUserId(uid)
            .switchIfEmpty { Mono.error(ChatException("user not found")) }
            .flatMap {
                roomRepo
                        .insert(ChatRoom(
                                ChatRoomKey(UUIDs.timeBased(), name),
                                emptySet(),
                                Instant.now()))
            }

    override fun joinRoom(uid: UUID, roomId: UUID): Mono<ChatRoomInfoAlert> =
            verifyRoomAndUser(uid, roomId)
                    .then(roomRepo.joinRoom(uid, roomId))
                    .flatMap {
                        roomRepo.roomInfo(roomId)
                    }
                    .map {
                        ChatRoomInfoAlert(
                                MessageAlertKey(UUIDs.timeBased(), roomId, Instant.now()), it, true)
                    }


    override fun leaveRoom(uid: UUID, roomId: UUID): Mono<ChatRoomInfoAlert> =
            verifyRoomAndUser(uid, roomId)
                    .then(roomRepo.leaveRoom(uid, roomId))
                    .flatMap {
                        roomRepo.roomInfo(roomId)
                    }
                    .map {
                        ChatRoomInfoAlert(
                                MessageAlertKey(UUIDs.timeBased(), roomId, Instant.now()), it, true)
                    }

    override fun storeMessage(uid: UUID, roomId: UUID, messageText: String): Mono<ChatMessage> =
            verifyRoomAndUser(uid, roomId)
                    .then(
                            messageRepo
                                    .saveMessage(ChatMessage(ChatMessageKey(UUIDs.timeBased(),
                                            uid,
                                            roomId,
                                            Instant.now()),
                                            messageText,
                                            true))
                    )

    override fun getMessagesForRoom(uid: UUID, roomId: UUID): Flux<ChatMessage> =
            verifyRoomAndUser(uid, roomId)
                    .thenMany(
                            messageRoomRepo.findByKeyRoomId(roomId)
                                    .map {
                                        ChatMessage(
                                                ChatMessageKey(
                                                        it.key.id,
                                                        it.key.userId,
                                                        it.key.roomId,
                                                        it.key.timestamp
                                                ),
                                                it.value,
                                                it.visible
                                        )
                                    }
                    )

    override fun getMessagesForUser(uid: UUID): Flux<ChatMessage> =
            userRepo.findByKeyUserId(uid).switchIfEmpty { Mono.error(UserNotFoundException) }
                    .thenMany(
                            messageUserRepo.findByKeyUserId(uid)
                                    .map {
                                        ChatMessage(
                                                ChatMessageKey(
                                                        it.key.id,
                                                        it.key.userId,
                                                        it.key.roomId,
                                                        it.key.timestamp
                                                ),
                                                it.value,
                                                it.visible
                                        )
                                    }
                    )

    override fun getRoomInfo(roomId: UUID): Mono<ChatRoomInfoAlert> =
            roomRepo.findByKeyRoomId(roomId)
                    .map {
                        val roomSize = Optional.ofNullable(it.members)
                                .orElse(Collections.emptySet())
                                .size

                        ChatRoomInfoAlert(MessageAlertKey(
                                UUIDs.timeBased(), roomId, Instant.now()
                        ), RoomInfo(
                                roomSize,
                                roomSize,
                                0
                        ), true)
                    }

    override fun verifyRoomAndUser(uid: UUID, roomId: UUID): Mono<Void> =
            Flux.zip(
                    userRepo.findByKeyUserId(uid).switchIfEmpty { Mono.error(UserNotFoundException) },
                    roomRepo.findByKeyRoomId(roomId).switchIfEmpty { Mono.error(RoomNotFoundException) }
            ).then()
}
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
                           val userByHandleRepo: ChatUserHandleRepository,
                           val roomRepo: ChatRoomRepository,
                           val messageRepo: ChatMessageRepository,
                           val messageRoomRepo: ChatMessageRoomRepository)
    : ChatService<ChatRoom, ChatUser, ChatMessage> {

    val logger = LoggerFactory.getLogger("CHAT-SERVICE-CASSANDRA")

    override fun getMessage(id: UUID): Mono<ChatMessage> =
            messageRepo.findByKeyId(id)

    override fun getRoom(roomId: UUID): Mono<ChatRoom> =
            roomRepo.findByKeyRoomId(roomId)

    override fun getUser(userId: UUID): Mono<ChatUser> =
            userRepo.findByKeyUserId(userId)

    override fun getUserByHandle(handle: String): Mono<ChatUser> =
            userByHandleRepo
                    .findByKeyHandle(handle)
                    .map {
                        ChatUser(
                                ChatUserKey(it.key.userId, it.key.handle),
                                it.name,
                                it.timestamp
                        )
                    }

    override fun storeUser(handle: String, name: String): Mono<UserKey> = userRepo
            .saveUser(ChatUser(
                    ChatUserKey(UUIDs.timeBased(), handle),
                    name,
                    Instant.now()))
            .map {
                it.key
            }

    override fun storeRoom(name: String): Mono<RoomKey> =
            roomRepo
                    .insert(ChatRoom(
                            ChatRoomKey(UUIDs.timeBased(), name),
                            emptySet(),
                            Instant.now()))
                    .map {
                        it.key
                    }

    override fun joinRoom(uid: UUID, roomId: UUID): Mono<Void> =
            verifyRoomAndUser(uid, roomId)
                    .then(roomRepo.joinRoom(uid, roomId))

    override fun leaveRoom(uid: UUID, roomId: UUID): Mono<Void> =
            verifyRoomAndUser(uid, roomId)
                    .then(roomRepo.leaveRoom(uid, roomId))

    override fun storeMessage(uid: UUID, roomId: UUID, messageText: String): Mono<TextMessageKey> =
            messageRepo
                    .saveMessage(ChatMessage(ChatMessageKey(UUIDs.timeBased(),
                            uid,
                            roomId,
                            Instant.now()),
                            messageText,
                            true))
                    .map {
                        it.key
                    }

    override fun getRoomMessages(roomId: UUID): Flux<ChatMessage> =
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

    private fun verifyRoomAndUser(uid: UUID, roomId: UUID): Mono<Void> =
            Flux.zip(
                    userRepo.findByKeyUserId(uid).switchIfEmpty { Mono.error(UserNotFoundException) },
                    roomRepo.findByKeyRoomId(roomId).switchIfEmpty { Mono.error(RoomNotFoundException) }
            ).then()
}
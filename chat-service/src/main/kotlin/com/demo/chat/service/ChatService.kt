package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

// Backend for Chat services

interface ChatService<U : User<UserKey>, R : Room<RoomKey>, M : Message<MessageKey, Any>> {
    fun newUser(handle: String, name: String): Mono<U>
    fun newRoom(uid: UUID, name: String): Mono<R>
    fun joinRoom(uid: UUID, roomId: UUID): Mono<ChatRoomInfoAlert>
    fun leaveRoom(uid: UUID, roomId: UUID): Mono<ChatRoomInfoAlert>
    fun storeMessage(uid: UUID, roomId: UUID, messageText: String): Mono<M>
    fun getMessagesForRoom(uid: UUID, roomId: UUID): Flux<M>
    fun getMessagesForUser(uid: UUID): Flux<M>
    fun getRoomInfo(roomId: UUID): Mono<ChatRoomInfoAlert>

    fun verifyRoomAndUser(uid: UUID, roomId: UUID): Mono<Void>
}
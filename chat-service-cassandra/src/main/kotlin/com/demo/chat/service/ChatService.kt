package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatService<U : User<UserKey>, R : Room<RoomKey>, M : Message<MessageKey>> {
    fun newUser(handle: String, name: String): Mono<U>
    fun newRoom(uid: UUID, name: String): Mono<R>
    fun joinRoom(uid: UUID, roomId: UUID): Mono<Boolean>
    fun leaveRoom(uid: UUID, roomId: UUID): Mono<Boolean>
    fun storeMessage(uid: UUID, roomId: UUID, messageText: String): Mono<M>
    fun getMessagesForRoom(uid: UUID, roomId: UUID): Flux<M>
    fun verifyRoomAndUser(uid: UUID, roomId: UUID): Mono<Void>
}
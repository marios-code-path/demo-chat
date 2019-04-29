package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

// Backend for Chat services

interface ChatService<R : Room<RoomKey>, U : User<UserKey>, M : Message<TextMessageKey, Any>> {
    fun storeRoom(name: String): Mono<RoomKey>
    fun storeUser(name: String, handle: String): Mono<UserKey>
    fun storeMessage(uid: UUID, roomId: UUID, text: String): Mono<TextMessageKey>

    fun getRoom(roomId: UUID): Mono<R>
    fun getUser(userId: UUID): Mono<U>
    fun getRoomMessages(roomId: UUID): Flux<M>
    fun getMessage(id: UUID): Mono<M>

    fun joinRoom(uid: UUID, roomId: UUID): Mono<Void>
    fun leaveRoom(uid: UUID, roomId: UUID): Mono<Void>

}
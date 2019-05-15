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
    fun getUserByHandle(handle: String): Mono<U>
    fun getRoomMessages(roomId: UUID): Flux<M>
    fun getMessage(id: UUID): Mono<M>

    fun joinRoom(uid: UUID, roomId: UUID): Mono<Void>
    fun leaveRoom(uid: UUID, roomId: UUID): Mono<Void>

}

interface ChatUserService< U : User<UserKey>, UK: UserKey> {
    fun createUser(name: String, handle: String): Mono<UserKey>
    fun getUser(handle: String): Mono<U>
    fun getUsersById(uuids: Flux<UUID>): Flux<U>

    // TODO WE DO NOT HAVE proper user authentication mechanism yet.. FYI
    fun createUserAuthentication(uid: UUID, password: String): Mono<Void>
    fun authenticateUser(name: String, password: String): Mono<UK>
}


interface ChatRoomService<RK : RoomKey> {
    fun createRoom(name: String): Mono<RK>
    fun roomSize(roomId: UUID): Mono<Int>
    fun roomMembers(roomId: UUID): Mono<Set<UUID>>
    fun deleteRoom(roomId: UUID): Mono<Void>

    fun joinRoom(uid: UUID, roomId: UUID): Mono<Void>
    fun leaveRoom(uid: UUID, roomId: UUID): Mono<Void>
}

interface ChatMessageService<MK : MessageKey, M: Message<MK, Any>> {
    fun getMessage(id: UUID): Mono<M>
    fun storeMessage(uid: UUID, roomId: UUID, messageText: String): Mono<MK>
    fun getTopicMessages(roomId: UUID): Flux<M>

}

package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatUserService< U : User<UserKey>, UK: UserKey> {
    fun createUser(name: String, handle: String): Mono<U>
    fun getUser(handle: String): Mono<U>
    fun getUsersById(uuids: Flux<UUID>): Flux<U>
    fun getUserById(uuid: UUID): Mono<U>

    // TODO WE DO NOT HAVE proper user authentication mechanism yet.. FYI
    fun createUserAuthentication(uid: UUID, password: String): Mono<Void>
    fun authenticateUser(name: String, password: String): Mono<UK>
}


interface ChatRoomService<R : Room<RoomKey>, RK : RoomKey> {
    fun getRooms(activeOnly: Boolean): Flux<R>
    fun getRoomById(id: UUID): Mono<R>
    fun createRoom(name: String): Mono<RK>
    fun roomSize(roomId: UUID): Mono<Int>
    fun roomMembers(roomId: UUID): Mono<Set<UUID>>
    fun deleteRoom(roomId: UUID): Mono<Void>

    fun joinRoom(uid: UUID, roomId: UUID): Mono<Void>
    fun leaveRoom(uid: UUID, roomId: UUID): Mono<Void>
}

interface ChatMessageService<M: Message<MessageKey, Any>,MK : MessageKey > {
    fun getMessage(id: UUID): Mono<M>
    fun storeMessage(uid: UUID, roomId: UUID, messageText: String): Mono<MK>
    fun getTopicMessages(topicId: UUID): Flux<M>
}

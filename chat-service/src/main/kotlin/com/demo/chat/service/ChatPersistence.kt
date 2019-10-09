package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatUserPersistence<U : User, UK : UserKey> {
    fun key(handle: String): Mono<out UK>
    fun add(key: UK, name: String, imgUri: String): Mono<Void>
    fun rem(key: UK): Mono<Void>
}

interface ChatRoomPersistence<R : Room, RK : RoomKey> {
    fun key(name: String): Mono<out RK>
    fun add(key: RK): Mono<Void>
    fun rem(key: RK): Mono<Void>

    fun size(roomId: UUID): Mono<Int>
    fun members(roomId: UUID): Mono<Set<UUID>>

    fun addMember(uid: UUID, roomId: UUID): Mono<Void>
    fun remMember(uid: UUID, roomId: UUID): Mono<Void>

    // TODO THIS NOW BECOMES INDEX OPERATION
    fun getAll(activeOnly: Boolean): Flux<out R>
    fun getById(id: UUID): Mono<out R>
    fun getByName(name: String): Mono<out R>
}

interface TextMessagePersistence<M : TextMessage, MK : TextMessageKey> {
    fun key(uid: UUID, roomId: UUID): Mono<out MK>
    fun add(key: MK, messageText: String): Mono<Void>
    fun rem(key: MK): Mono<Void>
    fun getById(id: UUID): Mono<out M>
    fun getAll(topicId: UUID): Flux<out M>
}
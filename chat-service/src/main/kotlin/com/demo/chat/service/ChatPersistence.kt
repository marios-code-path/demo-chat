package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatUserPersistence<U : User, UK : UserKey> {
    fun key(handle: String): Mono<out UK>
    fun add(key: UK, name: String, imgUri: String): Mono<Void>
    fun rem(key: UK): Mono<Void>
    fun getByHandle(handle: String): Mono<out U>
    fun findByIds(uuids: Flux<UUID>): Flux<out U>
    fun getById(uuid: UUID): Mono<out U>

    // TODO WE DO NOT HAVE proper user authentication mechanism yet.. FYI
    fun createAuthentication(uid: UUID, password: String): Mono<Void>

    fun authenticate(name: String, password: String): Mono<out UK>
}

interface ChatRoomPersistence<R : Room, RK : RoomKey> {
    fun key(name: String): Mono<out RK>
    fun add(key: RK): Mono<Void>
    fun rem(key: RK): Mono<Void>
    fun getAll(activeOnly: Boolean): Flux<out R>
    fun getById(id: UUID): Mono<out R>
    fun size(roomId: UUID): Mono<Int>
    fun members(roomId: UUID): Mono<Set<UUID>>

    fun addMember(uid: UUID, roomId: UUID): Mono<Void>
    fun remMember(uid: UUID, roomId: UUID): Mono<Void>
}

interface TextMessagePersistence<M : TextMessage, MK : TextMessageKey> {
    fun key(uid: UUID, roomId: UUID): Mono<out MK>
    fun add(key: MK, messageText: String): Mono<Void>
    fun rem(key: MK): Mono<Void>
    fun getById(id: UUID): Mono<out M>
    fun getAll(topicId: UUID): Flux<out M>
}

interface KeyService {
    fun id(): Mono<EventKey>
    fun <T> key(kind: Class<T>, create: (eventKey: EventKey) -> T): Mono<T>
}
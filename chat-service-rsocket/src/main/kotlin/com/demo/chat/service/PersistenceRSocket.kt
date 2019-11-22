package com.demo.chat.service

import com.demo.chat.domain.*
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


open class PersistenceRSocket<T>(val that: ChatPersistence<T>) : ChatPersistence<T> {
    @MessageMapping("key")
    override fun key(): Mono<out EventKey> = that.key()

    @MessageMapping("add")
    override fun add(ent: T): Mono<Void> = that.add(ent)

    @MessageMapping("rem")
    override fun rem(key: EventKey): Mono<Void> = that.rem(key)

    @MessageMapping("get")
    override fun get(key: EventKey): Mono<out T> = that.get(key)

    @MessageMapping("all")
    override fun all(): Flux<out T> = that.all()
}

class RSocketUserPersistence(t: UserPersistence) : PersistenceRSocket<User>(t)
class RSocketKeyPersistence(t: KeyPersistence) : PersistenceRSocket<EventKey>(t)
class RSocketMessagePersistence(t: TextMessagePersistence) : PersistenceRSocket<TextMessage>(t)
class RSocketRoomPersistence(t: RoomPersistence) : PersistenceRSocket<Room>(t)
class RSocketMembershipPersistence(t: MembershipPersistence) : PersistenceRSocket<Membership<EventKey>>(t)
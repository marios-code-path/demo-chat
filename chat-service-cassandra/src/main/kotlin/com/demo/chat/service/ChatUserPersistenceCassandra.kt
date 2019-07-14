package com.demo.chat.service

import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.demo.chat.domain.UserNotFoundException
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

// TODO flexability on what classes go in and out of repository thru persistence
open class ChatUserPersistenceCassandra(val keyService: KeyService,
                                        val userRepo: ChatUserRepository,
                                        val userHandleRepo: ChatUserHandleRepository)
    : ChatUserPersistence<User, UserKey> {
    override fun key(handle: String): Mono<UserKey> =
            keyService.key(UserKey::class.java) {
                UserKey.create(it.id, handle)
            }

    override fun rem(key: UserKey): Mono<Void> =
            userRepo
                    .rem(key)

    override fun authenticate(handle: String, password: String): Mono<UserKey> =
            userHandleRepo
                    .findByKeyHandle(handle)
                    .handle { u, s ->
                        when (u) {
                            null -> s.error(UserNotFoundException)
                            else -> s.next(UserKey.create(u.key.id, u.key.handle))
                        }
                    }

    override fun add(key: UserKey, name: String, defaultImageUri: String): Mono<Void> = userRepo
            .add(User.create(key, name, defaultImageUri))

    override fun createAuthentication(uid: UUID, password: String): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getByHandle(handle: String): Mono<out User> = userHandleRepo
            .findByKeyHandle(handle)

    override fun getById(uuid: UUID): Mono<out User> = userRepo
            .findByKeyId(uuid)


    override fun findByIds(uuids: Flux<UUID>): Flux<out User> = userRepo
            .findByKeyIdIn(uuids)

}
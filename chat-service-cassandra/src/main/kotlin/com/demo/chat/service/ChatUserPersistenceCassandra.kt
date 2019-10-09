package com.demo.chat.service

import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.demo.chat.domain.UserNotFoundException
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

// TODO flexibility on what classes go in and out of repository thru persistence
open class ChatUserPersistenceCassandra(val keyService: KeyService,
                                        val userRepo: ChatUserRepository)
    : ChatUserPersistence<User, UserKey> {
    override fun key(handle: String): Mono<UserKey> =
            keyService.key(UserKey::class.java) {
                UserKey.create(it.id, handle)
            }

    override fun rem(key: UserKey): Mono<Void> =
            userRepo
                    .rem(key)

    override fun add(key: UserKey, name: String, defaultImageUri: String): Mono<Void> = userRepo
            .add(User.create(key, name, defaultImageUri))
}
package com.demo.chat.persistence.cassandra.impl

import java.time.Instant

import com.demo.chat.persistence.cassandra.domain.ChatUserKey

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.persistence.cassandra.domain.ChatUser
import com.demo.chat.persistence.cassandra.repository.ChatUserRepository
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.UserPersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Each read maps a row under the root of the USER domain, and it keeps the
 * stored timestamp. See `CHAT-avduuqwp`.
 */
open class UserPersistenceCassandra<T : Any>(
    val keyService: IKeyService<T>,
    private val rootKeys: RootKeys<T>,
    private val userRepo: ChatUserRepository<T>
) : UserPersistence<T> {
    override fun all(): Flux<out User<T>> = userRepo.findAll().map(::user)

    override fun get(key: Key<T>): Mono<out User<T>> = userRepo.findByKeyId(key.id).map(::user)

    override fun key(): Mono<out Key<T>> = keyService.key(ChatDomain.USER)

    override fun rem(key: Key<T>): Mono<Void> = userRepo.rem(key)

    override fun add(ent: User<T>): Mono<Void> =
        userRepo.add(ChatUser(ChatUserKey(ent.key.id), ent.name, ent.handle, ent.imageUri, Instant.now()))

    override fun byIds(keys: List<Key<T>>): Flux<out User<T>> =
        userRepo.findByKeyIdIn(keys.map { it.id }).map(::user)

    private fun user(row: ChatUser<T>): User<T> =
        User.create(Key.of(row.key.id, rootKeys.of(ChatDomain.USER).id), row.name, row.handle, row.imageUri, row.timestamp)
}

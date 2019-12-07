package com.demo.chat.service.persistence

import com.demo.chat.domain.UUIDKey
import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.service.UserPersistence
import com.demo.chat.service.KeyService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

// TODO flexibility on what classes go in and out of repository thru persistence
open class UserPersistenceCassandra(val keyService: KeyService,
                                    val userRepo: ChatUserRepository)
    : UserPersistence {
    override fun all(): Flux<out User> = userRepo.findAll()

    override fun get(key: UUIDKey): Mono<out User> = userRepo.findByKeyId(key.id)

    override fun key(): Mono<UUIDKey> = keyService.id(UserKey::class.java)

    override fun rem(key: UUIDKey): Mono<Void> = userRepo.rem(key)

    override fun add(user: User): Mono<Void> = userRepo.add(user)

    override fun byIds(keys: List<UUIDKey>): Flux<out User> =
            userRepo.findByKeyIdIn(Flux.fromStream(keys.stream().map { it.id }))
}
package com.demo.chat.persistence.cassandra.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.persistence.cassandra.repository.ChatUserRepository
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.UserPersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.stream.Collectors

open class UserPersistenceCassandra<T>(val keyService: IKeyService<T>,
                                       private val userRepo: ChatUserRepository<T>
)
    : UserPersistence<T> {
    override fun all(): Flux<out User<T>> = userRepo.findAll()

    override fun get(key: Key<T>): Mono<out User<T>> = userRepo.findByKeyId(key.id)

    override fun key(): Mono<out Key<T>> = keyService.key(User::class.java)

    override fun rem(key: Key<T>): Mono<Void> = userRepo.rem(key)

    override fun add(ent: User<T>): Mono<Void> = userRepo.add(ent)

    override fun byIds(keys: List<Key<T>>): Flux<out User<T>> =
            userRepo.findByKeyIdIn(keys.stream().map { it.id }.collect(Collectors.toList()))
}


package com.demo.chat.service.persistence

import com.demo.chat.domain.EventKey
import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.service.ChatUserPersistence
import com.demo.chat.service.KeyService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

// TODO flexibility on what classes go in and out of repository thru persistence
open class ChatUserPersistenceCassandra(val keyService: KeyService,
                                        val userRepo: ChatUserRepository)
    : ChatUserPersistence {
    override fun all(): Flux<out User> = userRepo.findAll()

    override fun get(key: EventKey): Mono<out User> = userRepo.findByKeyId(key.id)

    override fun key(): Mono<EventKey> = keyService.id(UserKey::class.java)

    override fun rem(key: EventKey): Mono<Void> = userRepo.rem(key)

    override fun add(user: User): Mono<Void> = userRepo.add(user)
}
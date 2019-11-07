package com.demo.chat.service.persistence

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatMembershipRepository
import com.demo.chat.service.ChatMembershipPersistence
import com.demo.chat.service.KeyService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.stream.Collectors

class ChatMembershipPersistenceCassandra (
        private val keyService: KeyService,
        private val membershipRepo: ChatMembershipRepository
) : ChatMembershipPersistence {
    override fun key(): Mono<out EventKey> = keyService.id(Membership::class.java)

    override fun  add(entity: Membership<EventKey>): Mono<Void> = membershipRepo
            .save(entity as ChatMembership)
            .then()

    override fun rem(key: EventKey): Mono<Void> = membershipRepo.deleteById(key.id)

    override fun get(key: EventKey): Mono<out Membership<EventKey>> = membershipRepo.findByKeyId(key.id)

    override fun all(): Flux<out Membership<EventKey>> = membershipRepo.findAll()

    override fun byIds(keys: List<EventKey>): Flux<out Membership<EventKey>> = membershipRepo
            .findByKeyIdIn(keys.stream().map { it.id }.collect(Collectors.toList()))
}
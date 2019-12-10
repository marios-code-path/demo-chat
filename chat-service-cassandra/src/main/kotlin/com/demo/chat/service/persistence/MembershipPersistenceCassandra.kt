package com.demo.chat.service.persistence

import com.demo.chat.domain.*
import com.demo.chat.domain.cassandra.ChatMembership
import com.demo.chat.repository.cassandra.ChatMembershipRepository
import com.demo.chat.service.MembershipPersistence
import com.demo.chat.service.UUIDKeyService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.stream.Collectors

class MembershipPersistenceCassandra (
        private val keyService: UUIDKeyService,
        private val membershipRepo: ChatMembershipRepository
) : MembershipPersistence {
    override fun key(): Mono<out UUIDKey> = keyService.id(Membership::class.java)

    override fun  add(entity: Membership<UUIDKey>): Mono<Void> = membershipRepo
            .save(entity as ChatMembership)
            .then()

    override fun rem(key: UUIDKey): Mono<Void> = membershipRepo.deleteById(key.id)

    override fun get(key: UUIDKey): Mono<out Membership<UUIDKey>> = membershipRepo.findByKeyId(key.id)

    override fun all(): Flux<out Membership<UUIDKey>> = membershipRepo.findAll()

    override fun byIds(keys: List<UUIDKey>): Flux<out Membership<UUIDKey>> = membershipRepo
            .findByKeyIdIn(keys.stream().map { it.id }.collect(Collectors.toList()))
}
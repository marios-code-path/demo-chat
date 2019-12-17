package com.demo.chat.service.persistence

import com.demo.chat.domain.*
import com.demo.chat.domain.cassandra.ChatMembership
import com.demo.chat.repository.cassandra.ChatMembershipRepository
import com.demo.chat.service.IKeyService
import com.demo.chat.service.MembershipPersistence
import com.demo.chat.service.UUIDKeyService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.stream.Collectors

class MembershipPersistenceCassandra <T>(
        private val keyService: IKeyService<T>,
        private val membershipRepo: ChatMembershipRepository<T>
) : MembershipPersistence<T> {
    override fun key(): Mono<out Key<T>> = keyService.key(Membership::class.java)

    override fun  add(entity: Membership<T>): Mono<Void> = membershipRepo
            .save(entity)
            .then()

    override fun rem(key: Key<T>): Mono<Void> = membershipRepo.deleteById(key.id)

    override fun get(key: Key<T>): Mono<out Membership<T>> = membershipRepo.findByKeyId(key.id)

    override fun all(): Flux<out Membership<T>> = membershipRepo.findAll()

    override fun byIds(keys: List<out Key<T>>): Flux<out Membership<T>> = membershipRepo
            .findByKeyIdIn(keys.stream().map { it.id }.collect(Collectors.toList()))
}
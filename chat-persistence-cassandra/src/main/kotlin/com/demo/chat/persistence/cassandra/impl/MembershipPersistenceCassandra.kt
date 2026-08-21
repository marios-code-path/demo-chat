package com.demo.chat.persistence.cassandra.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.persistence.cassandra.domain.TopicMembershipByKey
import com.demo.chat.persistence.cassandra.repository.TopicMembershipRepository
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.MembershipPersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class MembershipPersistenceCassandra<T>(
    private val keyService: IKeyService<T>,
    private val membershipRepo: TopicMembershipRepository<T>
) : MembershipPersistence<T> {

    override fun key(): Mono<out Key<T>> = keyService.key(TopicMembershipByKey::class.java)

    override fun add(ent: TopicMembership<T>): Mono<Void> = membershipRepo
        .save(
            TopicMembershipByKey(
                ent.key,
                ent.member,
                ent.memberOf
            )
        )
        .then()

    override fun rem(key: Key<T>): Mono<Void> {
        requireNotNull(key.id)
        return membershipRepo.deleteById(key.id)
    }

    override fun get(key: Key<T>): Mono<out TopicMembership<T>> = membershipRepo
        .findByKey(key.id)

    override fun all(): Flux<out TopicMembership<T>> = membershipRepo.findAll()

    override fun byIds(keys: List<Key<T>>): Flux<out TopicMembership<T>> = membershipRepo
        .findByKeyIn(keys.map { it.id })
}

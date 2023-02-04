package com.demo.chat.persistence.cassandra.impl

import com.demo.chat.domain.Key
import com.demo.chat.persistence.cassandra.domain.TopicMembershipByKey
import com.demo.chat.persistence.cassandra.repository.TopicMembershipRepository
import com.demo.chat.service.IKeyService
import com.demo.chat.service.MembershipPersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.stream.Collectors

// TODO: Convert me to STREAM
class MembershipPersistenceCassandra<T>(
        private val keyService: IKeyService<T>,
        private val membershipRepo: TopicMembershipRepository<T>
) : MembershipPersistence<T> {
    override fun key(): Mono<out Key<T>> = keyService.key(TopicMembershipByKey::class.java)

    override fun add(ent: com.demo.chat.domain.TopicMembership<T>): Mono<Void> = membershipRepo
            .save(
                TopicMembershipByKey(
                    ent.key,
                    ent.member,
                    ent.memberOf)
            )
            .then()

    override fun rem(key: Key<T>): Mono<Void> = membershipRepo.deleteById(key.id)

    override fun get(key: Key<T>): Mono<out com.demo.chat.domain.TopicMembership<T>> = membershipRepo
            .findByKey(key.id)

    override fun all(): Flux<out com.demo.chat.domain.TopicMembership<T>> = membershipRepo.findAll()

    override fun byIds(keys: List<Key<T>>): Flux<out com.demo.chat.domain.TopicMembership<T>> = membershipRepo
            .findByKeyIn(keys.stream().map { it.id }.collect(Collectors.toList()))
}
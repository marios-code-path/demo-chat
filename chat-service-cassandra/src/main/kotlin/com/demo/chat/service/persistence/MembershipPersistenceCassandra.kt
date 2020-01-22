package com.demo.chat.service.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.cassandra.TopicMembershipByKey
import com.demo.chat.repository.cassandra.TopicMembershipRepository
import com.demo.chat.service.IKeyService
import com.demo.chat.service.MembershipPersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.stream.Collectors

class MembershipPersistenceCassandra<T>(
        private val keyService: IKeyService<T>,
        private val membershipRepo: TopicMembershipRepository<T>
) : MembershipPersistence<T> {
    override fun key(): Mono<out Key<T>> = keyService.key(TopicMembershipByKey::class.java)

    override fun add(entity: com.demo.chat.domain.TopicMembership<T>): Mono<Void> = membershipRepo
            .save(com.demo.chat.domain.cassandra.TopicMembershipByKey(
                    entity.key,
                    entity.member,
                    entity.memberOf))
            .then()

    override fun rem(key: Key<T>): Mono<Void> = membershipRepo.deleteById(key.id)

    override fun get(key: Key<T>): Mono<out com.demo.chat.domain.TopicMembership<T>> = membershipRepo
            .findByKey(key.id)

    override fun all(): Flux<out com.demo.chat.domain.TopicMembership<T>> = membershipRepo.findAll()

    override fun byIds(keys: List<out Key<T>>): Flux<out com.demo.chat.domain.TopicMembership<T>> = membershipRepo
            .findByKeyIn(keys.stream().map { it.id }.collect(Collectors.toList()))
}
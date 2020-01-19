package com.demo.chat.service.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.cassandra.CassandraUUIDKeyType
import com.demo.chat.domain.cassandra.TopicMembership
import com.demo.chat.domain.cassandra.TopicMembershipKey
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
    override fun key(): Mono<out Key<T>> = keyService.key(TopicMembership::class.java)

    override fun add(entity: com.demo.chat.domain.TopicMembership<T>): Mono<Void> = membershipRepo
            .save(com.demo.chat.domain.cassandra.TopicMembership(
                    entity.key.id,
                    entity.member.id,
                    entity.memberOf.id))
            .then()

    override fun rem(key: Key<T>): Mono<Void> = membershipRepo.deleteById(key.id)

    override fun get(key: Key<T>): Mono<out com.demo.chat.domain.TopicMembership<T>> = membershipRepo
            .findByKeyId(key.id)
            .map(this::update)

    override fun all(): Flux<out com.demo.chat.domain.TopicMembership<T>> = membershipRepo.findAll()
            .map(this::update)

    override fun byIds(keys: List<out Key<T>>): Flux<out com.demo.chat.domain.TopicMembership<T>> = membershipRepo
            .findByKeyIdIn(keys.stream().map { it.id }.collect(Collectors.toList()))
            .map(this::update)

    fun update(from: TopicMembership<T>): TopicMembership<T> =
            TopicMembership(TopicMembershipKey(from.key),
                    CassandraUUIDKeyType(from.member),
                    CassandraUUIDKeyType(from.memberOf))

    fun legacy(from: TopicMembership<T>): TopicMembership<T> =
            TopicMembership(from.key.id, from.member.id, from.memberOf.id)
}
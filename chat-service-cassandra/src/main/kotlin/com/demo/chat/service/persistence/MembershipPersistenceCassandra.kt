package com.demo.chat.service.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.cassandra.CassandraUUIDKeyType
import com.demo.chat.domain.cassandra.ChatMembership
import com.demo.chat.domain.cassandra.ChatMembershipKey
import com.demo.chat.repository.cassandra.ChatMembershipRepository
import com.demo.chat.service.IKeyService
import com.demo.chat.service.MembershipPersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import java.util.stream.Collectors

class MembershipPersistenceCassandra<T : UUID>(
        private val keyService: IKeyService<T>,
        private val membershipRepo: ChatMembershipRepository<T>
) : MembershipPersistence<T> {
    override fun key(): Mono<out Key<T>> = keyService.key(TopicMembership::class.java)

    override fun add(entity: TopicMembership<T>): Mono<Void> = membershipRepo
            .save(ChatMembership(
                    ChatMembershipKey(entity.key.id),
                    CassandraUUIDKeyType(entity.member.id),
                    CassandraUUIDKeyType(entity.memberOf.id)))
            .then()

    override fun rem(key: Key<T>): Mono<Void> = membershipRepo.deleteById(key.id)

    override fun get(key: Key<T>): Mono<out TopicMembership<T>> = membershipRepo.findByKeyId(key.id)

    override fun all(): Flux<out TopicMembership<T>> = membershipRepo.findAll()

    override fun byIds(keys: List<out Key<T>>): Flux<out TopicMembership<T>> = membershipRepo
            .findByKeyIdIn(keys.stream().map { it.id }.collect(Collectors.toList()))
}
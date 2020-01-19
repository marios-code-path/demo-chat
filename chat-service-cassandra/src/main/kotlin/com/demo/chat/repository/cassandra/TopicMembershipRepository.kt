package com.demo.chat.repository.cassandra

import com.demo.chat.domain.cassandra.*
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface TopicMembershipRepository<T> : ReactiveCassandraRepository<TopicMembership<T>, T> {
    fun findByKeyId(id: T): Mono<TopicMembership<T>>
    fun findByKeyIdIn(ids: List<T>): Flux<TopicMembership<T>>
}

interface TopicMembershipByMemberRepository<T> : ReactiveCassandraRepository<TopicMembershipByMember<T>, T> {
    fun findByMemberId(id: T): Flux<TopicMembershipByMember<T>>
}

interface TopicMembershipByMemberOfRepository<T> : ReactiveCassandraRepository<TopicMembershipByMemberOf<T>, T> {
    fun findByMemberOfId(id: T): Flux<TopicMembershipByMemberOf<T>>
}
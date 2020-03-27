package com.demo.chat.repository.cassandra

import com.demo.chat.domain.cassandra.*
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface TopicMembershipRepository<T> : ReactiveCassandraRepository<TopicMembershipByKey<T>, T> {
    fun findByKey(id: T): Mono<TopicMembershipByKey<T>>
    fun findByKeyIn(ids: List<T>): Flux<TopicMembershipByKey<T>>
}

interface TopicMembershipByMemberRepository<T> : ReactiveCassandraRepository<TopicMembershipByMember<T>, T> {
    fun findByMember(id: T): Flux<TopicMembershipByMember<T>>
}

interface TopicMembershipByMemberOfRepository<T> : ReactiveCassandraRepository<TopicMembershipByMemberOf<T>, T> {
    fun findByMemberOf(id: T): Flux<TopicMembershipByMemberOf<T>>
}
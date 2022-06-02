package com.demo.chat.repository.cassandra

import com.demo.chat.domain.cassandra.TopicMembershipByKey
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface TopicMembershipRepository<T> : ReactiveCassandraRepository<TopicMembershipByKey<T>, T> {
    fun findByKey(id: T): Mono<TopicMembershipByKey<T>>
    fun findByKeyIn(ids: List<T>): Flux<TopicMembershipByKey<T>>
}
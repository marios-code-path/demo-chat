package com.demo.chat.persistence.cassandra.repository

import com.demo.chat.persistence.cassandra.domain.TopicMembershipByKey
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface TopicMembershipRepository<T> : ReactiveCassandraRepository<TopicMembershipByKey<T>, T> {
    fun findByKey(id: T): Mono<TopicMembershipByKey<T>>
    fun findByKeyIn(ids: List<T>): Flux<TopicMembershipByKey<T>>
}
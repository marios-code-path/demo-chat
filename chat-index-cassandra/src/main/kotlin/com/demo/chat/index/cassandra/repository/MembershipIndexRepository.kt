package com.demo.chat.index.cassandra.repository

import com.demo.chat.index.cassandra.domain.TopicMembershipByMember
import com.demo.chat.index.cassandra.domain.TopicMembershipByMemberOf
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux

interface TopicMembershipByMemberRepository<T> : ReactiveCassandraRepository<TopicMembershipByMember<T>, T> {
    fun findByMember(id: T): Flux<TopicMembershipByMember<T>>
}

interface TopicMembershipByMemberOfRepository<T> : ReactiveCassandraRepository<TopicMembershipByMemberOf<T>, T> {
    fun findByMemberOf(id: T): Flux<TopicMembershipByMemberOf<T>>
}
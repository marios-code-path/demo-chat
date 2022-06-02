package com.demo.chat.repository.cassandra

import com.demo.chat.domain.cassandra.TopicMembershipByMember
import com.demo.chat.domain.cassandra.TopicMembershipByMemberOf
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux

interface TopicMembershipByMemberRepository<T> : ReactiveCassandraRepository<TopicMembershipByMember<T>, T> {
    fun findByMember(id: T): Flux<TopicMembershipByMember<T>>
}

interface TopicMembershipByMemberOfRepository<T> : ReactiveCassandraRepository<TopicMembershipByMemberOf<T>, T> {
    fun findByMemberOf(id: T): Flux<TopicMembershipByMemberOf<T>>
}
package com.demo.chat.repository.cassandra

import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.cassandra.ChatMembership
import com.demo.chat.domain.cassandra.ChatMembershipByMember
import com.demo.chat.domain.cassandra.ChatMembershipByMemberOf
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatMembershipRepository<S> : ReactiveCassandraRepository<TopicMembership<S>, S> {
    fun findByKeyId(id: S) : Mono<ChatMembership<S>>
    fun findByKeyIdIn(ids: List<S>): Flux<ChatMembership<S>>
}

interface ChatMembershipByMemberRepository<S> : ReactiveCassandraRepository<TopicMembership<S>, S> {
    fun findByMemberId(id: S) : Flux<ChatMembershipByMember<S>>
}

interface ChatMembershipByMemberOfRepository<S> : ReactiveCassandraRepository<TopicMembership<S>, S> {
    fun findByMemberOfId(id: S) : Flux<ChatMembershipByMemberOf<S>>
}
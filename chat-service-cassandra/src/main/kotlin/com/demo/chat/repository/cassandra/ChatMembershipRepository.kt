package com.demo.chat.repository.cassandra

import com.demo.chat.domain.cassandra.ChatMembership
import com.demo.chat.domain.cassandra.ChatMembershipByMember
import com.demo.chat.domain.cassandra.ChatMembershipByMemberOf
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatMembershipRepository<T> : ReactiveCassandraRepository<ChatMembership<T>, T> {
    fun findByKeyId(id: T): Mono<ChatMembership<T>>
    fun findByKeyIdIn(ids: List<T>): Flux<ChatMembership<T>>
}

interface ChatMembershipByMemberRepository<T> : ReactiveCassandraRepository<ChatMembershipByMember<T>, T> {
    fun findByMemberId(id: T): Flux<ChatMembershipByMember<T>>
}

interface ChatMembershipByMemberOfRepository<T> : ReactiveCassandraRepository<ChatMembershipByMemberOf<T>, T> {
    fun findByMemberOfId(id: T): Flux<ChatMembershipByMemberOf<T>>
}
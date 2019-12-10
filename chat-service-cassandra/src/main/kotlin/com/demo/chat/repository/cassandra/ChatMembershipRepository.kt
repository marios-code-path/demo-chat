package com.demo.chat.repository.cassandra

import com.demo.chat.domain.cassandra.ChatMembership
import com.demo.chat.domain.cassandra.ChatMembershipByMember
import com.demo.chat.domain.cassandra.ChatMembershipByMemberOf
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatMembershipRepository : ReactiveCassandraRepository<ChatMembership, UUID> {
    fun findByKeyId(id: UUID) : Mono<ChatMembership>
    fun findByKeyIdIn(ids: List<UUID>): Flux<ChatMembership>
}

interface ChatMembershipByMemberRepository : ReactiveCassandraRepository<ChatMembershipByMember, UUID> {
    fun findByMemberId(id: UUID) : Flux<ChatMembershipByMember>
}

interface ChatMembershipByMemberOfRepository : ReactiveCassandraRepository<ChatMembershipByMemberOf, UUID> {
    fun findByMemberOfId(id: UUID) : Flux<ChatMembershipByMemberOf>
}
package com.demo.chat.repository.cassandra

import com.demo.chat.domain.ChatMembership
import com.demo.chat.domain.ChatMembershipByMember
import com.demo.chat.domain.ChatMembershipByMemberOf
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Mono
import java.util.*

interface ChatMembershipRepository : ReactiveCassandraRepository<ChatMembership, UUID> {
    fun findByKeyId(id: UUID) : Mono<ChatMembership>
}

interface ChatMembershipByMemberRepository : ReactiveCassandraRepository<ChatMembershipByMember, UUID> {
    fun findByMemberId(id: UUID) : Mono<ChatMembershipByMember>
}

interface ChatMembershipByMemberOfRepository : ReactiveCassandraRepository<ChatMembershipByMemberOf, UUID> {
    fun findByMemberOfId(id: UUID) : Mono<ChatMembershipByMemberOf>
}
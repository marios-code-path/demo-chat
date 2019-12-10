package com.demo.chat.service.index

import com.demo.chat.domain.*
import com.demo.chat.domain.cassandra.*
import com.demo.chat.repository.cassandra.ChatMembershipByMemberOfRepository
import com.demo.chat.repository.cassandra.ChatMembershipByMemberRepository
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MembershipIndexService.Companion.MEMBER
import com.demo.chat.service.MembershipIndexService.Companion.MEMBEROF
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

class MembershipIndexCassandra(val byMemberRepo: ChatMembershipByMemberRepository,
                               val byMemberOfRepo: ChatMembershipByMemberOfRepository) : MembershipIndexService {

    override fun add(ent: TopicMembership, criteria: Map<String, String>): Mono<Void> =
            byMemberRepo
                    .save(ChatMembershipByMember(
                            CassandraKeyType(ent.key.id),
                            ChatMembershipKeyByMember(ent.member.id),
                            CassandraKeyType(ent.memberOf.id)
                    ))
                    .thenMany(byMemberOfRepo
                            .save(ChatMembershipByMemberOf(
                                    CassandraKeyType(ent.key.id),
                                    CassandraKeyType(ent.member.id),
                                    ChatMembershipKeyByMemberOf(ent.memberOf.id)
                            ))).then()

    override fun rem(ent: TopicMembership): Mono<Void> =
            byMemberRepo
                    .delete(ChatMembershipByMember(
                            CassandraKeyType(ent.key.id),
                            ChatMembershipKeyByMember(ent.member.id),
                            CassandraKeyType(ent.memberOf.id)))
                    .flatMap {
                        byMemberOfRepo
                                .delete(ChatMembershipByMemberOf(
                                        CassandraKeyType(ent.key.id),
                                        CassandraKeyType(ent.member.id),
                                        ChatMembershipKeyByMemberOf(ent.memberOf.id))
                                )
                    }
                    .then()

    override fun size(roomId: UUIDKey): Mono<Int> =
            byMemberOfRepo.findByMemberOfId(roomId.id)
                    .reduce(0) { c, m ->
                        c + 1
                    }

    override fun addMember(membership: TopicMembership): Mono<Void> = add(membership, mapOf())

    override fun remMember(membership: TopicMembership): Mono<Void> = rem(membership)

    override fun findBy(query: Map<String, String>): Flux<out UUIDKey> {
        return when (val queryBy = query.keys.first()) {
            MEMBER -> {
                byMemberRepo.findByMemberId(UUID.fromString(query[queryBy] ?: error("member not valid")))
            }
            MEMBEROF -> {
                byMemberOfRepo.findByMemberOfId(UUID.fromString(query[queryBy] ?: error("memberOf not valid")))
            }
            else -> {
                Flux.empty()
            }
        }
                .map {
                    it.key
                }
    }
}
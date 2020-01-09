package com.demo.chat.service.index

import com.demo.chat.codec.Codec
import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.cassandra.*
import com.demo.chat.repository.cassandra.ChatMembershipByMemberOfRepository
import com.demo.chat.repository.cassandra.ChatMembershipByMemberRepository
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MembershipIndexService.Companion.ID
import com.demo.chat.service.MembershipIndexService.Companion.MEMBER
import com.demo.chat.service.MembershipIndexService.Companion.MEMBEROF
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

class MembershipCriteriaCodec<T: UUID> : Codec<TopicMembership<T>, Map<String, T>> {
    override fun decode(record: TopicMembership<T>): Map<String, T> =
            mapOf(
                    Pair(MEMBER, record.member.id),
                    Pair(MEMBEROF, record.memberOf.id)
            )
}

class MembershipIndexCassandra<T: UUID>(
        val criteriaCodec: Codec<TopicMembership<T>, Map<String, T>>,
        val byMemberRepo: ChatMembershipByMemberRepository<T>,
        val byMemberOfRepo: ChatMembershipByMemberOfRepository<T>)
    : MembershipIndexService<T> {

    override fun add(ent: TopicMembership<T>): Mono<Void> {
        val criteria = criteriaCodec.decode(ent)

        return byMemberRepo
                .save(ChatMembershipByMember(
                        CassandraUUIDKeyType(criteria[ID]!!),
                        ChatMembershipKeyByMember(criteria[MEMBER]!!),
                        CassandraUUIDKeyType(criteria[MEMBEROF]!!)
                ))
                .thenMany(byMemberOfRepo
                        .save(ChatMembershipByMemberOf(
                                CassandraUUIDKeyType(criteria[ID]!!),
                                CassandraUUIDKeyType(criteria[MEMBER]!!),
                                ChatMembershipKeyByMemberOf(criteria[MEMBEROF]!!)
                        ))).then()
    }

    override fun rem(key: Key<T>): Mono<Void> =
            byMemberRepo
                    .deleteById(key.id)
                    .flatMap {
                        byMemberOfRepo
                                .deleteById(key.id)

                    }
                    .then()

    override fun size(key: Key<T>): Mono<Int> =
            byMemberOfRepo.findByMemberOfId(key.id)
                    .reduce(0) { c, _ ->
                        c + 1
                    }

    override fun addMember(membership: TopicMembership<T>): Mono<Void> = add(membership)

    override fun remMember(membership: TopicMembership<T>): Mono<Void> = rem(membership.key)

    override fun findBy(query: Map<String, T>): Flux<Key<T>> {
        return when (val queryBy = query.keys.first()) {
            MEMBER -> {
                byMemberRepo.findByMemberId(query[queryBy] ?: error("missing Member"))
            }
            MEMBEROF -> {
                byMemberOfRepo.findByMemberOfId(query[queryBy] ?: error("missing memberOf"))
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
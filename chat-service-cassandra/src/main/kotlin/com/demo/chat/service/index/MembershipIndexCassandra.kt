package com.demo.chat.service.index

import com.demo.chat.codec.Codec
import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.cassandra.TopicMembershipByMember
import com.demo.chat.domain.cassandra.TopicMembershipByMemberOf
import com.demo.chat.repository.cassandra.TopicMembershipByMemberOfRepository
import com.demo.chat.repository.cassandra.TopicMembershipByMemberRepository
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MembershipIndexService.Companion.ID
import com.demo.chat.service.MembershipIndexService.Companion.MEMBER
import com.demo.chat.service.MembershipIndexService.Companion.MEMBEROF
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

class MembershipCriteriaCodec<T: UUID> : Codec<com.demo.chat.domain.cassandra.TopicMembership<T>, Map<String, T>> {
    override fun decode(record: com.demo.chat.domain.cassandra.TopicMembership<T>): Map<String, T> =
            mapOf(
                    Pair(MEMBER, record.member),
                    Pair(MEMBEROF, record.memberOf)
            )
}

class MembershipIndexCassandra<T>(
        val criteriaCodec: Codec<TopicMembership<T>, Map<String, T>>,
        val byMemberRepo: TopicMembershipByMemberRepository<T>,
        val byMemberOfRepo: TopicMembershipByMemberOfRepository<T>)
    : MembershipIndexService<T> {

    override fun add(ent: TopicMembership<T>): Mono<Void> {
        val criteria = criteriaCodec.decode(ent)

        return byMemberRepo
                .save(TopicMembershipByMember(
                        criteria[ID]!!,
                        criteria[MEMBER]!!,
                        criteria[MEMBEROF]!!
                ))
                .thenMany(byMemberOfRepo
                        .save(TopicMembershipByMemberOf(
                                criteria[ID]!!,
                                criteria[MEMBER]!!,
                                criteria[MEMBEROF]!!
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

    override fun addMember(topicMembership: TopicMembership<T>): Mono<Void> = add(topicMembership)

    override fun remMember(topicMembership: TopicMembership<T>): Mono<Void> = rem(Key.funKey(topicMembership.key))

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
                    Key.funKey(it.key)
                }
    }
}
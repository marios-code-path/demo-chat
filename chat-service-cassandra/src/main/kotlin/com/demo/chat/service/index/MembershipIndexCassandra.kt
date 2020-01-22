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

class MembershipCriteriaCodec<T> : Codec<TopicMembership<T>, Map<String, T>> {
    override fun decode(record: TopicMembership<T>): Map<String, T> =
            mapOf(
                    Pair(ID, record.key),
                    Pair(MEMBER, record.member),
                    Pair(MEMBEROF, record.memberOf)
            )
}

class MembershipIndexCassandra<T>(
        private val criteriaCodec: MembershipCriteriaCodec<T>,
        private val byMemberRepo: TopicMembershipByMemberRepository<T>,
        private val byMemberOfRepo: TopicMembershipByMemberOfRepository<T>)
    : MembershipIndexService<T> {

    override fun add(entity: TopicMembership<T>): Mono<Void> {
        val criteria = criteriaCodec.decode(entity)
        val id = criteria[ID] ?: error("Key not found")
        val member = criteria[MEMBER] ?: error("Member not found")
        val memberOf = criteria[MEMBEROF] ?: error("MemberOf not found")
        return byMemberRepo
                .save(TopicMembershipByMember(id, member, memberOf))
                .thenMany(byMemberOfRepo
                        .save(TopicMembershipByMemberOf(id, member, memberOf)))
                .then()
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
            byMemberOfRepo.findByMemberOf(key.id)
                    .reduce(0) { c, _ ->
                        c + 1
                    }

    override fun addMember(topicMembership: TopicMembership<T>): Mono<Void> = add(topicMembership)

    override fun remMember(topicMembership: TopicMembership<T>): Mono<Void> = rem(Key.funKey(topicMembership.key))

    override fun findBy(query: Map<String, T>): Flux<Key<T>> =
            when (val queryBy = query.keys.first()) {
                MEMBER -> {
                    byMemberRepo.findByMember(query[queryBy] ?: error("missing Member"))
                }
                MEMBEROF -> {
                    byMemberOfRepo.findByMemberOf(query[queryBy] ?: error("missing memberOf"))
                }
                else -> Flux.empty()

            }
                    .map {
                        Key.funKey(it.key)
                    }
}
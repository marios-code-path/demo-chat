package com.demo.chat.service.index

import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.cassandra.TopicMembershipByMember
import com.demo.chat.domain.cassandra.TopicMembershipByMemberOf
import com.demo.chat.repository.cassandra.TopicMembershipByMemberOfRepository
import com.demo.chat.repository.cassandra.TopicMembershipByMemberRepository
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MembershipIndexService.Companion.MEMBER
import com.demo.chat.service.MembershipIndexService.Companion.MEMBEROF
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function

class MembershipIndexCassandra<T>(
        private val stringToKey: Function<String, T>,
        private val byMemberRepo: TopicMembershipByMemberRepository<T>,
        private val byMemberOfRepo: TopicMembershipByMemberOfRepository<T>,
) : MembershipIndexService<T, Map<String, String>> {

    override fun add(entity: TopicMembership<T>): Mono<Void> {
        return byMemberRepo
                .save(TopicMembershipByMember(entity.key,
                        entity.member,
                        entity.memberOf))
                .thenMany(byMemberOfRepo
                        .save(TopicMembershipByMemberOf(entity.key,
                                entity.member,
                                entity.memberOf)))
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

    override fun findBy(query: Map<String, String>): Flux<Key<T>> =
            when (val queryBy = query.keys.first()) {
                MEMBER -> byMemberRepo.findByMember(stringToKey.apply(query[queryBy] ?: error("missing Member")))
                MEMBEROF -> byMemberOfRepo.findByMemberOf(stringToKey.apply(query[queryBy] ?: error("missing memberOf")))
                else -> Flux.empty()

            }
                    .map {
                        Key.funKey(it.key)
                    }
}
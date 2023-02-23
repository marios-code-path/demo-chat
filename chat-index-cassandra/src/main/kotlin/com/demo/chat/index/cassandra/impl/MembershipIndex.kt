package com.demo.chat.index.cassandra.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.index.cassandra.domain.TopicMembershipByMember
import com.demo.chat.index.cassandra.domain.TopicMembershipByMemberOf
import com.demo.chat.index.cassandra.repository.TopicMembershipByMemberOfRepository
import com.demo.chat.index.cassandra.repository.TopicMembershipByMemberRepository
import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.MembershipIndexService.Companion.MEMBER
import com.demo.chat.service.core.MembershipIndexService.Companion.MEMBEROF
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function

class MembershipIndex<T>(
    private val stringToKey: Function<String, T>,
    private val byMemberRepo: TopicMembershipByMemberRepository<T>,
    private val byMemberOfRepo: TopicMembershipByMemberOfRepository<T>,
) : MembershipIndexService<T, Map<String, String>> {

    override fun add(entity: TopicMembership<T>): Mono<Void> {
        return byMemberRepo
                .save(
                    TopicMembershipByMember(entity.key,
                        entity.member,
                        entity.memberOf)
                )
                .thenMany(byMemberOfRepo
                        .save(
                            TopicMembershipByMemberOf(entity.key,
                                entity.member,
                                entity.memberOf)
                        ))
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

    // TODO deperecate this or fix this implementation
    override fun size(query: Map<String, String>): Mono<Long> =
            byMemberOfRepo.findByMemberOf(stringToKey.apply(query.keys.first()))
                    .reduce(0) { c, _ ->
                        c + 1
                    }
//
//    override fun addMember(topicMembership: TopicMembership<T>): Mono<Void> = add(topicMembership)
//
//    override fun remMember(topicMembership: TopicMembership<T>): Mono<Void> = rem(Key.funKey(topicMembership.key))

    override fun findBy(query: Map<String, String>): Flux<Key<T>> =
        //iterate the keys and return the flux of the first match
                 when (val queryBy = query.keys.first()) {
                MEMBER -> byMemberRepo.findByMember(stringToKey.apply(query[queryBy] ?: error("missing Member")))
                MEMBEROF -> byMemberOfRepo.findByMemberOf(stringToKey.apply(query[queryBy] ?: error("missing memberOf")))
                // TODO : add support for other queries such as when querying 2 fields, such as member and memberOf

                else -> Flux.empty()
            }
                    .map {
                        Key.funKey(it.key)
                    }

    override fun findUnique(query: Map<String, String>): Mono<out Key<T>> {
        TODO("Not yet implemented")
    }
}
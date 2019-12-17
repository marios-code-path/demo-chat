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

class MembershipIndexCassandra<T>(val byMemberRepo: ChatMembershipByMemberRepository<T>,
                               val byMemberOfRepo: ChatMembershipByMemberOfRepository<T>)
    : MembershipIndexService<T>  {

    override fun add(ent: Membership<T>, criteria: Map<T, String>): Mono<Void> =
            byMemberRepo
                    .save(ChatMembershipByMember(
                            CassandraUUIDKeyType(ent.key.id),
                            ChatMembershipKeyByMember(ent.member.id),
                            CassandraUUIDKeyType(ent.memberOf.id)
                    ))
                    .thenMany(byMemberOfRepo
                            .save(ChatMembershipByMemberOf(
                                    CassandraUUIDKeyType(ent.key.id),
                                    CassandraUUIDKeyType(ent.member.id),
                                    ChatMembershipKeyByMemberOf(ent.memberOf.id)
                            ))).then()

    override fun rem(ent: Membership<T>): Mono<Void> =
            byMemberRepo
                    .delete(ChatMembershipByMember(
                            CassandraUUIDKeyType(ent.key.id),
                            ChatMembershipKeyByMember(ent.member.id),
                            CassandraUUIDKeyType(ent.memberOf.id)))
                    .flatMap {
                        byMemberOfRepo
                                .delete(ChatMembershipByMemberOf(
                                        CassandraUUIDKeyType(ent.key.id),
                                        CassandraUUIDKeyType(ent.member.id),
                                        ChatMembershipKeyByMemberOf(ent.memberOf.id))
                                )
                    }
                    .then()

    override fun size(roomId: Key<T>): Mono<Int> =
            byMemberOfRepo.findByMemberOfId(roomId.id)
                    .reduce(0) { c, m ->
                        c + 1
                    }

    override fun addMember(membership: Membership<T>): Mono<Void> = add(membership, mapOf())

    override fun remMember(membership: Membership<T>): Mono<Void> = rem(membership)

    override fun findBy(query: Map<String, T>): Flux<out Key<T>> {
        return when (val queryBy = query.keys.first()) {
            MEMBER -> {
                byMemberRepo.findByMemberId(query[queryBy] ?: error("member not valid"))
            }
            MEMBEROF -> {
                byMemberOfRepo.findByMemberOfId(query[queryBy] ?: error("memberOf not valid"))
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
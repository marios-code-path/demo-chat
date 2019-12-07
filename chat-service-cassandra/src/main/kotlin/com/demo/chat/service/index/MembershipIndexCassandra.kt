package com.demo.chat.service.index

import com.demo.chat.domain.*
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

    override fun add(ent: RoomMembership, criteria: Map<String, String>): Mono<Void> =
            byMemberRepo
                    .save(ChatMembershipByMember(
                            CassandraEventKeyType(ent.key.id),
                            ChatMembershipKeyByMember(ent.member.id),
                            CassandraEventKeyType(ent.memberOf.id)
                    ))
                    .thenMany(byMemberOfRepo
                            .save(ChatMembershipByMemberOf(
                                    CassandraEventKeyType(ent.key.id),
                                    CassandraEventKeyType(ent.member.id),
                                    ChatMembershipKeyByMemberOf(ent.memberOf.id)
                            ))).then()

    override fun rem(ent: RoomMembership): Mono<Void> =
            byMemberRepo
                    .delete(ChatMembershipByMember(
                            CassandraEventKeyType(ent.key.id),
                            ChatMembershipKeyByMember(ent.member.id),
                            CassandraEventKeyType(ent.memberOf.id)))
                    .flatMap {
                        byMemberOfRepo
                                .delete(ChatMembershipByMemberOf(
                                        CassandraEventKeyType(ent.key.id),
                                        CassandraEventKeyType(ent.member.id),
                                        ChatMembershipKeyByMemberOf(ent.memberOf.id))
                                )
                    }
                    .then()

    override fun size(roomId: EventKey): Mono<Int> =
            byMemberOfRepo.findByMemberOfId(roomId.id)
                    .reduce(0) { c, m ->
                        c + 1
                    }

    override fun addMember(membership: RoomMembership): Mono<Void> = add(membership, mapOf())

    override fun remMember(membership: RoomMembership): Mono<Void> = rem(membership)

    override fun findBy(query: Map<String, String>): Flux<out EventKey> {
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
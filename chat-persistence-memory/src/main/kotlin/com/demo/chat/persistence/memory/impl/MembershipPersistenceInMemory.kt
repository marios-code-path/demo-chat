package com.demo.chat.persistence.memory.impl

import reactor.core.publisher.Mono

import com.demo.chat.service.core.StoreDomain

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.MembershipPersistence
import java.util.function.Function

class MembershipPersistenceInMemory<T>(
    private val keyService: IKeyService<T>,
    private val rootKeys: RootKeys<T>,
    keyFromEntity: Function<TopicMembership<T>, Key<T>>
) : InMemoryPersistence<T, TopicMembership<T>>(keyService, ChatDomain.TOPIC_MEMBERSHIP, rootKeys, keyFromEntity),
    MembershipPersistence<T> {
    /** A membership stores its key as a raw id, so the registry supplies the root. */
    override fun domainCheck(ent: TopicMembership<T>): Mono<Void> =
        StoreDomain.requireId(ent.key, ChatDomain.TOPIC_MEMBERSHIP, keyService, rootKeys)
}

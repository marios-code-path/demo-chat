package com.demo.chat.streams.functions

import com.demo.chat.domain.TopicMembership
import com.demo.chat.service.EnricherPersistenceStore
import com.demo.chat.service.MembershipIndexService
import reactor.core.publisher.Flux
import java.util.function.Function

class MembershipFunctions<T, Q>(
    private val persist: EnricherPersistenceStore<T, TopicMembershipRequest<T>, TopicMembership<T>>,
    private val index: MembershipIndexService<T, Q>
) {
    fun receiveMembershipRequest() = Function<Flux<TopicMembershipRequest<T>>, Flux<TopicMembership<T>>> { memReq ->
        memReq
            .flatMap { req -> persist.addEnriched(req) }
            .flatMap { membership -> index.add(membership).thenReturn(membership) }
    }
}
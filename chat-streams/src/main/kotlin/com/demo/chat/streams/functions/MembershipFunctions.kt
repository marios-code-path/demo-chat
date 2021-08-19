package com.demo.chat.streams.functions

import com.demo.chat.domain.TopicMembership
import com.demo.chat.service.EnricherPersistenceStore
import com.demo.chat.service.IndexService
import reactor.core.publisher.Flux
import java.util.function.Function

open class MembershipFunctions<T, Q>(
    private val persist: EnricherPersistenceStore<T, TopicMembershipRequest<T>, TopicMembership<T>>
) {
    open fun membershipCreateFunction() = Function<Flux<TopicMembershipRequest<T>>, Flux<TopicMembership<T>>> { memReq ->
        memReq
            .log()
            .flatMap { req -> persist.addEnriched(req) }
    }
}
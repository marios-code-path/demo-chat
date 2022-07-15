package com.demo.chat.client.rsocket.core

import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.service.IndexService
import com.demo.chat.service.MembershipIndexService
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveFlux
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class MembershipIndexClient<T, Q>(
    val t: IndexService<T, TopicMembership<T>, Q>,
    val prefix: String,
    val requester: RSocketRequester
) : MembershipIndexService<T, Q>,
    IndexService<T, TopicMembership<T>, Q> by t {
    override fun size(query: Q): Mono<Long> = requester
        .route("${prefix}size")
        .data(query as Any)     //TODO Type fix
        .retrieveMono()
}

open class IndexClient<T, E, Q>(
    private val prefix: String,
    private val requester: RSocketRequester,
) : IndexService<T, E, Q> {
    override fun add(entity: E): Mono<Void> = requester
        .route("${prefix}add")
        .data(entity as Any)        //TODO Type fix
        .retrieveMono()

    override fun rem(key: Key<T>): Mono<Void> = requester
        .route("${prefix}rem")
        .data(key)
        .retrieveMono()

    override fun findBy(query: Q): Flux<out Key<T>> = requester
        .route("${prefix}query")
        .data(query as Any)
        .retrieveFlux()

    override fun findUnique(query: Q): Mono<out Key<T>> {
        TODO("Not yet implemented")
    }
}
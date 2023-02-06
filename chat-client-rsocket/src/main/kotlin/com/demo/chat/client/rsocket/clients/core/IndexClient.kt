package com.demo.chat.client.rsocket.clients.core

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IndexService
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveFlux
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

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

    override fun findUnique(query: Q): Mono<out Key<T>> = requester
        .route("${prefix}unique")
        .data(query as Any)
        .retrieveMono()
}
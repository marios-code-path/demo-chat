package com.demo.chat.client.rsocket.clients.core

import com.demo.chat.domain.Key
import com.demo.chat.service.core.PersistenceStore
import org.springframework.core.ParameterizedTypeReference
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 *
 * TODO: NOT THE BEST IMPL. PLEASE REVISE ASAP. I BELIEVE
 * There can be a route-matcher for the prefix? Possibly a handler ?
 * Basically, some way to augment the route without sending in the prefix
 */
open class PersistenceClient<T, E>(
    private val prefix: String,
    private val requester: RSocketRequester,
    private val ref: ParameterizedTypeReference<E>,
) : PersistenceStore<T, E> {
    override fun key(): Mono<out Key<T>> = requester
        .route("${prefix}key")
        .retrieveMono()

    override fun add(ent: E): Mono<Void> = requester
        .route("${prefix}add")
        .data(ent as Any)
        .retrieveMono()

    override fun rem(key: Key<T>): Mono<Void> = requester
        .route("${prefix}rem")
        .data(key)
        .retrieveMono()

    override fun get(key: Key<T>): Mono<out E> = requester
        .route("${prefix}get")
        .data(key)
        .retrieveMono(ref)

    override fun all(): Flux<out E> = requester
        .route("${prefix}all")
        .retrieveFlux(ref)
}
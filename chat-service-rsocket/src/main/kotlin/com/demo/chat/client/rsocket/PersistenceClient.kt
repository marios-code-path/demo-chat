package com.demo.chat.client.rsocket

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.PersistenceStore
import org.springframework.core.ParameterizedTypeReference
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class UserClient<T>(requestor: RSocketRequester) :
        PersistenceClient<T, User<T>>(requestor, ParameterizedTypeReference.forType(User::class.java))

/**
 *
 * TODO: NOT THE BEST IMPL. PLEASE REVISE ASAP. I BELIEVE WE ARE RACING.
 */
open class PersistenceClient<T, V : Any>(val requestor: RSocketRequester,
                                         val ref: ParameterizedTypeReference<V>) : PersistenceStore<T, V> {
    override fun key(): Mono<out Key<T>> = requestor
            .route("key")
            .retrieveMono()

    override fun add(ent: V): Mono<Void> = requestor
            .route("add")
            .data(ent as Any)
            .retrieveMono()

    override fun rem(key: Key<T>): Mono<Void> = requestor
            .route("rem")
            .data(key)
            .retrieveMono()

    override fun get(key: Key<T>): Mono<out V> = requestor
            .route("get")
            .data(key)
            .retrieveMono(ref)

    override fun all(): Flux<out V> = requestor
            .route("all")
            .retrieveFlux(ref)

}
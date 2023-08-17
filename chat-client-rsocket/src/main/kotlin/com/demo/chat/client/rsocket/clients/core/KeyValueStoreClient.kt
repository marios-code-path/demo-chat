package com.demo.chat.client.rsocket.clients.core

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.service.core.KeyValueStore
import org.springframework.core.ParameterizedTypeReference
import org.springframework.messaging.rsocket.RSocketRequester
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class KeyValueStoreClientBase<T>(
    private val prefix: String,
    private val requester: RSocketRequester,
) : PersistenceClient<T, KeyValuePair<T, Any>>(prefix, requester, ParameterizedTypeReference.forType(KeyValuePair::class.java)),
    KeyValueStore<T, Any> {

    override fun <E> typedAll(typeArgument: Class<E>): Flux<KeyValuePair<T, E>> = requester
        .route("${prefix}typedAll")
        .retrieveFlux<KeyValuePair<T, E>>(ParameterizedTypeReference.forType(com.demo.chat.domain.KeyValuePair::class.java))

    override fun <E> typedByIds(ids: List<Key<T>>, typedArgument: Class<E>): Flux<KeyValuePair<T, E>> = requester
        .route("${prefix}typedByIds")
        .data(ids)
        .retrieveFlux<KeyValuePair<T, E>>(ParameterizedTypeReference.forType(com.demo.chat.domain.KeyValuePair::class.java))

    override fun <E> typedGet(key: Key<T>, typeArgument: Class<E>): Mono<KeyValuePair<T, E>> = requester
        .route("${prefix}typedGet")
        .data(key)
        .retrieveMono<KeyValuePair<T, E>>(ParameterizedTypeReference.forType(com.demo.chat.domain.KeyValuePair::class.java))
}
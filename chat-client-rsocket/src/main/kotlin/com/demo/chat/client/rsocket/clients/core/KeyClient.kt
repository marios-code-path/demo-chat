package com.demo.chat.client.rsocket.clients.core

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.domain.TypeUtil

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveMono
import org.springframework.messaging.rsocket.service.RSocketExchange
import reactor.core.publisher.Mono

/**
 * The key service over RSocket. A mint names a domain. [rootOf] answers a bare
 * id, and Jackson reads a small number as an Integer, so [typeUtil] converts
 * the answer to the key type. See `CHAT-avduuqwp`.
 */
open class KeyClient<T : Any>(
    private val prefix: String,
    private val requester: RSocketRequester,
    private val typeUtil: TypeUtil<T>,
) : IKeyService<T> {
    override fun key(domain: ChatDomain): Mono<out Key<T>> = requester
            .route("${prefix}key")
            .data(domain)
            .retrieveMono()

    override fun rem(key: Key<T>): Mono<Void> = requester
            .route("${prefix}rem")
            .data(key)
            .send()

    override fun exists(key: Key<T>): Mono<Boolean> = requester
            .route("${prefix}exists")
            .data(key)
            .retrieveMono()

    override fun rootOf(id: T): Mono<T> = requester
            .route("${prefix}rootOf")
            .data(id)
            .retrieveMono(Any::class.java)
            .map { typeUtil.assignFrom(it) }
}

interface KeyClientProxy<T>: IKeyService<T> {
    @RSocketExchange("key.key")
    override fun key(@Payload domain: ChatDomain): Mono<out Key<T>>

    @RSocketExchange("key.exists")
    override fun exists(@Payload  key: Key<T>): Mono<Boolean>

    @RSocketExchange("key.rem")
    override fun rem(@Payload key: Key<T>): Mono<Void>

    @RSocketExchange("key.rootOf")
    override fun rootOf(@Payload id: T): Mono<T & Any>
}

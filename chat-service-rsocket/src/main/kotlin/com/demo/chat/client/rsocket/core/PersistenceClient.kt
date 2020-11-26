package com.demo.chat.client.rsocket.core

import com.demo.chat.domain.*
import com.demo.chat.service.PersistenceStore
import org.springframework.core.ParameterizedTypeReference
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class UserPersistenceClient<T>(requester: RSocketRequester) :
        PersistenceClient<T, User<T>>("user.", requester, ParameterizedTypeReference.forType(User::class.java))

class MessagePersistenceClient<T, V>(requester: RSocketRequester) :
        PersistenceClient<T, Message<T, V>>("message.", requester, ParameterizedTypeReference.forType(Message::class.java))

class TopicPersistenceClient<T>(requester: RSocketRequester) :
        PersistenceClient<T, MessageTopic<T>>("topic.", requester, ParameterizedTypeReference.forType(MessageTopic::class.java))

class MembershipPersistenceClient<T>(requester: RSocketRequester) :
        PersistenceClient<T, TopicMembership<T>>("membership.", requester, ParameterizedTypeReference.forType(TopicMembership::class.java))

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
package com.demo.chat.client.rsocket

import com.demo.chat.domain.*
import com.demo.chat.service.PersistenceStore
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.listener.Topic
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class UserPersistenceClient<T>(requester: RSocketRequester) :
        PersistenceClient<T, User<T>>("user.", requester, ParameterizedTypeReference.forType(User::class.java))

class MessagePersistenceClient<T, V>(requester: RSocketRequester) :
        PersistenceClient<T, Message<T, V>>("message.",requester, ParameterizedTypeReference.forType(Message::class.java))

class MessageTopicPersistenceClient<T>(requester: RSocketRequester) :
        PersistenceClient<T, MessageTopic<T>>("topic.", requester, ParameterizedTypeReference.forType(Topic::class.java))

class MembershipPersistenceClient<T>(requester: RSocketRequester) :
        PersistenceClient<T, TopicMembership<T>>("membership.",requester, ParameterizedTypeReference.forType(TopicMembership::class.java))

/**
 *
 * TODO: NOT THE BEST IMPL. PLEASE REVISE ASAP. I BELIEVE
 * There can be a route-matcher for the prefix? Possibly a handler ?
 * Basically, some way to augment the route without sending in the prefix
 */
open class PersistenceClient<T, V : Any>(val prefix: String,
                                         val requestor: RSocketRequester,
                                         val ref: ParameterizedTypeReference<V>) : PersistenceStore<T, V> {
    override fun key(): Mono<out Key<T>> = requestor
            .route("${prefix}key")
            .retrieveMono()

    override fun add(ent: V): Mono<Void> = requestor
            .route("${prefix}add")
            .data(ent)
            .retrieveMono()

    override fun rem(key: Key<T>): Mono<Void> = requestor
            .route("${prefix}rem")
            .data(key)
            .retrieveMono()

    override fun get(key: Key<T>): Mono<out V> = requestor
            .route("${prefix}get")
            .data(key)
            .retrieveMono(ref)

    override fun all(): Flux<out V> = requestor
            .route("${prefix}all")
            .retrieveFlux(ref)
}
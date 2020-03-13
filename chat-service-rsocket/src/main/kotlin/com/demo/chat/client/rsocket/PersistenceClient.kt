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
        PersistenceClient<T, User<T>>(requester, ParameterizedTypeReference.forType(User::class.java))

class MessageTopicPersistenceClient<T>(requester: RSocketRequester) :
        PersistenceClient<T, MessageTopic<T>>(requester, ParameterizedTypeReference.forType(Topic::class.java))

class MessagePersistenceClient<T, E>(requester: RSocketRequester) :
        PersistenceClient<T, Message<T, E>>(requester, ParameterizedTypeReference.forType(Message::class.java))

class MembershipPersistenceClient<T>(requester: RSocketRequester) :
        PersistenceClient<T, TopicMembership<T>>(requester, ParameterizedTypeReference.forType(TopicMembership::class.java))

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
            .data(ent)
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
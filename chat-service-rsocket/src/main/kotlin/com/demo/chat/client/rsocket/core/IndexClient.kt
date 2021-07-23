package com.demo.chat.client.rsocket.core

import com.demo.chat.domain.*
import com.demo.chat.service.IndexService
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveFlux
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class UserIndexClient<T, Q>(prefix: String, requester: RSocketRequester) :
        IndexClient<T, User<T>, Q>(prefix, requester)

class MessageIndexClient<T, V, Q>(prefix: String, requester: RSocketRequester) :
        IndexClient<T, Message<T, V>, Q>(prefix, requester)

class TopicIndexClient<T, Q>(prefix: String, requester: RSocketRequester) :
        IndexClient<T, MessageTopic<T>, Q>(prefix, requester)

class MembershipIndexClient<T, Q>(prefix: String, requester: RSocketRequester) :
        IndexClient<T, TopicMembership<T>, Q>(prefix, requester)

open class IndexClient<T, E, Q>(
        private val prefix: String,
        private val requester: RSocketRequester,
) : IndexService<T, E, Q> {
    override fun add(entity: E): Mono<Void> = requester
            .route("${prefix}add")
            .data(entity as Any)
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
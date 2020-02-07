package com.demo.chat.client.rsocket

import com.demo.chat.domain.*
import com.demo.chat.service.IndexService
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveFlux
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class UserIndexClient<T>(requester: RSocketRequester) : IndexClient<T, User<T>, Map<String, String>>(requester)
class TopicIndexClient<T>(requester: RSocketRequester) : IndexClient<T, MessageTopic<T>, Map<String, String>>(requester)
class MembershipIndexClient<T>(requester: RSocketRequester) : IndexClient<T, TopicMembership<T>, Map<String, T>>(requester)
class MessageIndexClient<T, V>(requester: RSocketRequester) : IndexClient<T, Message<T, V>, Map<String, T>>(requester)

open class IndexClient<T, E : Any, Q : Any>(private val requester: RSocketRequester) : IndexService<T, E, Q> {
    override fun add(entity: E): Mono<Void> = requester
            .route("add")
            .data(entity)
            .retrieveMono()

    override fun rem(key: Key<T>): Mono<Void> = requester
            .route("rem")
            .data(key)
            .retrieveMono()

    override fun findBy(query: Q): Flux<out Key<T>> = requester
            .route("query")
            .data(query)
            .retrieveFlux()
}
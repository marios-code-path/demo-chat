package com.demo.chat.client.rsocket.edge

import com.demo.chat.ByIdRequest
import com.demo.chat.ByNameRequest
import com.demo.chat.MembershipRequest
import com.demo.chat.service.edge.ChatTopicService
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMemberships
import org.springframework.core.ParameterizedTypeReference
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class TopicClient<T, V>(
        private val prefix: String,
        private val requester: RSocketRequester,
) : ChatTopicService<T, V> {
    override fun addRoom(req: ByNameRequest): Mono<out Key<T>> = requester
            .route("${prefix}topic-add")
            .data(req)
            .retrieveMono(ParameterizedTypeReference.forType(Key::class.java))

    override fun deleteRoom(req: ByIdRequest<T>): Mono<Void> = requester
            .route("${prefix}topic-rem")
            .data(req)
            .retrieveMono(Void::class.java)

    override fun listRooms(): Flux<out MessageTopic<T>> = requester
            .route("${prefix}topic-list")
            .retrieveFlux(ParameterizedTypeReference.forType(MessageTopic::class.java))

    override fun getRoom(req: ByIdRequest<T>): Mono<out MessageTopic<T>> = requester
            .route("${prefix}topic-by-id")
            .data(req)
            .retrieveMono(ParameterizedTypeReference.forType(MessageTopic::class.java))

    override fun getRoomByName(req: ByNameRequest): Mono<out MessageTopic<T>> = requester
            .route("${prefix}topic-by-name")
            .data(req)
            .retrieveMono(ParameterizedTypeReference.forType(MessageTopic::class.java))

    override fun joinRoom(req: MembershipRequest<T>): Mono<Void> = requester
            .route("${prefix}topic-join")
            .data(req)
            .retrieveMono(Void::class.java)

    override fun leaveRoom(req: MembershipRequest<T>): Mono<Void> = requester
            .route("${prefix}topic-leave")
            .data(req)
            .retrieveMono(Void::class.java)

    override fun roomMembers(req: ByIdRequest<T>): Mono<TopicMemberships> = requester
            .route("${prefix}topic-members")
            .data(req)
            .retrieveMono()
}
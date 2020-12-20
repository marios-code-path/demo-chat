package com.demo.chat.client.rsocket.core

import com.demo.chat.MemberTopicRequest
import com.demo.chat.domain.Message
import com.demo.chat.service.PubSubService
import org.springframework.core.ParameterizedTypeReference
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveFlux
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class PubSubClient<T, V>(
        private val prefix: String,
        private val requester: RSocketRequester,
        private val ref: ParameterizedTypeReference<T>,
) : PubSubService<T, V> {

    fun subscribeOne(req: MemberTopicRequest<T>): Mono<Void> =
            requester.route("${prefix}subscribe")
                    .data(req)
                    .retrieveMono(Void::class.java)

    override fun subscribe(member: T, topic: T): Mono<Void> = subscribeOne(MemberTopicRequest(member,topic))


    override fun unSubscribe(member: T, topic: T): Mono<Void> = unSubscribeOne(MemberTopicRequest(member, topic))

    fun unSubscribeOne(req: MemberTopicRequest<T>): Mono<Void> =
            requester.route("${prefix}unsubscribe")
                    .data(req)
                    .retrieveMono(Void::class.java)

    override fun unSubscribeAll(member: T): Mono<Void> =
            requester.route("${prefix}unSubscribeAll")
                    .data(Mono.just(member), ref)
                    .retrieveMono(Void::class.java)

    override fun unSubscribeAllIn(topic: T): Mono<Void> =
            requester.route("${prefix}unSubscribeAllIn")
                    .data(Mono.just(topic), ref)
                    .retrieveMono(Void::class.java)

    override fun sendMessage(message: Message<T, V>): Mono<Void> =
            requester.route("${prefix}sendMessage")
                    .data(message)
                    .retrieveMono(Void::class.java)

    override fun receiveOn(topic: T): Flux<out Message<T, V>> =
            requester.route("${prefix}receiveOn")
                    .data(Mono.just(topic), ref)
                    .retrieveFlux()

    override fun exists(topic: T): Mono<Boolean> =
            requester.route("${prefix}exists")
                    .data(Mono.just(topic), ref)
                    .retrieveMono()

    override fun add(id: T): Mono<Void> =
            requester.route("${prefix}add")
                    .data(Mono.just(id), ref)
                    .retrieveMono()

    override fun rem(id: T): Mono<Void> =
            requester.route("${prefix}rem")
                    .data(Mono.just(id), ref)
                    .retrieveMono()

    override fun getByUser(uid: T): Flux<T> =
            requester.route("${prefix}getByUser")
                    .data(Mono.just(uid), ref)
                    .retrieveFlux(ref)

    override fun getUsersBy(id: T): Flux<T> =
            requester.route("${prefix}getUsersBy")
                    .data(Mono.just(id), ref)
                    .retrieveFlux(ref)
}
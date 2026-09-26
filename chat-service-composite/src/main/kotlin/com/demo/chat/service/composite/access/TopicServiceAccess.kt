package com.demo.chat.service.composite.access

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.security.AccessBroker
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class TopicServiceAccess<T, V>(
    private val authMetadataAccessBroker: AccessBroker<T>,
    private val principalSupplier: () -> Publisher<Key<T>>,
    private val rootKeys: RootKeys<T>,
    private val that: ChatTopicService<T, V>,
    private val verifier: KeyVerifier<T>,
) : ChatTopicService<T, V> {
    override fun addRoom(req: ByStringRequest): Mono<out Key<T>> = authMetadataAccessBroker
        .hasAccessByPrincipal(Mono.from(principalSupplier()), rootKeys.of(ChatDomain.MESSAGE_TOPIC), "CREATE")
        .then(that.addRoom(req))

    override fun listRooms(): Flux<out MessageTopic<T>> = authMetadataAccessBroker
        .hasAccessByPrincipal(Mono.from(principalSupplier()), rootKeys.of(ChatDomain.MESSAGE_TOPIC), "READ")
        .thenMany(that.listRooms())

    override fun getRoomByName(req: ByStringRequest): Mono<out MessageTopic<T>> = authMetadataAccessBroker
        .hasAccessByPrincipal(Mono.from(principalSupplier()), rootKeys.of(ChatDomain.MESSAGE_TOPIC), "READ")
        .then(that.getRoomByName(req))

    override fun roomMembers(req: ByIdRequest<T>): Mono<TopicMemberships> = verifier.resolve(req.id, ChatDomain.MESSAGE_TOPIC)
        .flatMap { authMetadataAccessBroker.hasAccessByPrincipal(Mono.from(principalSupplier()), it.key, "READ") }
        .then(that.roomMembers(req))

    override fun leaveRoom(req: MembershipRequest<T>): Mono<Void> = verifier.resolve(req.roomId, ChatDomain.MESSAGE_TOPIC)
        .flatMap { authMetadataAccessBroker.hasAccessByPrincipal(Mono.from(principalSupplier()), it.key, "LEAVE") }
        .then(that.leaveRoom(req))

    override fun joinRoom(req: MembershipRequest<T>): Mono<Void> = verifier.resolve(req.roomId, ChatDomain.MESSAGE_TOPIC)
        .flatMap { authMetadataAccessBroker.hasAccessByPrincipal(Mono.from(principalSupplier()), it.key, "JOIN") }
        .then(that.joinRoom(req))

    override fun getRoom(req: ByIdRequest<T>): Mono<out MessageTopic<T>> = verifier.resolve(req.id, ChatDomain.MESSAGE_TOPIC)
        .flatMap { authMetadataAccessBroker.hasAccessByPrincipal(Mono.from(principalSupplier()), it.key, "READ") }
        .then(that.getRoom(req))

    override fun deleteRoom(req: ByIdRequest<T>): Mono<Void> = verifier.resolve(req.id, ChatDomain.MESSAGE_TOPIC)
        .flatMap { authMetadataAccessBroker.hasAccessByPrincipal(Mono.from(principalSupplier()), it.key, "DELETE") }
        .then(that.deleteRoom(req))
}
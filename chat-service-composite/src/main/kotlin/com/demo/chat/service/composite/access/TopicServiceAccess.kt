package com.demo.chat.service.composite.access

import com.demo.chat.domain.*
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
    private val that: ChatTopicService<T, V>
) : ChatTopicService<T, V> {
    override fun addRoom(req: ByStringRequest): Mono<out Key<T>> = authMetadataAccessBroker
        .getAccessFromPublisher(Mono.from(principalSupplier()), rootKeys.getRootKey(MessageTopic::class.java), "CREATE")
        .then(that.addRoom(req))

    override fun listRooms(): Flux<out MessageTopic<T>> = authMetadataAccessBroker
        .getAccessFromPublisher(Mono.from(principalSupplier()), rootKeys.getRootKey(MessageTopic::class.java), "READ")
        .thenMany(that.listRooms())

    override fun getRoomByName(req: ByStringRequest): Mono<out MessageTopic<T>> = authMetadataAccessBroker
        .getAccessFromPublisher(Mono.from(principalSupplier()), rootKeys.getRootKey(MessageTopic::class.java), "READ")
        .then(that.getRoomByName(req))

    override fun roomMembers(req: ByIdRequest<T>): Mono<TopicMemberships> = authMetadataAccessBroker
        .getAccessFromPublisher(Mono.from(principalSupplier()), Key.funKey(req.id), "READ")
        .then(that.roomMembers(req))

    override fun leaveRoom(req: MembershipRequest<T>): Mono<Void> = authMetadataAccessBroker
        .getAccessFromPublisher(Mono.from(principalSupplier()), Key.funKey(req.roomId), "LEAVE")
        .then(that.leaveRoom(req))

    override fun joinRoom(req: MembershipRequest<T>): Mono<Void> = authMetadataAccessBroker
        .getAccessFromPublisher(Mono.from(principalSupplier()), Key.funKey(req.roomId), "JOIN")
        .then(that.joinRoom(req))

    override fun getRoom(req: ByIdRequest<T>): Mono<out MessageTopic<T>> = authMetadataAccessBroker
        .getAccessFromPublisher(Mono.from(principalSupplier()), Key.funKey(req.id), "READ")
        .then(that.getRoom(req))

    override fun deleteRoom(req: ByIdRequest<T>): Mono<Void> = authMetadataAccessBroker
        .getAccessFromPublisher(Mono.from(principalSupplier()), Key.funKey(req.id), "DELETE")
        .then(that.deleteRoom(req))
}
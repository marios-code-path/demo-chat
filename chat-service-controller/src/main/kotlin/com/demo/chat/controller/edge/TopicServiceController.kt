package com.demo.chat.controller.edge

import com.demo.chat.ByIdRequest
import com.demo.chat.ByNameRequest
import com.demo.chat.MembershipRequest
import com.demo.chat.domain.*
import com.demo.chat.service.*
import com.demo.chat.service.edge.ChatTopicService
import com.fasterxml.jackson.annotation.JsonTypeName
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function
import java.util.function.Supplier

@JsonTypeName("JoinAlert")
data class JoinAlert<T, V>(override val key: MessageKey<T>, override val data: V) : Message<T, V> {
    override val record: Boolean
        get() = false
}

@JsonTypeName("LeaveAlert")
data class LeaveAlert<T, V>(override val key: MessageKey<T>, override val data: V) : Message<T, V> {
    override val record: Boolean
        get() = false
}

open class TopicServiceController<T, V, Q>(
        private val topicPersistence: PersistenceStore<T, MessageTopic<T>>,
        private val topicIndex: IndexService<T, MessageTopic<T>, Q>,
        private val messaging: PubSubTopicExchangeService<T, V>,
        private val userPersistence: PersistenceStore<T, User<T>>,
        private val membershipPersistence: PersistenceStore<T, TopicMembership<T>>,
        private val membershipIndex: IndexService<T, TopicMembership<T>, Q>,
        private val emptyDataCodec: Supplier<V>,
        private val topicNameToQuery: Function<ByNameRequest, Q>,
        private val membershipIdToQuery: Function<ByIdRequest<T>, Q>,
) : ChatTopicService<T, V> {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("topic-add")
    override fun addRoom(req: ByNameRequest): Mono<out Key<T>> =
            topicPersistence
                    .key()
                    .flatMap { key ->
                        val room = MessageTopic.create(Key.funKey(key.id), req.name)
                        topicPersistence
                                .add(room)
                                .flatMap {
                                    topicIndex.add(room)
                                }
                                .then(messaging.add(key.id))
                                .map { key }
                    }

    @MessageMapping("topic-rem")
    override fun deleteRoom(req: ByIdRequest<T>): Mono<Void> =
            topicPersistence
                    .get(Key.funKey(req.id))
                    .flatMap {
                        topicPersistence.rem(it.key)
                                .then(topicIndex.rem(it.key))
                                .then(messaging.unSubscribeAllIn(it.key.id))
                                .then(messaging.rem(it.key.id))
                    }
                    .then()

    @MessageMapping("topic-list")
    override fun listRooms(): Flux<out MessageTopic<T>> =
            topicPersistence
                    .all()

    @MessageMapping("topic-by-id")
    override fun getRoom(req: ByIdRequest<T>): Mono<out MessageTopic<T>> =
            topicPersistence
                    .get(Key.funKey(req.id))

    @MessageMapping("topic-by-name")
    override fun getRoomByName(req: ByNameRequest): Mono<out MessageTopic<T>> =
            topicIndex
                    .findBy(topicNameToQuery.apply(req))
                    .single()
                    .flatMap {
                        topicPersistence.get(it)
                    }

    @MessageMapping("topic-join")
    override fun joinRoom(req: MembershipRequest<T>): Mono<Void> =
            topicPersistence
                    .get(Key.funKey(req.roomId))
                    .switchIfEmpty(Mono.error(TopicNotFoundException))
                    .then(
                            membershipPersistence
                                    .key()
                                    .flatMap { key ->
                                        membershipPersistence
                                                .add(TopicMembership.create(key.id, req.roomId, req.uid))
                                                .thenMany(messaging
                                                        .sendMessage(JoinAlert(MessageKey.create(key.id, req.roomId, req.uid),
                                                                emptyDataCodec.get()))
                                                        .then(messaging.subscribe(req.uid, req.roomId)))
                                                .then()
                                    }
                    )


    @MessageMapping("topic-leave")
    override fun leaveRoom(req: MembershipRequest<T>): Mono<Void> =
            membershipPersistence
                    .get(Key.funKey(req.roomId))
                    .flatMap { m ->
                        membershipPersistence.rem(Key.funKey(m.key))
                                .thenMany(messaging
                                        .sendMessage(LeaveAlert(MessageKey.create(m.key, m.member, m.memberOf),
                                                emptyDataCodec.get()))
                                        .then(messaging.unSubscribe(m.member, m.memberOf)))
                                .then()

                    }

    @MessageMapping("topic-members")
    override fun roomMembers(req: ByIdRequest<T>): Mono<TopicMemberships> =
            membershipIndex.findBy(membershipIdToQuery.apply(req))
                    .collectList()
                    .flatMapMany { membershipList ->
                        membershipPersistence.byIds(membershipList)
                    }
                    .flatMap { membership ->
                        userPersistence
                                .get(Key.funKey(membership.member))
                                .map { user ->
                                    TopicMember(user.key.id.toString(), user.handle, user.imageUri)
                                }
                    }
                    .collectList()
                    .map {
                        TopicMemberships(it.toSet())
                    }
}
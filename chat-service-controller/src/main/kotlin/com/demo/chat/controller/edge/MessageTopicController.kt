package com.demo.chat.controller.edge

import com.demo.chat.ByIdRequest
import com.demo.chat.MembershipRequest
import com.demo.chat.ByNameRequest
import com.demo.chat.codec.Codec
import com.demo.chat.domain.*
import com.demo.chat.service.*
import com.fasterxml.jackson.annotation.JsonTypeName
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import com.demo.chat.service.MembershipIndexService.Companion.MEMBEROF as MEMBEROF1

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

open class MessageTopicController<T, V>(private val topicPersistence: TopicPersistence<T>,
                                        private val topicIndex: TopicIndexService<T>,
                                        private val messaging: ChatTopicMessagingService<T, V>,
                                        private val userPersistence: UserPersistence<T>,
                                        private val membershipPersistence: MembershipPersistence<T>,
                                        private val membershipIndex: MembershipIndexService<T>,
                                        private val emptyDataCodec: Codec<Unit, V>) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("room-add")
    fun addRoom(req: ByNameRequest): Mono<out Key<T>> =
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

    @MessageMapping("room-rem")
    fun deleteRoom(req: ByIdRequest<T>): Mono<Void> =
            topicPersistence
                    .get(Key.funKey(req.id))
                    .flatMap {
                        topicPersistence.rem(it.key)
                                .then(topicIndex.rem(it.key))
                                .then(messaging.unSubscribeAllIn(it.key.id))
                                .then(messaging.rem(it.key.id))
                    }
                    .then()

    @MessageMapping("room-list")
    fun listRooms(): Flux<out MessageTopic<T>> =
            topicPersistence
                    .all()

    @MessageMapping("room-by-id")
    fun getRoom(req: ByIdRequest<T>): Mono<out MessageTopic<T>> =
            topicPersistence
                    .get(Key.funKey(req.id))

    @MessageMapping("room-by-name")
    fun getRoomByName(req: ByNameRequest): Mono<out MessageTopic<T>> =
            topicIndex
                    .findBy(mapOf(Pair(TopicIndexService.NAME, req.name)))
                    .single()
                    .flatMap {
                        topicPersistence.get(it)
                    }

    @MessageMapping("room-join")
    fun joinRoom(req: MembershipRequest<T>): Mono<Void> =
            membershipPersistence
                    .key()
                    .flatMap { key ->
                        membershipPersistence
                                .add(TopicMembership.create(key.id, req.roomId, req.uid))
                                .thenMany(messaging
                                        .sendMessage(JoinAlert(MessageKey.create(key.id, req.roomId, req.uid),
                                                emptyDataCodec.decode(Unit)))
                                        .then(messaging.subscribe(req.uid, req.roomId)))
                                .then()
                    }


    @MessageMapping("room-leave")
    fun leaveRoom(req: MembershipRequest<T>): Mono<Void> =
            membershipPersistence
                    .get(Key.funKey(req.roomId))
                    .flatMap { m ->
                        membershipPersistence.rem(Key.funKey(m.key))
                                .thenMany(messaging
                                        .sendMessage(LeaveAlert(MessageKey.create(m.key, m.member, m.memberOf),
                                                emptyDataCodec.decode(Unit)))
                                        .then(messaging.unSubscribe(m.member, m.memberOf)))
                                .then()

                    }

    @MessageMapping("room-members")
    fun roomMembers(req: ByIdRequest<T>): Mono<TopicMemberships> =
            membershipIndex.findBy(mapOf(Pair(MEMBEROF1, req.id)))
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
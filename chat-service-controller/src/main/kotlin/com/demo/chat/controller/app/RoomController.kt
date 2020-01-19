package com.demo.chat.controller.app

import com.demo.chat.*
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

open class RoomController<T, V>(val topicPersistence: PersistenceStore<T, MessageTopic<T>>,
                                val topicIndex: TopicIndexService<T>,
                                val topicService: ChatTopicMessagingService<T, V>,
                                val userPersistence: UserPersistence<T>,
                                val membershipPersistence: MembershipPersistence<T>,
                                val membershipIndex: MembershipIndexService<T>,
                                val emptyToDataEncoder: Codec<String, V>) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("room-add")
    fun addRoom(req: RoomCreateRequest): Mono<out Key<T>> =
            topicPersistence
                    .key()
                    .flatMap { key ->
                        val room = MessageTopic.create(Key.funKey(key.id), req.roomName)
                        topicPersistence
                                .add(room)
                                .flatMap {
                                    topicIndex.add(room)
                                }
                                .then(topicService.add(key.id))
                                .map { key }
                    }

    @MessageMapping("room-rem")
    fun deleteRoom(req: RoomRequestId<T>): Mono<Void> =
            topicPersistence
                    .get(Key.funKey(req.roomId))
                    .flatMap {
                        topicPersistence.rem(it.key)
                                .then(topicIndex.rem(it.key))
                                .then(topicService.unSubscribeAllIn(it.key.id))
                                .then(topicService.rem(it.key.id))
                    }
                    .then()

    @MessageMapping("room-list")
    fun listRooms(req: RoomRequestId<T>): Flux<out MessageTopic<T>> =
            topicPersistence
                    .all()

    @MessageMapping("room-by-id")
    fun getRoom(req: RoomRequestId<T>): Mono<out MessageTopic<T>> =
            topicPersistence
                    .get(Key.funKey(req.roomId))

    @MessageMapping("room-by-name")
    fun getRoomByName(req: RoomRequestName): Mono<out MessageTopic<T>> =
            topicIndex
                    .findBy(mapOf(Pair(TopicIndexService.NAME, req.name)))
                    .single()
                    .flatMap {
                        topicPersistence.get(it)
                    }

    @MessageMapping("room-join")
    fun joinRoom(req: RoomJoinRequest<T>): Mono<Void> =
            membershipPersistence
                    .key()
                    .flatMap { eventKey ->
                        membershipPersistence
                                .add(TopicMembership.create(eventKey, Key.funKey(req.roomId), Key.funKey(req.uid)))
                                .thenMany(topicService
                                        .sendMessage(JoinAlert(MessageKey.create(eventKey.id, req.roomId, req.uid),
                                                emptyToDataEncoder.decode("")))
                                        .then(topicService.subscribe(req.uid, req.roomId)))
                                .then()
                    }


    @MessageMapping("room-leave")
    fun leaveRoom(req: RoomLeaveRequest<T>): Mono<Void> =
            membershipPersistence
                    .get(Key.funKey(req.roomId))
                    .flatMap { m ->
                        membershipPersistence.rem(m.key)
                                .thenMany(topicService
                                        .sendMessage(LeaveAlert(MessageKey.create(m.key.id, m.member.id, m.memberOf.id),
                                                emptyToDataEncoder.decode("")))
                                        .then(topicService.unSubscribe(m.member.id, m.memberOf.id)))
                                .then()

                    }

    @MessageMapping("room-members")
    fun roomMembers(req: RoomRequestId<T>): Mono<TopicMemberships<T>> =
            membershipIndex.findBy(mapOf(Pair(MEMBEROF1, req.roomId)))
                    .collectList()
                    .flatMapMany { membershipList ->
                        membershipPersistence.byIds(membershipList)
                    }
                    .flatMap { membership ->
                        userPersistence
                                .get(Key.funKey(membership.member.id))
                                .map { user ->
                                    TopicMember(user.key.id, user.handle, user.imageUri)
                                }
                    }
                    .collectList()
                    .map {
                        TopicMemberships(it.toSet())
                    }
}
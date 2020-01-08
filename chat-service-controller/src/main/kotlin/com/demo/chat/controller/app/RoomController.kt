package com.demo.chat.controller.app

import com.demo.chat.*
import com.demo.chat.domain.*
import com.demo.chat.service.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

open class RoomController(val topicPersistence: TopicPersistence,
                          val topicIndex: TopicIndexService,
                          val topicService: ChatTopicService,
                          val userPersistence: UserPersistence,
                          val membershipPersistence: MembershipPersistence,
                          val membershipIndex: MembershipIndexService) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("room-add")
    fun addRoom(req: RoomCreateRequest): Mono<out Key<UUID>> =
            topicPersistence
                    .key()
                    .flatMap { key ->
                        val room = MessageTopic.create(Key.eventKey(key.id), req.roomName)
                        topicPersistence
                                .add(room)
                                .flatMap {
                                    topicIndex.add(room, mapOf())
                                }
                                .then(topicService.add(key.id))
                                .map { key }
                    }

    @MessageMapping("room-rem")
    fun deleteRoom(req: RoomRequestId): Mono<Void> =
            topicPersistence
                    .get(Key.eventKey(req.roomId))
                    .flatMap {
                        topicPersistence.rem(it.key)
                                .then(topicIndex.rem(it))
                                .then(topicService.unSubscribeAllIn(it.key.id))
                                .then(topicService.rem(it.key.id))
                    }
                    .then()

    @MessageMapping("room-list")
    fun listRooms(req: RoomRequestId): Flux<out MessageTopic> =
            topicPersistence
                    .all()

    @MessageMapping("room-by-id")
    fun getRoom(req: RoomRequestId): Mono<out MessageTopic> =
            topicPersistence
                    .get(Key.eventKey(req.roomId))

    @MessageMapping("room-by-name")
    fun getRoomByName(req: RoomRequestName): Mono<out MessageTopic> =
            topicIndex
                    .findBy(mapOf(Pair(TopicIndexService.NAME, req.name)))
                    .single()
                    .flatMap {
                        topicPersistence.get(it)
                    }

    @MessageMapping("room-join")
    fun joinRoom(req: RoomJoinRequest): Mono<Void> =
            membershipPersistence
                    .key()
                    .flatMap { eventKey ->
                        membershipPersistence
                                .add(TopicMembership.create(eventKey, Key.eventKey(req.roomId), Key.eventKey(req.uid)))
                                .thenMany(topicService
                                        .sendMessage(JoinAlert.create(eventKey.id, req.roomId, req.uid))
                                        .then(topicService.subscribe(req.uid, req.roomId)))
                                .then()
                    }


    @MessageMapping("room-leave")
    fun leaveRoom(req: RoomLeaveRequest): Mono<Void> =
            membershipPersistence
                    .get(Key.eventKey(req.roomId))
                    .flatMap { m ->
                        membershipPersistence.rem(m.key)
                                .thenMany(topicService
                                        .sendMessage(LeaveAlert.create(m.key.id, m.member.id, m.memberOf.id))
                                        .then(topicService.unSubscribe(m.member.id, m.memberOf.id)))
                                .then()

                    }

    @MessageMapping("room-members")
    fun roomMembers(req: RoomRequestId): Mono<TopicMemberships> =
            membershipIndex.findBy(mapOf(Pair("MEMBEROF", req.roomId.toString())))
                    .collectList()
                    .flatMapMany { membershipList ->
                        membershipPersistence.byIds(membershipList)
                    }
                    .flatMap { membership ->
                        userPersistence
                                .get(Key.eventKey(membership.member.id))
                                .map { user ->
                                    TopicMember(user.key.id, user.handle, user.imageUri)
                                }
                    }
                    .collectList()
                    .map {
                        TopicMemberships(it.toSet())
                    }
}
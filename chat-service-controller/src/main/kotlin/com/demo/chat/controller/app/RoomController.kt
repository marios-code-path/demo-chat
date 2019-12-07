package com.demo.chat.controller.app

import com.demo.chat.*
import com.demo.chat.domain.*
import com.demo.chat.service.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class RoomController(val roomPersistence: RoomPersistence,
                          val roomIndex: RoomIndexService,
                          val topicService: ChatTopicService,
                          val userPersistence: UserPersistence,
                          val membershipPersistence: MembershipPersistence,
                          val membershipIndex: MembershipIndexService) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("room-add")
    fun addRoom(req: RoomCreateRequest): Mono<out UUIDKey> =
            roomPersistence
                    .key()
                    .flatMap { key ->
                        val room = EventTopic.create(TopicKey.create(key.id), req.roomName)
                        roomPersistence
                                .add(room)
                                .flatMap {
                                    roomIndex.add(room, mapOf())
                                }
                                .then(topicService.add(key.id))
                                .map { key }
                    }

    @MessageMapping("room-rem")
    fun deleteRoom(req: RoomRequestId): Mono<Void> =
            roomPersistence
                    .get(Key.eventKey(req.roomId))
                    .flatMap {
                        roomPersistence.rem(it.key)
                                .then(roomIndex.rem(it))
                                .then(topicService.unSubscribeAllIn(it.key.id))
                                .then(topicService.rem(it.key.id))
                    }
                    .then()

    @MessageMapping("room-list")
    fun listRooms(req: RoomRequestId): Flux<out EventTopic> =
            roomPersistence
                    .all()

    @MessageMapping("room-by-id")
    fun getRoom(req: RoomRequestId): Mono<out EventTopic> =
            roomPersistence
                    .get(Key.eventKey(req.roomId))

    @MessageMapping("room-by-name")
    fun getRoomByName(req: RoomRequestName): Mono<out EventTopic> =
            roomIndex
                    .findBy(mapOf(Pair(RoomIndexService.NAME, req.name)))
                    .single()
                    .flatMap {
                        roomPersistence.get(it)
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
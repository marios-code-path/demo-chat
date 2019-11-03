package com.demo.chat.controllers

import com.demo.chat.*
import com.demo.chat.domain.*
import com.demo.chat.service.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Controller
class RoomController(val roomPersistence: ChatRoomPersistence,
                     val roomIndex: ChatRoomIndexService,
                     val topicService: ChatTopicService,
                     val userPersistence: ChatUserPersistence,
                     val membershipPersistence: ChatMembershipPersistence,
                     val membershipIndex: ChatMembershipIndexService) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("room-add")
    fun addRoom(req: RoomCreateRequest): Mono<out EventKey> =
            roomPersistence
                    .key()
                    .flatMap { key ->
                        val room = Room.create(RoomKey.create(key.id, req.roomName), setOf())
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
                    .get(EventKey.create(req.roomId))
                    .flatMap {
                        roomPersistence.rem(it.key)
                                .then(roomIndex.rem(it))
                                .then(topicService.unSubscribeAllIn(it.key.id))
                                .then(topicService.rem(it.key.id))
                    }
                    .then()

    @MessageMapping("room-list")
    fun listRooms(req: RoomRequestId): Flux<out Room> =
            roomPersistence
                    .all()

    @MessageMapping("room-by-id")
    fun getRoom(req: RoomRequestId): Mono<out Room> =
            roomPersistence
                    .get(EventKey.create(req.roomId))

    @MessageMapping("room-by-name")
    fun getRoomByName(req: RoomRequestName): Mono<out Room> =
            roomIndex
                    .findBy(mapOf(Pair(ChatRoomIndexService.NAME, req.name)))
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
                                .add(Membership.create(eventKey, EventKey.create(req.roomId), EventKey.create(req.uid)))
                                .thenMany(topicService
                                        .sendMessage(JoinAlert.create(eventKey.id, req.roomId, req.uid))
                                        .then(topicService.subscribe(req.uid, req.roomId)))
                                .then()
                    }


    @MessageMapping("room-leave")
    fun leaveRoom(req: RoomLeaveRequest): Mono<Void> =
            membershipPersistence
                    .get(EventKey.create(req.roomId))
                    .flatMap {
                        membershipPersistence.rem(it.key)
                                .thenMany(topicService
                                        .sendMessage(LeaveAlert.create(it.key.id, it.member.id, it.memberOf.id))
                                        .then(topicService.unSubscribe(it.member.id, it.memberOf.id)))
                                .then()

                    }

    @MessageMapping("room-members")
    fun roomMembers(req: RoomRequestId): Mono<RoomMemberships> =
            membershipIndex.findBy(mapOf(Pair("MEMBEROF", req.roomId.toString())))
                    .collectList()
                    .flatMapMany { membershipList ->
                        membershipPersistence.byIds(membershipList)
                    }
                    .flatMap { membership ->
                        userPersistence
                                .get(EventKey.create(membership.member.id))
                                .map { user ->
                                    RoomMember(req.roomId, user.key.handle, user.imageUri)
                                }
                    }
                    .collectList()
                    .map {
                        RoomMemberships(it.toSet())
                    }
}
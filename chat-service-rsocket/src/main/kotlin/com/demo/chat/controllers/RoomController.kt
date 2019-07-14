package com.demo.chat.controllers

import com.demo.chat.RoomCreateRequest
import com.demo.chat.RoomJoinRequest
import com.demo.chat.RoomLeaveRequest
import com.demo.chat.RoomRequest
import com.demo.chat.domain.*
import com.demo.chat.service.ChatRoomPersistence
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.ChatUserPersistence
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

@Controller
class RoomController(val roomPersistence: ChatRoomPersistence<out Room, RoomKey>,
                     val userPersistence: ChatUserPersistence<out User, UserKey>,
                     val topicService: ChatTopicService) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("room-create")
    fun createRoom(req: RoomCreateRequest): Mono<out RoomKey> =
            roomPersistence
                    .add(req.roomName)
                    .flatMap { key ->
                        topicService
                                .add(key.roomId)
                                .map { key }
                    }

    @MessageMapping("room-delete")
    fun deleteRoom(req: RoomRequest): Mono<Void> =
            roomPersistence
                    .rem(req.roomId)
                    .flatMap {
                        topicService
                                .unSubscribeAllIn(req.roomId)
                                .then(topicService.rem(req.roomId))
                    }
                    .then()

    @MessageMapping("room-list")
    fun listRooms(req: RoomRequest): Flux<out Room> =
            roomPersistence
                    .getAll(true)

    @MessageMapping("room-msgId")
    fun getRoom(req: RoomRequest): Mono<out Room> =
            roomPersistence
                    .getById(req.roomId)

    @MessageMapping("room-join")
    fun joinRoom(req: RoomJoinRequest): Mono<Void> =
            roomPersistence
                    .addMember(req.uid, req.roomId)
                    .flatMap {
                        topicService
                                .sendMessage(JoinAlert
                                        .create(UUID.randomUUID(), req.roomId, req.uid))
                                .then(topicService
                                        .subscribe(req.uid, req.roomId))
                    }.then()

    @MessageMapping("room-leave")
    fun leaveRoom(req: RoomLeaveRequest): Mono<Void> =
            roomPersistence
                    .remMember(req.uid, req.roomId)
                    .flatMap {
                        topicService.sendMessage(LeaveAlert.create(UUID.randomUUID(), req.roomId, req.uid))
                                .then(topicService.unSubscribe(req.uid, req.roomId))
                    }.then()

    @MessageMapping("room-members")
    fun roomMembers(req: RoomRequest): Mono<RoomMemberships> = roomPersistence
            .members(req.roomId)
            .flatMap { members ->
                userPersistence // kludge! This is nullable ! make sure enforce non-nullability across service contract
                        .findByIds(Flux.fromStream(members.stream()))
                        .map { u ->
                            RoomMember(u.key.id, u.key.handle)
                        }
                        .collectList()
                        .map { memberList ->
                            RoomMemberships(memberList.toSet())
                        }
            }
}
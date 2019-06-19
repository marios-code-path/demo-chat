package com.demo.chat.controllers

import com.demo.chat.*
import com.demo.chat.domain.*
import com.demo.chat.service.ChatRoomService
import com.demo.chat.service.ChatUserService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Controller
class RoomController(val roomService: ChatRoomService<out Room<RoomKey>, RoomKey>,
                     val userService: ChatUserService<out User<UserKey>, UserKey>) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("room-create")
    fun createRoom(req: RoomCreateRequest): Mono<out RoomKey> =
            roomService
                    .createRoom(req.roomName)

    @MessageMapping("room-delete")
    fun deleteRoom(req: RoomRequest): Mono<Void> =
            roomService
                    .deleteRoom(req.roomId)

    @MessageMapping("room-list")
    fun listRooms(req: RoomRequest): Flux<out Room<RoomKey>> =
            roomService
                    .getRooms(true)

    @MessageMapping("room-msgId")
    fun getRoom(req: RoomRequest): Mono<out Room<RoomKey>> =
            roomService
                    .getRoomById(req.roomId)

    @MessageMapping("room-join")
    fun joinRoom(req: RoomJoinRequest): Mono<Void> =
            roomService
                    .joinRoom(req.uid, req.roomId)

    @MessageMapping("room-leave")
    fun leaveRoom(req: RoomLeaveRequest): Mono<Void> =
            roomService
                    .leaveRoom(req.uid, req.roomId)

    @MessageMapping("room-members")
    fun roomMembers(req: RoomRequest): Mono<RoomMemberships> = roomService
            .roomMembers(req.roomId)
            .flatMap { members ->
                userService // kludge! This is nullable ! make sure enforce non-nullability across service contract
                        .getUsersById(Flux.fromStream(members.stream()))
                        .map { u ->
                            RoomMember(u.key.userId, u.key.handle)
                        }
                        .collectList()
                        .map { memberList ->
                            RoomMemberships(memberList.toSet())
                        }
            }
}
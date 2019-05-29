package com.demo.chat.chatconsumers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.stereotype.Component
import java.util.*

@Component
class ChatRoomClient(val socket: RSocketRequester){
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    fun callCreateRoom(roomName: String) = socket
            .route("room-create")
            .data(RoomCreateRequest(roomName))
            .retrieveMono(RoomCreateResponse::class.java)

    fun callJoinRoom(uid: UUID, roomId: UUID) = socket
            .route("room-join")
            .data(RoomJoinRequest(
                    uid, roomId
            ))
            .retrieveMono(Void::class.java)

    fun callLeaveRoom(uid: UUID, roomId: UUID) = socket
            .route("room-leave")
            .data(RoomLeaveRequest(
                    uid, roomId
            ))
            .retrieveMono(Void::class.java)

    fun callGetRooms() = socket
            .route("room-list")
            .data(Void.TYPE)
            .retrieveFlux(RoomResponse::class.java)

}
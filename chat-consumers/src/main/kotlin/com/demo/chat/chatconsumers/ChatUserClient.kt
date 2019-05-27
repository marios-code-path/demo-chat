package com.demo.chat.chatconsumers

import com.demo.chat.domain.RoomMemberships
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.stereotype.Component
import java.util.*

@Component
class ChatUserClient(val socket: RSocketRequester) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    fun callCreateUser(name: String, handle: String) = socket
            .route("user-create")
            .data(UserCreateRequest(name, handle))
            .retrieveMono(UserResponse::class.java)


    fun callGetUser(handle: String) = socket
            .route("user-handle")
            .data(UserRequest(handle))
            .retrieveFlux(UserResponse::class.java)
            .doOnNext {
                logger.info("The user was :${it.user}")
            }

    fun callCreateRoom(roomName: String) = socket
            .route("room-create")
            .data(RoomCreateRequest(roomName))
            .retrieveFlux(RoomCreateResponse::class.java)
            .doOnNext {
                logger.info("The room ID is = ${it.romKey.roomId} named ${it.romKey.name}")
            }

    fun callJoinRoom(uid: UUID, roomId: UUID) = socket
            .route("room-join")
            .data(RoomJoinRequest(
                    uid, roomId
            ))
            .retrieveMono(Void::class.java)

    fun callGetRooms() = socket
            .route("room-list")
            .data(Void.TYPE)
            .retrieveFlux(RoomResponse::class.java)

}

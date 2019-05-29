package com.demo.chat.chatconsumers

import com.demo.chat.domain.Room
import com.demo.chat.domain.RoomKey
import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.fasterxml.jackson.annotation.JsonTypeInfo
import reactor.core.publisher.Flux
import java.time.Instant
import java.util.*

data class UserRequest(val userHandle: String)
data class UserRequestId(val userId: UUID)
data class UserRequestIdList(val userId: Flux<UUID>)
data class UserCreateRequest(val name: String, val userHandle: String)
data class UserCreateResponse(val user: ChatUser)
data class UserResponse(val user: ChatUser)


data class RoomCreateRequest(val roomName: String)
data class RoomCreateResponse(val roomKey: ChatRoomKey)
data class RoomRequest(val roomId: UUID)
data class RoomResponse(val room: ChatRoom)

data class RoomJoinRequest(val uid: UUID, val roomId: UUID)
data class RoomLeaveRequest(val uid: UUID, val roomId: UUID)

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
data class ChatUser(
        override val key: ChatUserKey,
        override val name: String,
        override val timestamp: Instant
) : User<UserKey>

data class ChatUserKey(
        override val userId: UUID,
        override val handle: String
) : UserKey

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
data class ChatRoom(
        override val key: ChatRoomKey,
        override val members: Set<UUID>,
        val active: Boolean,
        override val timestamp: Instant
) : Room<RoomKey>

data class ChatRoomKey(
        override val roomId: UUID,
        override val name: String
) : RoomKey
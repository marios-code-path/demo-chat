package com.demo.chat.chatconsumers

import com.demo.chat.domain.Room
import com.demo.chat.domain.RoomKey
import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import reactor.core.publisher.Flux
import java.time.Instant
import java.util.*

data class UserRequest(val userHandle: String)
data class UserRequestId(val userId: UUID)
data class UserRequestIdList(val userId: Flux<UUID>)
data class UserCreateRequest(val name: String, val userHandle: String)
data class UserCreateResponse(val userKey: UserKey)
data class UserResponse(val user: User<UserKey>)


data class RoomCreateRequest(val roomName: String)
data class RoomCreateResponse(val romKey: RoomKey)
data class RoomRequest(val roomId: UUID)
data class RoomResponse(val room: Room<RoomKey>)

data class RoomJoinRequest(val uid: UUID, val roomId: UUID)
data class RoomLeaveRequest(val uid: UUID, val roomId: UUID)

data class ChatUser(
        override val key: ChatUserKey,
        override val name: String,
        override val timestamp: Instant
) : User<UserKey>

data class ChatUserKey(
        override val userId: UUID,
        override val handle: String
) : UserKey
package com.demo.chat

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import java.util.*

data class UserRequest(val userHandle: String)
data class UserRequestId(val userId: UUID)
data class UserResponse(val user: User<UserKey>)

data class UserCreateRequest(val name: String, val userHandle: String)
data class UserCreateResponse(val user: User<UserKey>)


data class RoomCreateRequest(val roomName: String)
data class RoomCreateResponse(val roomKey: RoomKey)
data class RoomRequest(val roomId: UUID)
data class RoomResponse(val room: Room<RoomKey>)

data class RoomJoinRequest(val uid: UUID, val roomId: UUID)
data class RoomLeaveRequest(val uid: UUID, val roomId: UUID)

data class MessagesRequest(val topicId: UUID)
data class MessageRequest(val messageId: UUID)
data class MessagesResponse(val messages: Message<MessageKey, Any>)
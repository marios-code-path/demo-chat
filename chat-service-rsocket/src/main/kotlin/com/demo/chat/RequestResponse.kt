package com.demo.chat

import com.demo.chat.domain.Room
import com.demo.chat.domain.RoomKey
import com.demo.chat.domain.User
import java.util.*

data class UserRequest(val userHandle: String)
data class UserRequestId(val userId: UUID)
data class UserResponse(val user: User)

data class UserCreateRequest(val name: String, val userHandle: String, val imgUri: String)
data class UserCreateResponse(val user: User)


data class RoomCreateRequest(val roomName: String)
data class RoomCreateResponse(val roomKey: RoomKey)
data class RoomRequest(val roomId: UUID)
data class RoomResponse(val room: Room)

data class RoomJoinRequest(val uid: UUID, val roomId: UUID)
data class RoomLeaveRequest(val uid: UUID, val roomId: UUID)

data class MessagesRequest(val topicId: UUID)
data class MessageRequest(val messageId: UUID)
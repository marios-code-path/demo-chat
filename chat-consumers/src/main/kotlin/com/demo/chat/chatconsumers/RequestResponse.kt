package com.demo.chat.chatconsumers

import com.demo.chat.domain.*
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import reactor.core.publisher.Flux
import java.time.Instant
import java.util.*

data class UserRequest(val userHandle: String)
data class UserCreateRequest(val name: String, val userHandle: String)
data class UserCreateResponse(val user: ChatUser)
data class UserResponse(val user: ChatUser)


data class RoomCreateRequest(val roomName: String)

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


data class ChatMessageKey(
        override val msgId: UUID,
        override val userId: UUID,
        override val topicId: UUID,
        override val timestamp: Instant
) : TextMessageKey

data class AlertMessageKey(
        override val msgId: UUID,
        override val topicId: UUID,
        override val timestamp: Instant
) : TopicMessageKey

@JsonTypeName("ChatMessage")
data class ChatMessage(
        override val key: ChatMessageKey,
        override val value: String,
        override val visible: Boolean
) : TextMessage


@JsonTypeName("InfoAlert")
data class TestInfoAlert(
        override val key: AlertMessageKey,
        override val value: RoomMetaData,
        override val visible: Boolean
) : Message<AlertMessageKey, RoomMetaData>

@JsonTypeName("LeaveAlert")
data class TestLeaveAlert(
        override val key: AlertMessageKey,
        override val value: UUID,
        override val visible: Boolean
) : Message<AlertMessageKey, UUID>

@JsonTypeName("JoinAlert")
data class TestJoinAlert(
        override val key: AlertMessageKey,
        override val value: UUID,
        override val visible: Boolean
) : Message<AlertMessageKey, UUID>



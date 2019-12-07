package com.demo.chat.chatconsumers

import com.demo.chat.domain.*
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*

data class UserRequest(val userHandle: String)
data class UserCreateRequest(val name: String, val userHandle: String, val imgUri: String)
data class UserCreateResponse(val user: ChatUser)
data class UserResponse(val user: ChatUser)
data class UserRequestId(val userId: UUID)


data class RoomCreateRequest(val roomName: String)
data class RoomRequestId(val roomId: UUID)
data class RoomRequestName(val name: String)
data class RoomJoinRequest(val uid: UUID, val roomId: UUID)
data class RoomLeaveRequest(val uid: UUID, val roomId: UUID)

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
data class ChatUser(
        override val key: ChatUserKey,
        override val name: String,
        override val handle: String,
        override val imageUri: String,
        override val timestamp: Instant
) : User

data class ChatUserKey(
        override val id: UUID
) : UserKey

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
data class ChatEventTopic(
        override val key: ChatRoomKey,
        val active: Boolean,
        override val name: String
) : EventTopic

data class ChatRoomKey(
        override val id: UUID
) : TopicKey


data class ChatMessageKey(
        override val id: UUID,
        override val userId: UUID,
        override val topicId: UUID,
        override val timestamp: Instant
) : TextMessageKey

data class AlertMessageKey(
        override val id: UUID,
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
data class ChatInfoAlert(
        override val key: AlertMessageKey,
        override val value: TopicMetaData,
        override val visible: Boolean
) : Message<AlertMessageKey, TopicMetaData>

@JsonTypeName("LeaveAlert")
data class ChatLeaveAlert(
        override val key: AlertMessageKey,
        override val value: UUID,
        override val visible: Boolean
) : Message<AlertMessageKey, UUID>

@JsonTypeName("JoinAlert")
data class ChatJoinAlert(
        override val key: AlertMessageKey,
        override val value: UUID,
        override val visible: Boolean
) : Message<AlertMessageKey, UUID>

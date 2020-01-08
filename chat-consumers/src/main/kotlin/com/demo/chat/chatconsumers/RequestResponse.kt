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
data class ChatMessageTopic(
        override val key: ChatRoomKey,
        val active: Boolean,
        override val data: String
) : MessageTopic<UUID>

data class ChatRoomKey(
        override val id: UUID
) : Key<UUID>


data class ChatMessageKey(
        override val id: UUID,
        val from: UUID,
        override val dest: UUID,
        override val timestamp: Instant
) : UserMessageKey<UUID, UUID, UUID>

data class AlertMessageKey(
        override val id: UUID,
        override val dest: UUID,
        override val timestamp: Instant
) : MessageKey<UUID, UUID>

@JsonTypeName("ChatMessage")
data class ChatMessage(
        override val key: ChatMessageKey,
        override val data: String,
        override val record: Boolean
) : TextMessage<UUID>


@JsonTypeName("InfoAlert")
data class ChatInfoAlert(
        override val key: AlertMessageKey,
        override val data: TopicMetaData,
        override val record: Boolean
) : Message<UUID, TopicMetaData>

@JsonTypeName("LeaveAlert")
data class ChatLeaveAlert(
        override val key: AlertMessageKey,
        override val data: UUID,
        override val record: Boolean
) : Message<UUID, UUID>

@JsonTypeName("JoinAlert")
data class ChatJoinAlert(
        override val key: AlertMessageKey,
        override val data: UUID,
        override val record: Boolean
) : Message<UUID, UUID>

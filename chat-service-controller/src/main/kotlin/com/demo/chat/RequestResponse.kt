package com.demo.chat

import com.demo.chat.domain.Message
import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.TextMessageKey
import com.demo.chat.domain.TopicMessageKey
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*

data class UserRequest(val userHandle: String)
data class UserRequestId(val userId: UUID)

data class UserCreateRequest(val name: String, val userHandle: String, val imgUri: String)


data class RoomCreateRequest(val roomName: String)
data class RoomRequestId(val roomId: UUID)
data class RoomRequestName(val name: String)

data class RoomJoinRequest(val uid: UUID, val roomId: UUID)
data class RoomLeaveRequest(val uid: UUID, val roomId: UUID)

data class MessagesRequest(val topicId: UUID)
data class MessageRequest(val messageId: UUID)
data class MessageSendRequest(val msg: Message<out TopicMessageKey, Any>)
data class TextMessageSend(val uid: UUID, val topic: UUID, val text: String)

@JsonTypeName("ChatMessage")
data class ChatMessage(
        override val key: ChatMessageKey,
        override val value: String,
        override val visible: Boolean
) : TextMessage

data class ChatMessageKey(
        override val id: UUID,
        override val userId: UUID,
        override val topicId: UUID,
        override val timestamp: Instant
) : TextMessageKey
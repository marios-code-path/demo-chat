package com.demo.chat

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import java.time.Instant

data class UserRequest(val handle: String)
data class UserRequestId<T>(val id: T)
data class TopicRequestId<T>(val id: T)

data class UserCreateRequest(val name: String, val handle: String, val imgUri: String)


data class TopicCreateRequest(val roomName: String)

data class TopicRequestName(val name: String)

data class TopicJoinRequest<T>(val uid: T, val roomId: T)
data class TopicLeaveRequest<T>(val uid: T, val roomId: T)
data class MessageRequest<T>(val id: T)
data class MessageSendRequest<T, V>(val msg: V, val from: T, val dest: T)


data class ChatMessage<T, V>(
        override val key: ChatMessageKey<T>,
        override val data: V,
        override val record: Boolean
) : Message<T, V>

data class ChatMessageKey<T>(
        override val id: T,
        override val from: T,
        override val dest: T,
        override val timestamp: Instant
) : MessageKey<T>
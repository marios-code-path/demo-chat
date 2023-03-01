package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant

data class ByHandleRequest(val handle: String)
data class ByNameRequest(val name: String)
data class ByIdRequest<T>(val id: T)

@JsonTypeName("MembershipRequest")
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
data class MembershipRequest<T>(val uid: T, val roomId: T)

@JsonTypeName("MessageSendRequest")
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
data class MessageSendRequest<T, V>(val msg: V, val from: T, val dest: T)

@JsonTypeName("UserCreateRequest")
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
data class UserCreateRequest(val name: String, val handle: String, val imgUri: String)

@JsonTypeName("MemberTopicRequest")
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
data class MemberTopicRequest<T>(val member: T, val topic: T)

data class ChatMessage<T, V>(
        override val key: ChatMessageKey<T>,
        override val data: V,
        override val record: Boolean
) : Message<T, V>

data class ChatMessageKey<T>(
        override val id: T,
        override val from: T,
        override val dest: T,
        override val timestamp: Instant,
        override val empty: Boolean = false
) : MessageKey<T>
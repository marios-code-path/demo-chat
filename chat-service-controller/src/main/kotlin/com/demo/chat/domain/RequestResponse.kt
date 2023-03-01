package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant

@JsonTypeInfo(include = JsonTypeInfo.As.PROPERTY, use = JsonTypeInfo.Id.NAME, property="type")
sealed class RequestResponse<T>

@JsonTypeName("ByHandleRequest")
data class ByHandleRequest(val handle: String) : RequestResponse<Any>()

@JsonTypeName("ByNameRequest")
data class ByNameRequest(val name: String) : RequestResponse<Any>()

@JsonTypeName("ByIdRequest")
data class ByIdRequest<T>(val id: T) : RequestResponse<T>()

@JsonTypeName("MembershipRequest")
data class MembershipRequest<T>(val uid: T, val roomId: T) : RequestResponse<T>()

@JsonTypeName("MessageSendRequest")
data class MessageSendRequest<T, V>(val msg: V, val from: T, val dest: T) : RequestResponse<T>()

@JsonTypeName("UserCreateRequest")
data class UserCreateRequest(val name: String, val handle: String, val imgUri: String) : RequestResponse<Any>()

@JsonTypeName("MemberTopicRequest")
data class MemberTopicRequest<T>(val member: T, val topic: T) : RequestResponse<T>()

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
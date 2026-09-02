package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant

@JsonTypeInfo(include = JsonTypeInfo.As.PROPERTY, use = JsonTypeInfo.Id.NAME, property="type")
sealed class RequestResponse<T>

@JsonTypeName("ByNameRequest")
data class ByStringRequest(val name: String) : RequestResponse<Any>()

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

@JsonTypeName("TopicRecallRequest")
data class TopicRecallRequest<T>(
    val topicId: T,
    val query: String,
    val limit: Int = 10,
    val threshold: Double = 0.0,
) : RequestResponse<T>() {
    fun validate() = RecallRequestValidation.validate(query, limit, threshold)
}

@JsonTypeName("UserRecallRequest")
data class UserRecallRequest<T>(
    val userId: T,
    val query: String,
    val limit: Int = 10,
    val threshold: Double = 0.0,
) : RequestResponse<T>() {
    fun validate() = RecallRequestValidation.validate(query, limit, threshold)
}

@JsonTypeName("GlobalRecallRequest")
data class GlobalRecallRequest(
    val query: String,
    val limit: Int = 10,
    val threshold: Double = 0.0,
) : RequestResponse<Any>() {
    fun validate() = RecallRequestValidation.validate(query, limit, threshold)
}

object RecallRequestValidation {

    fun validate(query: String, limit: Int, threshold: Double) {
        if (query.isBlank()) {
            throw InvalidRecallRequestException("query must not be blank")
        }
        if (limit < 1) {
            throw InvalidRecallRequestException("limit must be at least 1")
        }
        if (limit > 50) {
            throw InvalidRecallRequestException("limit must be at most 50")
        }
        if (threshold < 0.0 || threshold > 1.0) {
            throw InvalidRecallRequestException("threshold must be in 0.0..1.0")
        }
    }
}
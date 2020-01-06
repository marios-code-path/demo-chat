package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonSubTypes(JsonSubTypes.Type(UserMessageKey::class))
interface MessageKey<T> : Key<T> {
    val dest: T
    val timestamp: Instant

    companion object Factory {
        @JvmStatic
        fun <T> create(messageId: T, dest: T): MessageKey<T> = object : MessageKey<T> {
            override val id: T
                get() = messageId
            override val dest: T
                get() = dest
            override val timestamp: Instant
                get() = Instant.now()
        }
    }
}

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonSubTypes(JsonSubTypes.Type(TextMessage::class))
@JsonTypeName("Message")
interface Message<T, E> {
    val record: Boolean
    val key: MessageKey<T>
    val data: E

    companion object Factory {
        fun <T, E> create(key: MessageKey<T>, value: E, visible: Boolean): Message<T, E> = object : Message<T, E> {
            override val key: MessageKey<T>
                get() = key
            override val data: E
                get() = value
            override val record: Boolean
                get() = visible
        }
    }
}

// TODO :  Relax requirements (TextMessage to Message) on the Persistence layer so we can identify any Message Key and Payload.
@Deprecated("Maybe doing away with TextMessage Subtype")
@JsonTypeName("TextMessage")
interface TextMessage<T> : Message<T, String> {
    override val key: UserMessageKey<T>

    companion object Factory {
        fun <T> create(messageId: T, topic: T, member: T, stringOfData: String): TextMessage<T> = object : TextMessage<T> {
            override val key: UserMessageKey<T>
                get() = UserMessageKey.create(messageId, topic, member)
            override val data: String
                get() = stringOfData
            override val record: Boolean
                get() = true
        }

        fun <T> create(key: UserMessageKey<T>, text: String, visible: Boolean): TextMessage<T> = object : TextMessage<T> {
            override val key: UserMessageKey<T>
                get() = key
            override val data: String
                get() = text
            override val record: Boolean
                get() = visible
        }
    }
}

@Deprecated("Liberate the userId as a specific field for any message")
interface UserMessageKey<T> : MessageKey<T> {
    val userId: T

    companion object Factory {
        fun <T> create(messageId: T, topic: T, member: T): UserMessageKey<T> = object : UserMessageKey<T> {
            override val id: T
                get() = messageId
            override val dest: T
                get() = topic
            override val userId: T
                get() = member
            override val timestamp: Instant
                get() = Instant.now()
        }

        fun <T> create(messageKey: Key<T>, topic: T, member: T): UserMessageKey<T> = object : UserMessageKey<T> {
            override val id: T
                get() = messageKey.id
            override val dest: T
                get() = topic
            override val userId: T
                get() = member
            override val timestamp: Instant
                get() = Instant.now()
        }
    }
}
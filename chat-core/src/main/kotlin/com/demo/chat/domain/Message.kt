package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
interface MessageKey<T> : Key<T> {
    val from: T
    val dest: T
    val timestamp: Instant
    override val empty: Boolean

    companion object Factory {
        /** This method builds the key of message [id], minted under [root]. */
        @JvmStatic
        fun <T> of(id: T, root: T, from: T, dest: T): MessageKey<T> = SimpleMessageKey(id, root, from, dest)
    }
}

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("message")
@JsonSubTypes(
    JsonSubTypes.Type(value = JoinAlert::class, name = "JoinAlert"),
    JsonSubTypes.Type(value = LeaveAlert::class, name = "LeaveAlert"),
)
interface Message<T,  out E> : KeyValuePair<T, E> {
    val record: Boolean
    override val key: MessageKey<T>
    override val data: E

    companion object Factory {
        fun <T, E> create(key: MessageKey<T>, value: E, record: Boolean): Message<T, E> = @JsonTypeName("message")
        object : Message<T, E> {
            override val key: MessageKey<T>
                get() = key
            override val data: E
                get() = value
            override val record: Boolean
                get() = record
        }
    }
}

// TODO: Extract pubsub into interface for wrapping classes
@JsonTypeName("JoinAlert")
data class JoinAlert<T, V>(override val key: MessageKey<T>, override val data: V,override val record: Boolean=false) : Message<T, V>

@JsonTypeName("LeaveAlert")
data class LeaveAlert<T, V>(override val key: MessageKey<T>, override val data: V, override val record: Boolean=false) : Message<T, V>

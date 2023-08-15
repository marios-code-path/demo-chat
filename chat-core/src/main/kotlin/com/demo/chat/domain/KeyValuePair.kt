package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

interface KeyBearer<T> {
    val key: Key<T>
}

interface NoKey<T>: Key<T>

object Empty

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("key")
@JsonSubTypes(JsonSubTypes.Type(MessageKey::class))
interface Key<T> {
    val id: T
    val empty: Boolean

    companion object Factory {
        @JvmStatic
        fun <T> funKey(id: T): Key<T> = @com.fasterxml.jackson.annotation.JsonTypeName("key") object : Key<T> {
            override val id: T
                get() = id
            override val empty: Boolean
                get() = false
            override fun toString(): String {
                return id.toString()
            }

            override fun equals(k2: Any?): Boolean =
                (k2 != null && k2::class == this::class) &&
                        (k2 is Key<*> && k2.id == this.id)
        }

        fun <T> emptyKey(id: T): Key<T> = @com.fasterxml.jackson.annotation.JsonTypeName("key") object : NoKey<T> {
            override val id: T
                get() = id
            override val empty: Boolean
                get() = true
            override fun toString(): String {
                return id.toString()
            }

            override fun equals(k2: Any?): Boolean =
                (k2 != null && k2::class == this::class) &&
                        (k2 is Key<*> && k2.id == this.id)
        }
    }

}

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("keyValue")
@JsonSubTypes(
    JsonSubTypes.Type(value = Message::class, name = "Message")
)
interface KeyValuePair<T, out E> : KeyBearer<T> {
    val data: E

    companion object Factory {
        @JvmStatic
        fun <T, E> create(key: Key<T>, data: E): KeyValuePair<T, E> =
            @com.fasterxml.jackson.annotation.JsonTypeName("keyValue") object : KeyValuePair<T, E> {
                override val key: Key<T>
                    get() = key
                override val data: E
                    get() = data
            }
    }
}
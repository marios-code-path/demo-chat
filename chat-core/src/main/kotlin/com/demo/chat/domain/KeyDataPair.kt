package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

interface KeyBearer<T> {
    val key: Key<T>
}

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("key")
@JsonSubTypes(JsonSubTypes.Type(MessageKey::class))
interface Key<T> {
    val id: T

    companion object Factory {
        @JvmStatic
        fun <T> funKey(id: T): Key<T> = @com.fasterxml.jackson.annotation.JsonTypeName("key") object : Key<T> {
            override val id: T
                get() = id
        }

        @JvmStatic
        fun <T : Any> anyKey(id: T): Key<T> = @com.fasterxml.jackson.annotation.JsonTypeName("key") object : Key<T> {
            override val id: T
                get() = id
        }
    }
}

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("keyData")
interface KeyDataPair<T, out E> : KeyBearer<T> {
    val data: E

    companion object Factory {
        @JvmStatic
        fun <T, E> create(key: Key<T>, data: E): KeyDataPair<T, E> = @com.fasterxml.jackson.annotation.JsonTypeName("keyData") object : KeyDataPair<T, E> {
            override val key: Key<T>
                get() = key
            override val data: E
                get() = data
        }
    }
}
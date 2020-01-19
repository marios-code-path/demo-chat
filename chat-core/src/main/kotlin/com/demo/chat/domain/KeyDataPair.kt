package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.util.*

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("Key")
@JsonSubTypes(JsonSubTypes.Type(MessageKey::class))
interface Key<T> {
    val id: T

    companion object Factory {
        @JvmStatic
        fun <T> funKey(id: T): Key<T> = object : Key<T> {
            override val id: T
                get() = id
        }
        @JvmStatic
        fun <T : Any> anyKey(id: T): Key<T> = object : Key<T> {
            override val id: T
                get() = id
        }
    }
}

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("KeyData")
interface KeyDataPair<T, E> {
    val key: Key<T>
    val data: E

    companion object Factory {
        @JvmStatic
        fun <T, E> create(key: Key<T>, data :E): KeyDataPair<T, E> = object : KeyDataPair<T, E> {
            override val key: Key<T>
                get() = key
            override val data: E
                get() = data
        }
    }
}
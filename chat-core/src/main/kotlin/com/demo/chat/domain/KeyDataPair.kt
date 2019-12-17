package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.util.*

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("Key")
interface Key<K> {
    val id: K

    companion object Factory {
        @JvmStatic
        fun eventKey(id: UUID): UUIDKey = object : UUIDKey {
            override val id: UUID
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
@JsonTypeName("UUIDKey")
interface UUIDKey : Key<UUID>{
    override val id: UUID
}

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("KeyData")
interface KeyDataPair<K, T> {
    val key: Key<K>
    val data: T

    companion object Factory {
        @JvmStatic
        fun <T> uuidKeyDataPair(key: Key<UUID>, data :T): KeyDataPair<UUID, T> = object : KeyDataPair<UUID, T> {
            override val key: Key<UUID>
                get() = key
            override val data: T
                get() = data
        }

        @JvmStatic
        fun <K, T> keyDataPair(key: Key<K>, data :T): KeyDataPair<K, T> = object : KeyDataPair<K, T> {
            override val key: Key<K>
                get() = key
            override val data: T
                get() = data
        }
    }
}
package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("User")
interface User<K> {
    val key: Key<K>
    val name: String
    val handle: String
    val imageUri: String
    val timestamp: Instant

    companion object Factory {
        @JvmStatic
        fun<K> create(key: Key<K>, name: String, handle: String, imageUri: String): User<K> = object : User<K> {
            override val key: Key<K>
                get() = key
            override val name: String
                get() = name
            override val handle: String
                get() = handle
            override val imageUri: String
                get() = imageUri
            override val timestamp: Instant
                get() = Instant.now()
        }
    }
}

interface UserKey : Key<UUID> {

    companion object Factory {
        @JvmStatic
        fun create(id: UUID): UserKey = object : UserKey {
            override val id: UUID
                get() = id
        }
    }
}
package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("User")
interface User {
    val key: UserKey
    val name: String
    val handle: String
    val imageUri: String
    val timestamp: Instant

    companion object Factory {
        @JvmStatic
        fun create(key: UserKey, name: String, handle: String, imageUri: String): User = object : User {
            override val key: UserKey
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

interface UserKey : UUIDKey {

    companion object Factory {
        @JvmStatic
        fun create(id: UUID): UserKey = object : UserKey {
            override val id: UUID
                get() = id
        }
    }
}
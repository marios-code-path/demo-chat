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
    val imageUri: String
    val timestamp: Instant
    companion object Factory {
        fun create(key: UserKey, name: String, imageUri: String): User = object : User {
            override val key: UserKey
                get() = key
            override val name: String
                get() = name
            override val imageUri: String
                get() = imageUri
            override val timestamp: Instant
                get() = Instant.now()
        }
    }
}

interface UserKey {
    val id: UUID
    val handle: String
    companion object Factory {
        fun create(id: UUID, handle: String) : UserKey = object : UserKey {
            override val id: UUID
                get() = id
            override val handle: String
                get() = handle
        }
    }
}


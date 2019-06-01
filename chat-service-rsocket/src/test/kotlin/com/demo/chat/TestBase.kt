package com.demo.chat

import com.demo.chat.domain.Room
import com.demo.chat.domain.RoomKey
import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.mockito.Mockito
import org.springframework.data.cassandra.core.cql.PrimaryKeyType
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn
import org.springframework.data.cassandra.core.mapping.Table
import java.time.Instant
import java.util.*

object TestBase

fun <T> anyObject(): T {
    Mockito.anyObject<T>()
    return uninitialized()
}

fun <T> uninitialized(): T = null as T

private val ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
fun randomAlphaNumeric(size: Int): String {
    var count = size
    val builder = StringBuilder()
    while (count-- != 0) {
        val character = (Math.random() * ALPHA_NUMERIC_STRING.length).toInt()
        builder.append(ALPHA_NUMERIC_STRING[character])
    }
    return builder.toString()
}


data class TestUserCreateResponse(val user: ChatUser)
data class TestUserRequest(val userHandle: String)
data class TestUserResponse(val user: ChatUser)

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
data class ChatUser(
        override val key: ChatUserKey,
        override val name: String,
        override val timestamp: Instant
) : User<UserKey>

data class ChatUserKey(
        override val userId: UUID,
        override val handle: String
) : UserKey

data class TestRoomCreateRequest(val roomName: String)
data class TestRoomCreateResponse(val roomKey: ChatRoomKey)
data class TestRoomRequest(val roomId: UUID)
data class TestRoomResponse(val room: ChatRoom)

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
data class ChatRoom(
        override val key: ChatRoomKey,
        override val members: Set<UUID>?,
        val active: Boolean,
        override val timestamp: Instant
) : Room<RoomKey>

data class ChatRoomKey(
        override val roomId: UUID,
        override val name: String
) : RoomKey

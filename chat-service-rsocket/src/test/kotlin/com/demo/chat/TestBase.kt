package com.demo.chat

import com.demo.chat.domain.*
import com.demo.chat.service.ChatMessageService
import com.demo.chat.service.ChatRoomServiceCassandra
import com.demo.chat.service.ChatUserServiceCassandra
import com.fasterxml.jackson.annotation.JsonTypeName
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.rsocket.server.RSocketServerBootstrap
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Configuration
import java.time.Instant
import java.util.*

fun <T> anyObject(): T {
    Mockito.anyObject<T>()
    return uninitialized()
}

fun <T> uninitialized(): T = null as T

class TestVoid()

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

@JsonTypeName("ChatUser")
data class TestChatUser(
        override val key: TestChatUserKey,
        override val name: String,
        override val timestamp: Instant
) : User<UserKey>

data class TestChatUserKey(
        override val userId: UUID,
        override val handle: String
) : UserKey

data class TestUserCreateResponse(val user: TestChatUser)
data class TestUserRequest(val userHandle: String)
data class TestUserResponse(val user: TestChatUser)

@JsonTypeName("ChatRoom")
data class TestChatRoom(
        override val key: ChatRoomKey,
        override val members: Set<UUID>?,
        val active: Boolean,
        override val timestamp: Instant
) : Room<RoomKey>

data class ChatRoomKey(
        override val roomId: UUID,
        override val name: String
) : RoomKey

data class TestRoomCreateRequest(val roomName: String)
data class TestRoomCreateResponse(val roomKey: ChatRoomKey)
data class TestRoomResponse(val room: TestChatRoom)

data class TestAlertMessageKey(
        override val id: UUID,
        override val roomId: UUID,
        override val timestamp: Instant
) : MessageKey

data class TestTextMessageKey(
        override val id: UUID,
        override val userId: UUID,
        override val roomId: UUID,
        override val timestamp: Instant
) : TextMessageKey

@JsonTypeName("ChatMessage")
data class TestTextMessage(
        override val key: TestTextMessageKey,
        override val value: String,
        override val visible: Boolean
) : TextMessage


@JsonTypeName("InfoAlert")
data class TestInfoAlert(
        override val key: TestAlertMessageKey,
        override val value: RoomMetaData,
        override val visible: Boolean
) : Message<TestAlertMessageKey, RoomMetaData>

@JsonTypeName("LeaveAlert")
data class TestLeaveAlert(
        override val key: TestAlertMessageKey,
        override val value: UUID,
        override val visible: Boolean
) : Message<TestAlertMessageKey, UUID>

@JsonTypeName("JoinAlert")
data class TestJoinAlert(
        override val key: TestAlertMessageKey,
        override val value: UUID,
        override val visible: Boolean
) : Message<TestAlertMessageKey, UUID>

data class TestMessagesResponse(val messages: Message<MessageKey, Any>)
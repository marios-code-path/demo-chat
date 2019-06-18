package com.demo.chat

import com.demo.chat.domain.*
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*

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

@JsonTypeName("ChatRoom")
data class TestChatRoom(
        override val key: TestChatRoomKey,
        override val members: Set<UUID>?,
        val active: Boolean,
        override val timestamp: Instant
) : Room<RoomKey>

data class TestChatRoomKey(
        override val roomId: UUID,
        override val name: String
) : RoomKey


data class TestTextMessageKey(
        override val id: UUID,
        override val userId: UUID,
        override val topicId: UUID,
        override val timestamp: Instant
) : TextMessageKey

data class TestAlertMessageKey(
        override val id: UUID,
        override val topicId: UUID,
        override val timestamp: Instant
) : MessageKey

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

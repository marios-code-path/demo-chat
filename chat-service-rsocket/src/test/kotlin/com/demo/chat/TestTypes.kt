package com.demo.chat

import com.demo.chat.domain.*
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*

@JsonTypeName("User")
data class TestChatUser(
        override val key: TestChatUserKey,
        override val name: String,
        override val imageUri: String,
        override val timestamp: Instant
) : User<UUID> {
    override val handle: String = key.handle
}

data class TestChatUserKey(
        override val id: UUID,
        val handle: String
) : Key<UUID>

data class TestUUIDKey(
        override val id: UUID
) : UUIDKey

@JsonTypeName("ChatRoom")
data class TestChatMessageTopic(
        override val key: TestChatRoomKey,
        val active: Boolean
) : MessageTopic<UUID> {
    override val data = key.name
}

data class TestChatRoomKey(
        override val id: UUID,
        val name: String
) : Key<UUID>

data class TestUserMessageKey(
        override val id: UUID,
        override val userId: UUID,
        override val dest: UUID,
        override val timestamp: Instant
) : UserMessageKey<UUID, UUID, UUID>

data class TestAlertMessageKey(
        override val id: UUID,
        override val dest: UUID,
        override val timestamp: Instant
) : MessageKey<UUID, UUID>

@JsonTypeName("ChatMessage")
data class TestTextMessage(
        override val key: TestUserMessageKey,
        override val data: String,
        override val visible: Boolean
) : TextMessage<UUID>

@JsonTypeName("InfoAlert")
data class TestInfoAlert(
        override val key: TestAlertMessageKey,
        override val data: TopicMetaData,
        override val visible: Boolean
) : Message<UUID, TopicMetaData>

@JsonTypeName("LeaveAlert")
data class TestLeaveAlert(
        override val key: TestAlertMessageKey,
        override val data: UUID,
        override val visible: Boolean
) : Message<UUID, UUID>

@JsonTypeName("JoinAlert")
data class TestJoinAlert(
        override val key: TestAlertMessageKey,
        override val data: UUID,
        override val visible: Boolean
) : Message<UUID, UUID>

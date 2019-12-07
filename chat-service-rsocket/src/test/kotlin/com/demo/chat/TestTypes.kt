package com.demo.chat

import com.demo.chat.domain.*
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*

@JsonTypeName("User")
data class TestChatUser (
        override val key: TestChatUserKey,
        override val name: String,
        override val imageUri: String,
        override val timestamp: Instant
) : User {
    override val handle: String = key.handle
}

data class TestChatUserKey(
        override val id: UUID,
        val handle: String
) : UserKey

data class TestUUIDKey(
        override val id: UUID
) : UUIDKey

@JsonTypeName("ChatRoom")
data class TestChatEventTopic(
        override val key: TestChatRoomKey,
        val active: Boolean
) : EventTopic {
    override val name = key.name
}

data class TestChatRoomKey(
        override val id: UUID,
        val name: String
) : TopicKey


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
) : TopicMessageKey

@JsonTypeName("ChatMessage")
data class TestTextMessage(
        override val key: TestTextMessageKey,
        override val value: String,
        override val visible: Boolean
) : TextMessage

@JsonTypeName("InfoAlert")
data class TestInfoAlert(
        override val key: TestAlertMessageKey,
        override val value: TopicMetaData,
        override val visible: Boolean
) : Message<TestAlertMessageKey, TopicMetaData>

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

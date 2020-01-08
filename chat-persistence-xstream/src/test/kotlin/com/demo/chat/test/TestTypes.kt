package com.demo.chat.test

import com.demo.chat.domain.*
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*

object TestTypes


data class Usr(val name: String, val handle: String, val date: Date)

@JsonTypeName("TestEntity")
data class TestEntity(var data: String) {
    constructor() : this("foo")//Usr("","", Date()))
}


@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("TopicData")
data class TestTopicData(val state: Message<UUID, Any>)

@JsonTypeName("ChatUser")
data class TestChatUser(
        override val key: TestChatUserKey,
        override val name: String,
        override val handle: String,
        override val imageUri: String,
        override val timestamp: Instant
) : User<UUID>

data class TestChatUserKey(
        override val id: UUID
) : Key<UUID>

@JsonTypeName("ChatRoom")
data class TestChatMessageTopic(
        override val key: TestChatRoomKey,
        override val data: String,
        val active: Boolean
) : MessageTopic<UUID>

data class TestChatRoomKey(
        override val id: UUID
) : Key<UUID>


data class TestUserMessageKey(
        override val id: UUID,
        val from: UUID,
        override val dest: UUID,
        override val timestamp: Instant
) : UserMessageKey<UUID, UUID, UUID>

data class TestAlertMessageKey(
        override val id: UUID,
        override val dest: UUID,
        override val timestamp: Instant
) : MessageKey<UUID, UUID>


@JsonTypeName("InfoAlert")
data class TestInfoAlert(
        override val key: TestAlertMessageKey,
        override val data: TopicMetaData,
        override val record: Boolean
) : Message<UUID, TopicMetaData>

@JsonTypeName("LeaveAlert")
data class TestLeaveAlert(
        override val key: TestAlertMessageKey,
        override val data: UUID,
        override val record: Boolean
) : Message<UUID, UUID>

@JsonTypeName("JoinAlert")
data class TestJoinAlert(
        override val key: TestAlertMessageKey,
        override val data: UUID,
        override val record: Boolean
) : Message<UUID, UUID>

@JsonTypeName("ChatMessage")
data class TestTextMessage(
        override val key: TestUserMessageKey,
        override val data: String,
        override val record: Boolean
) : TextMessage<UUID>

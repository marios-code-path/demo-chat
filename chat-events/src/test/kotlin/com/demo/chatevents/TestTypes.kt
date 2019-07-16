package com.demo.chatevents

import com.demo.chat.domain.*
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*

object TestTypes


data class Usr(val name: String, val handle: String, val date: Date)

@JsonTypeName("Zoom")
data class Zoom(var data: String) {
    constructor() : this("foo")//Usr("","", Date()))
}


@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("TopicData")
data class TestTopicData(val state: Message<TopicMessageKey, Any>)

@JsonTypeName("ChatUser")
data class TestChatUser(
        override val key: TestChatUserKey,
        override val name: String,
        override val imageUri: String,
        override val timestamp: Instant
) : User

data class TestChatUserKey(
        override val id: UUID,
        override val handle: String
) : UserKey

@JsonTypeName("ChatRoom")
data class TestChatRoom(
        override val key: TestChatRoomKey,
        override val members: Set<UUID>?,
        val active: Boolean,
        override val timestamp: Instant
) : Room

data class TestChatRoomKey(
        override val id: UUID,
        override val name: String
) : RoomKey


data class TestTextMessageKey(
        override val msgId: UUID,
        override val userId: UUID,
        override val topicId: UUID,
        override val timestamp: Instant
) : TextMessageKey

data class TestAlertMessageKey(
        override val msgId: UUID,
        override val topicId: UUID,
        override val timestamp: Instant
) : TopicMessageKey


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

@JsonTypeName("ChatMessage")
data class TestTextMessage(
        override val key: TestTextMessageKey,
        override val value: String,
        override val visible: Boolean
) : TextMessage
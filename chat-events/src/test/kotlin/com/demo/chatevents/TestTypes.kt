package com.demo.chatevents

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
data class TestChatUser<T>(
        override val key: TestChatUserKey<T>,
        override val name: String,
        override val handle: String,
        override val imageUri: String,
        override val timestamp: Instant
) : User<T>

data class TestChatUserKey<T>(
        override val id: T
) : Key<T>

@JsonTypeName("ChatRoom")
data class TestChatMessageTopic<T>(
        override val key: TestChatRoomKey<T>,
        override val data: String,
        val active: Boolean
) : MessageTopic<T>

data class TestChatRoomKey<T>(
        override val id: T
) : Key<T>


data class TestUserMessageKey<T>(
        override val id: T,
        override val from: T,
        override val dest: T,
        override val timestamp: Instant
) : UserMessageKey<T>

@JsonTypeName("ChatMessage")
data class TestTextMessage<T>(
        override val key: TestUserMessageKey<T>,
        override val data: String,
        override val record: Boolean
) : TextMessage<T>
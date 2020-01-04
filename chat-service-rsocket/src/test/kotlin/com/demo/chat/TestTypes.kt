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
) : Key<UUID>

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

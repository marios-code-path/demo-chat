package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import java.time.Instant
import java.util.*

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
) : Key<UUID> {
    override val empty: Boolean = false
}

data class TestUUIDKey(
    override val id: UUID
) : Key<UUID> {
    override val empty: Boolean = false
}

data class TestChatMessageTopic(
    override val key: TestChatRoomKey,
    val active: Boolean
) : MessageTopic<UUID> {
    override val data = key.name
}

data class TestChatRoomKey(
    override val id: UUID,
    val name: String
) : Key<UUID> {
    override val empty: Boolean = false
}
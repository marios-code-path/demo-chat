package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import java.time.Instant
import java.util.*

/**
 * A user of the RSocket tests. The key is a canonical key, because only
 * SimpleKey, EmptyKey and SimpleMessageKey implement Key. See CHAT-avduuqwp.
 */
data class TestChatUser(
    override val key: Key<UUID>,
    override val handle: String,
    override val name: String,
    override val imageUri: String,
    override val timestamp: Instant
) : User<UUID>

/** A room of the RSocket tests, with a canonical key. */
data class TestChatMessageTopic(
    override val key: Key<UUID>,
    override val data: String,
    val active: Boolean
) : MessageTopic<UUID>
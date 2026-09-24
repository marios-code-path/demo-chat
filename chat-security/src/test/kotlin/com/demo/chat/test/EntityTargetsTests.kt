package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.security.access.EntityTargets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** One case per entity that a many target read can answer. See `CHAT-wkwiipgy`. */
class EntityTargetsTests {

    private val key: Key<Long> = Key.funKey(7L)

    @Test
    fun `a key bearer names its key`() {
        val entities = listOf(
            User.create(key, "n", "h", "http://u"),
            MessageTopic.create(key, "room"),
            KeyValuePair.create(key, "v"),
            AuthMetadata.create(key, Key.funKey(1L), Key.funKey(2L), "GET", 0L)
        )

        entities.forEach { assertThat(EntityTargets.keyOf<Long>(it)).isEqualTo(key) }
    }

    @Test
    fun `a message names its message key`() {
        val message = Message.create(MessageKey.create(7L, 1L, 2L), "text", true)

        assertThat(EntityTargets.keyOf<Long>(message)).isEqualTo(key)
    }

    @Test
    fun `a membership names its raw id as a key`() {
        assertThat(EntityTargets.keyOf<Long>(TopicMembership.create(7L, 1L, 2L))).isEqualTo(key)
    }

    @Test
    fun `an unknown entity names no target`() {
        assertThat(EntityTargets.keyOf<Long>("not an entity")).isNull()
        assertThat(EntityTargets.keyOf<Long>(null)).isNull()
    }
}

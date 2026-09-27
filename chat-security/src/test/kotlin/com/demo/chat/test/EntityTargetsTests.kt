package com.demo.chat.test

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.test.key.FakeKeyServices

import com.demo.chat.test.key.TestKeys

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

    private val roots = FakeKeyServices.longRoots()
    private val key: Key<Long> = TestKeys.key(7L)

    @Test
    fun `a key bearer names its key`() {
        val entities = listOf(
            User.create(key, "n", "h", "http://u"),
            MessageTopic.create(key, "room"),
            KeyValuePair.create(key, "v"),
            AuthMetadata.create(key, TestKeys.key(1L), TestKeys.key(2L), "GET", 0L)
        )

        entities.forEach { assertThat(EntityTargets.keyOf(it, roots)).isEqualTo(key) }
    }

    @Test
    fun `a message names its message key`() {
        val message = Message.create(TestKeys.message(7L, 1L, 2L), "text", true)

        assertThat(EntityTargets.keyOf(message, roots)).isEqualTo(key)
    }

    @Test
    fun `a membership names its raw id as a key under the TOPIC_MEMBERSHIP root`() {
        assertThat(EntityTargets.keyOf(TopicMembership.create(7L, 1L, 2L), roots))
            .isEqualTo(Key.of(7L, roots.of(ChatDomain.TOPIC_MEMBERSHIP).id))
    }

    @Test
    fun `an unknown entity names no target`() {
        assertThat(EntityTargets.keyOf("not an entity", roots)).isNull()
        assertThat(EntityTargets.keyOf(null, roots)).isNull()
    }
}

package com.demo.chat.test.domain

import com.demo.chat.domain.ChatMessageKey
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/** Equality reads id, root and empty, in every implementation. See `CHAT-avduuqwp`. */
class KeyEqualityTests {

    @Test
    fun `equality reads id and root`() {
        assertThat(Key.of(1L, 9L)).isEqualTo(Key.of(1L, 9L))
        assertThat(Key.of(1L, 9L)).isNotEqualTo(Key.of(1L, 8L))
        assertThat(Key.of(1L, 9L).hashCode()).isEqualTo(Key.of(1L, 9L).hashCode())
    }

    @Test
    fun `equality is symmetric across every implementation`() {
        val plain: Key<Long> = Key.of(1L, 9L)
        val message: Key<Long> = MessageKey.of(1L, 9L, 2L, 3L)
        assertThat(plain == message).isEqualTo(message == plain)
        assertThat(plain).isEqualTo(message)
        assertThat(plain.hashCode()).isEqualTo(message.hashCode())
    }

    @Test
    fun `a root key is its own root`() {
        assertThat(Key.root(9L).root).isEqualTo(9L)
        assertThat(Key.root(9L)).isEqualTo(Key.of(9L, 9L))
    }

    @Test
    fun `an empty key equals only an empty key with the same id and root`() {
        assertThat(Key.empty(0L, 9L)).isEqualTo(Key.empty(0L, 9L))
        assertThat(Key.empty(0L, 9L)).isNotEqualTo(Key.of(0L, 9L))
        assertThat(Key.of(0L, 9L)).isNotEqualTo(Key.empty(0L, 9L))
        assertThat(Key.empty(0L, 9L)).isNotEqualTo(Key.empty(0L, 8L))
    }

    @Test
    fun `a chat message key is not a key`() {
        val request = ChatMessageKey(1L, 2L, 3L, Instant.EPOCH)
        assertThat(request as Any).isNotInstanceOf(Key::class.java)
        assertThat(request.toKey(9L)).isEqualTo(MessageKey.of(1L, 9L, 2L, 3L))
    }
}

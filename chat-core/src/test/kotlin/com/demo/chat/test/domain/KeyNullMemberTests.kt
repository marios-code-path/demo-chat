package com.demo.chat.test.domain

import com.demo.chat.domain.EmptyKey
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.SimpleKey
import com.demo.chat.domain.SimpleMessageKey
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** A key needs a non-null id and root, in every class and factory. See `CHAT-avduuqwp`. */
class KeyNullMemberTests {

    @Test
    fun `a direct construction with a null id or root is refused`() {
        assertThatThrownBy { SimpleKey<Long?>(null, 9L) }.hasMessageContaining("id")
        assertThatThrownBy { SimpleKey<Long?>(1L, null) }.hasMessageContaining("root")
        assertThatThrownBy { EmptyKey<Long?>(null, 9L) }.hasMessageContaining("id")
        assertThatThrownBy { EmptyKey<Long?>(0L, null) }.hasMessageContaining("root")
        assertThatThrownBy { SimpleMessageKey<Long?>(null, 9L, 2L, 3L) }.hasMessageContaining("id")
        assertThatThrownBy { SimpleMessageKey<Long?>(1L, null, 2L, 3L) }.hasMessageContaining("root")
    }

    @Test
    fun `a factory with a nullable generic argument is refused`() {
        assertThatThrownBy { Key.of<Long?>(null, 9L) }.hasMessageContaining("id")
        assertThatThrownBy { Key.of<Long?>(1L, null) }.hasMessageContaining("root")
        assertThatThrownBy { Key.root<Long?>(null) }.hasMessageContaining("id")
        assertThatThrownBy { Key.empty<Long?>(0L, null) }.hasMessageContaining("root")
        assertThatThrownBy { MessageKey.of<Long?>(1L, null, 2L, 3L) }.hasMessageContaining("root")
    }
}

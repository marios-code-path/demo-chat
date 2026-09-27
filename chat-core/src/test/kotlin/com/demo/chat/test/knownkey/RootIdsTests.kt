package com.demo.chat.test.knownkey

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.domain.knownkey.RootIds
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test
import java.util.UUID

/** The strict root id parser. The permissive Long parser returns zero. See `CHAT-avduuqwp`. */
class RootIdsTests {

    @Test
    fun `a canonical id parses`() {
        assertThat(RootIds.parse(TypeUtil.LongUtil, "1234", "User")).isEqualTo(1234L)
        val uuid = UUID.randomUUID()
        assertThat(RootIds.parse(UUIDUtil(), uuid.toString(), "User")).isEqualTo(uuid)
    }

    @ParameterizedTest
    @ValueSource(strings = ["not-a-number", "9223372036854775808", "12x", "+5", "05", " 5", "0", "", "  "])
    fun `a malformed, overflowing, non canonical or empty long id is refused`(text: String) {
        assertThatThrownBy { RootIds.parse(TypeUtil.LongUtil, text, "User") }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("User")
    }

    @ParameterizedTest
    @ValueSource(strings = ["not-a-uuid", "1-1-1-1-1", "00000000-0000-0000-0000-000000000000"])
    fun `a malformed, non canonical or empty uuid id is refused`(text: String) {
        assertThatThrownBy { RootIds.parse(UUIDUtil(), text, "Anon") }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("Anon")
    }

    @Test
    fun `an upper case uuid is refused, because it does not write back to the same text`() {
        val text = UUID.randomUUID().toString().uppercase()
        assertThatThrownBy { RootIds.parse(UUIDUtil(), text, "Admin") }.isInstanceOf(ChatException::class.java)
    }

    @Test
    fun `a null id is refused`() {
        assertThatThrownBy { RootIds.parse(TypeUtil.LongUtil, null, "Message") }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("missing")
    }
}

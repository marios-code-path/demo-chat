package com.demo.chat.test.serializers

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageKey
import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.domain.serializers.JacksonModules
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

/**
 * A key carries its root on the wire, and a decoder refuses a key without one.
 * Both Jackson generations hold the same contract. See `CHAT-avduuqwp`.
 *
 * The wire keeps `empty` beside `id` and `root`, so the tests read fields
 * rather than one exact string.
 */
class KeyWireTests {

    private val jackson2: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .findAndRegisterModules()
        .registerModule(JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter).keyModule())

    private val jackson3: JsonMapper = JsonMapper.builder()
        .addModule(ChatJackson3Modules().chatJackson3Module())
        .build()

    @Test
    fun `a key writes id and root, in both generations`() {
        listOf(jackson2.readTree(jackson2.writeValueAsString(Key.of(1L, 9L))).get("key"))
            .forEach { node ->
                assertThat(node.get("id").asLong()).isEqualTo(1L)
                assertThat(node.get("root").asLong()).isEqualTo(9L)
                assertThat(node.get("empty").asBoolean()).isFalse()
            }
        val node3 = jackson3.readTree(jackson3.writeValueAsString(Key.of(1L, 9L))).get("key")
        assertThat(node3.get("id").asLong()).isEqualTo(1L)
        assertThat(node3.get("root").asLong()).isEqualTo(9L)
    }

    @Test
    fun `a key and a message key round trip with their root`() {
        val key = Key.of(1L, 9L)
        val message = MessageKey.of(3L, 9L, 10L, 20L)
        assertThat(jackson2.readValue(jackson2.writeValueAsString(key), Key::class.java)).isEqualTo(key)
        assertThat(jackson2.readValue(jackson2.writeValueAsString(message), Key::class.java)).isEqualTo(message)
        assertThat(jackson3.readValue(jackson3.writeValueAsString(key), Key::class.java)).isEqualTo(key)
        assertThat(jackson3.readValue(jackson3.writeValueAsString(message), Key::class.java)).isEqualTo(message)
    }

    @Test
    fun `an empty key round trips as empty`() {
        val empty = Key.empty(0L, 9L)
        assertThat(jackson2.readValue(jackson2.writeValueAsString(empty), Key::class.java)).isEqualTo(empty)
        assertThat(jackson3.readValue(jackson3.writeValueAsString(empty), Key::class.java)).isEqualTo(empty)
    }

    @Test
    fun `a payload without root fails to decode, in both generations`() {
        assertThatThrownBy { jackson2.readValue("""{"key":{"id":1}}""", Key::class.java) }.hasMessageContaining("root")
        assertThatThrownBy { jackson3.readValue("""{"key":{"id":1}}""", Key::class.java) }.hasMessageContaining("root")
    }

    @Test
    fun `a payload with a null root fails to decode, in both generations`() {
        assertThatThrownBy { jackson2.readValue("""{"key":{"id":1,"root":null}}""", Key::class.java) }.hasMessageContaining("root")
        assertThatThrownBy { jackson3.readValue("""{"key":{"id":1,"root":null}}""", Key::class.java) }.hasMessageContaining("root")
    }

    @Test
    fun `a message key without root fails to decode, in both generations`() {
        val json = """{"key":{"id":3,"from":10,"dest":20}}"""
        assertThatThrownBy { jackson2.readValue(json, Key::class.java) }.hasMessageContaining("root")
        assertThatThrownBy { jackson3.readValue(json, Key::class.java) }.hasMessageContaining("root")
    }
}

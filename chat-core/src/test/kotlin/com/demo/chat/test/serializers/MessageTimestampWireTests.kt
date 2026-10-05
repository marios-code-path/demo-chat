package com.demo.chat.test.serializers

import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.serializers.JacksonModules
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

class MessageTimestampWireTests {
    private val timestamp = Instant.parse("2001-02-03T04:05:06.123456789Z")
    private val module2 = JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter)
    private val json2 = ObjectMapper().registerModule(KotlinModule.Builder().build()).findAndRegisterModules()
        .registerModule(module2.keyModule()).registerModule(module2.messageModule())
    private val cbor2 = ObjectMapper(CBORFactory()).registerModule(KotlinModule.Builder().build()).findAndRegisterModules()
        .registerModule(module2.keyModule()).registerModule(module2.messageModule())
    private val json3 = JsonMapper.builder().addModule(ChatJackson3Modules().chatJackson3Module()).build()

    private fun keyPayload(value: String) =
        """{"key":{"id":1,"root":9,"from":2,"dest":3,"timestamp":$value}}"""

    private fun messagePayload(value: String) =
        """{"message":{"key":${keyPayload(value)},"data":"stored","record":true}}"""

    @Test
    fun `Jackson 3 retains all nanoseconds in numeric timestamps`() {
        val value = "981173106.123456789"
        assertThat((json3.readValue(keyPayload(value), Key::class.java) as MessageKey<*>).timestamp).isEqualTo(timestamp)
        assertThat(json3.readValue(keyPayload(value), MessageKey::class.java).timestamp).isEqualTo(timestamp)
        assertThat(json3.readValue(messagePayload(value), Message::class.java).key.timestamp).isEqualTo(timestamp)
    }

    @Test
    fun `Jackson 2 reads integer milliseconds when its reader requests milliseconds`() {
        val expected = Instant.ofEpochMilli(981173106123L)
        listOf(json2, cbor2).forEach { mapper ->
            listOf(Key::class.java, MessageKey::class.java, Message::class.java).forEach { type ->
                val payload = if (type == Message::class.java) messagePayload("981173106123") else keyPayload("981173106123")
                val bytes = mapper.writeValueAsBytes(json2.readTree(payload))
                val result = mapper.readerFor(type)
                    .without(com.fasterxml.jackson.databind.DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                    .readValue<Any>(bytes)
                val actual = if (result is Message<*, *>) result.key.timestamp else (result as MessageKey<*>).timestamp
                assertThat(actual).isEqualTo(expected)
            }
        }
    }

    @Test
    fun `Jackson 3 reads integer milliseconds when its reader requests milliseconds`() {
        val expected = Instant.ofEpochMilli(981173106123L)
        listOf(Key::class.java, MessageKey::class.java, Message::class.java).forEach { type ->
            val payload = if (type == Message::class.java) messagePayload("981173106123") else keyPayload("981173106123")
            val result = json3.readerFor(type)
                .without(tools.jackson.databind.cfg.DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue<Any>(payload)
            val actual = if (result is Message<*, *>) result.key.timestamp else (result as MessageKey<*>).timestamp
            assertThat(actual).isEqualTo(expected)
        }
    }

    @Test
    fun `both generations read whole numbers as seconds by default`() {
        val expected = Instant.ofEpochSecond(981173106L)
        assertThat(json2.readValue(keyPayload("981173106"), MessageKey::class.java).timestamp).isEqualTo(expected)
        assertThat(json3.readValue(keyPayload("981173106"), MessageKey::class.java).timestamp).isEqualTo(expected)
    }

    @Test
    fun `decimal timestamps remain seconds when readers select milliseconds`() {
        mapOf(
            "981173106.123456789" to timestamp,
            "-0.123456789" to Instant.ofEpochSecond(-1, 876543211),
        ).forEach { (value, expected) ->
            val decoded2 = json2.readerFor(Message::class.java)
                .without(com.fasterxml.jackson.databind.DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue<Message<*, *>>(messagePayload(value))
            val decoded3 = json3.readerFor(Message::class.java)
                .without(tools.jackson.databind.cfg.DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .readValue<Message<*, *>>(messagePayload(value))
            assertThat(decoded2.key.timestamp).isEqualTo(expected)
            assertThat(decoded3.key.timestamp).isEqualTo(expected)
        }
    }

    @Test
    fun `both generations report malformed timestamps as mapping failures`() {
        listOf(Key::class.java, MessageKey::class.java, Message::class.java).forEach { type ->
            val payload = if (type == Message::class.java) messagePayload("\"invalid\"") else keyPayload("\"invalid\"")
            assertThatThrownBy { json2.readValue(payload, type) }
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException::class.java)
                .hasMessageContaining("Invalid message timestamp")
            assertThatThrownBy { json3.readValue(payload, type) }
                .isInstanceOf(tools.jackson.databind.DatabindException::class.java)
                .hasMessageContaining("Invalid message timestamp")
        }
    }

    @Test
    fun `all codecs preserve stored time for messages and both key decode types`() {
        val keys = listOf(
            SimpleMessageKey(1L, 9L, 2L, 3L, timestamp),
            SimpleMessageKey(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), timestamp),
        )
        keys.forEach { key ->
            val message = Message.create(key, "stored", true)
            listOf(json2, cbor2).forEach { mapper ->
                val keyBytes = mapper.writeValueAsBytes(key)
                assertThat((mapper.readValue(keyBytes, Key::class.java) as MessageKey<*>).timestamp).isEqualTo(timestamp)
                assertThat(mapper.readValue(keyBytes, MessageKey::class.java).timestamp).isEqualTo(timestamp)
                assertThat(mapper.readValue(mapper.writeValueAsBytes(message), Message::class.java).key.timestamp).isEqualTo(timestamp)
            }
            listOf(json3).forEach { mapper ->
                val keyBytes = mapper.writeValueAsBytes(key)
                assertThat((mapper.readValue(keyBytes, Key::class.java) as MessageKey<*>).timestamp).isEqualTo(timestamp)
                assertThat(mapper.readValue(keyBytes, MessageKey::class.java).timestamp).isEqualTo(timestamp)
                assertThat(mapper.readValue(mapper.writeValueAsBytes(message), Message::class.java).key.timestamp).isEqualTo(timestamp)
            }
        }
    }
}

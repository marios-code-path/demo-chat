package com.demo.chat.test.serializers

import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.domain.Message
import com.demo.chat.domain.PointerMessage
import com.demo.chat.test.key.TestKeys
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import tools.jackson.databind.json.JsonMapper

/**
 * The pointer wire contract, read by both Jackson generations.
 *
 * A pointer carries the stored flag. A decoder that drops it reads a removed
 * pointer as a live one, which is the defect `CHAT-erkoxybm` repaired at the
 * store. **The store tests cannot see this path**, because they never leave
 * the typed store. Both generations need their own reading, because each one
 * holds its own pointer deserializer.
 */
class PointerWireShapeTests {

    /**
     * The mapper is built the way `Jackson2MapperConfiguration` builds one, so
     * the reading covers the mapper a deployment serves. **A plain
     * `ObjectMapper` cannot serialize a `MessageKey`**, because its timestamp
     * needs the JavaTime module that this builder installs with the well known
     * set.
     */
    @Suppress("DEPRECATION")
    private val jackson2: ObjectMapper = Jackson2ObjectMapperBuilder.json()
        .modulesToInstall(*DefaultChatJacksonModules().allModules().toTypedArray())
        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build()

    private val jackson3: JsonMapper = JsonMapper.builder()
        .addModule(ChatJackson3Modules().chatJackson3Module())
        .build()

    /** The measured shape: `{"PointerMessage":{"key":{..},"data":"","record":..,"target":{..}}}`. */
    private fun pointer(record: Boolean): PointerMessage<Long> =
        PointerMessage(TestKeys.message(5001L, 5002L, 5003L), TestKeys.key(5004L), record)

    private fun decode2(json: String): Message<Long, String> {
        @Suppress("UNCHECKED_CAST")
        return jackson2.readValue(json, Message::class.java) as Message<Long, String>
    }

    private fun decode3(json: String): Message<Long, String> {
        @Suppress("UNCHECKED_CAST")
        return jackson3.readValue(json, Message::class.java) as Message<Long, String>
    }

    private fun assertPointer(message: Message<Long, String>, record: Boolean, generation: String) {
        assertThat(message).describedAs("the $generation pointer").isInstanceOf(PointerMessage::class.java)
        @Suppress("UNCHECKED_CAST")
        val read = message as PointerMessage<Long>
        assertThat(read.record).describedAs("the $generation record flag").isEqualTo(record)
        assertThat(read.target).describedAs("the $generation target").isEqualTo(TestKeys.key(5004L))
    }

    @Test
    fun `a Jackson 2 pointer carries the flag on the wire`() {
        val json = jackson2.writeValueAsString(pointer(false))
        val node = jackson2.readTree(json).get("PointerMessage")

        assertThat(node.has("record")).describedAs("the Jackson 2 pointer carries the flag").isTrue()
        assertThat(node.get("record").asBoolean()).describedAs("the Jackson 2 wire flag").isFalse()
    }

    @Test
    fun `a Jackson 2 removed pointer decodes as removed`() {
        assertPointer(decode2(jackson2.writeValueAsString(pointer(false))), false, "Jackson 2")
    }

    @Test
    fun `a Jackson 2 live pointer decodes as live`() {
        assertPointer(decode2(jackson2.writeValueAsString(pointer(true))), true, "Jackson 2")
    }

    @Test
    fun `a Jackson 3 pointer carries the flag on the wire`() {
        val json = jackson3.writeValueAsString(pointer(false))
        val node = jackson3.readTree(json).get("PointerMessage")

        assertThat(node.has("record")).describedAs("the Jackson 3 pointer carries the flag").isTrue()
        assertThat(node.get("record").asBoolean()).describedAs("the Jackson 3 wire flag").isFalse()
    }

    @Test
    fun `a Jackson 3 removed pointer decodes as removed`() {
        assertPointer(decode3(jackson3.writeValueAsString(pointer(false))), false, "Jackson 3")
    }

    @Test
    fun `a Jackson 3 live pointer decodes as live`() {
        assertPointer(decode3(jackson3.writeValueAsString(pointer(true))), true, "Jackson 3")
    }
}

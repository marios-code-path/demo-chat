package com.demo.chat.test.serializers

import com.demo.chat.test.key.TestKeys

import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.MessageTopic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/**
 * The domain wire contract, read by a Jackson 3 mapper.
 *
 * **Spring Boot 4 decodes HTTP with Jackson 3, and these tests are what prove
 * the domain types survive that.** `DomainWireShapeTests` and
 * `E2eeWireShapeTests` cover the Jackson 2 side and stay unchanged. Both
 * generations serve this repository, so both need their own reading.
 *
 * The mapper here is built the way `ServerJsonCodecConfiguration` builds one.
 * **`findAndAddModules` would not find the domain module**, because that call
 * reads the module service registry rather than a Spring context, so the
 * module is added by hand exactly as production adds it.
 *
 * Annotations are shared between the generations. jackson-databind 3.1.5
 * depends on `com.fasterxml.jackson.core:jackson-annotations`, and its own pom
 * says the annotations remain at the Jackson 2 group id. So `@JsonTypeInfo`
 * still wraps a `Key`, and these tests assert that wrapper rather than assume
 * it.
 *
 * See CHAT-qwmjrixq.
 */
class Jackson3WireShapeTests {

    private val mapper: JsonMapper = JsonMapper.builder()
        .addModule(ChatJackson3Modules().chatJackson3Module())
        .build()

    private fun round(value: Any): String = mapper.writeValueAsString(value)

    @Test
    fun `a MessageTopic decodes and keeps the nested key wrapper`() {
        // This is the shape that answered 500 on PUT /index/topic/add before
        // the Jackson 3 deserializers existed.
        val topic = MessageTopic.create(TestKeys.key(1001L), "a-topic")
        val json = round(topic)

        // The measured shape, not an assumed one:
        // {"keyValue":{"data":"a-topic","key":{"key":{"empty":false,"id":1001}}}}
        // MessageTopic extends KeyValuePair, which carries its own
        // @JsonTypeInfo, so the outer wrapper is keyValue and the key keeps a
        // second wrapper inside it.
        val node = mapper.readTree(json)
        assertThat(node.has("keyValue"))
            .describedAs("MessageTopic carries the keyValue wrapper")
            .isTrue()
        assertThat(node.get("keyValue").get("key").has("key"))
            .describedAs("the key keeps its own wrapper inside it")
            .isTrue()

        val decoded = mapper.readValue(json, MessageTopic::class.java)

        assertThat(decoded.key.id).isEqualTo(1001L)
        assertThat(decoded.data).isEqualTo("a-topic")
    }

    @Test
    fun `a Key decodes each id type and stays a Key`() {
        val uuid = UUID.randomUUID()

        val cases = listOf<Pair<Any, Any>>(
            1001L to 1001L,
            // A whole number reads as a Long, which is the shared rule.
            42 to 42L,
            2.5 to 2.5,
            uuid to uuid,
            "not-a-uuid" to "not-a-uuid",
        )

        cases.forEach { (id, expected) ->
            val decoded = mapper.readValue(round(TestKeys.key(id)), Key::class.java)

            assertThat(decoded.id)
                .describedAs("the id of a key written from %s", id)
                .isEqualTo(expected)

            assertThat(decoded)
                .describedAs("a key with no from and no dest stays a Key")
                .isNotInstanceOf(MessageKey::class.java)
        }
    }

    @Test
    fun `a MessageKey decodes as a MessageKey and keeps from and dest`() {
        // The rule that separates the two lives in KeyAssembly, and both
        // Jackson generations apply it. A key that carries from and dest is a
        // MessageKey, and one that does not is a Key.
        val json = round(TestKeys.message(7L, 10L, 20L))

        val decoded = mapper.readValue(json, Key::class.java)

        assertThat(decoded)
            .describedAs("from and dest make this a MessageKey")
            .isInstanceOf(MessageKey::class.java)

        val key = decoded as MessageKey<*>
        assertThat(key.id).isEqualTo(7L)
        assertThat(key.from).isEqualTo(10L)
        assertThat(key.dest).isEqualTo(20L)
    }

    @Test
    fun `a Message decodes its nested message key and its record flag`() {
        val message = Message.create(TestKeys.message(3L, 10L, 20L), "hello", true)
        val json = round(message)

        // The measured shape:
        // {"message":{"data":"hello","key":{"key":{...,"from":10,"dest":20}},"record":true}}
        val node = mapper.readTree(json)
        assertThat(node.has("message"))
            .describedAs("Message carries the message wrapper")
            .isTrue()
        assertThat(node.get("message").get("key").has("key"))
            .describedAs("the message key keeps its own wrapper")
            .isTrue()

        val decoded = mapper.readValue(json, Message::class.java)

        assertThat(decoded.key.id).isEqualTo(3L)
        assertThat(decoded.data).isEqualTo("hello")
        assertThat(decoded.record).isTrue()
    }

    @Test
    fun `an AuthMetadata decodes its keys, its permission and its expiry`() {
        val meta = AuthMetadata.create(
            TestKeys.key(1L),
            TestKeys.key(2L),
            TestKeys.key(3L),
            "WRITE",
            4L,
        )
        val json = round(meta)

        val decoded = mapper.readValue(json, AuthMetadata::class.java)

        assertThat(decoded.key.id).isEqualTo(1L)
        assertThat(decoded.principal.id).isEqualTo(2L)
        assertThat(decoded.target.id).isEqualTo(3L)
        assertThat(decoded.permission).isEqualTo("WRITE")
        assertThat(decoded.expires).isEqualTo(4L)
    }
}

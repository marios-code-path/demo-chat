package com.demo.chat.test.messaging

import com.demo.chat.config.JACKSON_2_OBJECT_MAPPER
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.pubsub.kafka.impl.KafkaTopicPubSubService
import com.demo.chat.test.TestStringKeyService
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.common.header.internals.RecordHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.kafka.support.mapping.AbstractJavaTypeMapper
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import reactor.core.publisher.Hooks
import java.nio.charset.StandardCharsets
import java.util.function.Supplier

@ExtendWith(SpringExtension::class)
@DirtiesContext
@EmbeddedKafka(
    partitions = 1,
    brokerPropertiesLocation = "classpath:kafka-test.properties",
)
@ContextConfiguration(classes = [KafkaTestConfiguration::class])
class KafkaPubSubTests @Autowired constructor(
    service: KafkaTopicPubSubService<String, String>,
    @Qualifier(JACKSON_2_OBJECT_MAPPER) private val objectMapper: ObjectMapper,
) : PubSubTests<String, String>(
    service,
    TestStringKeyService(),
    Supplier { "TEST" },
) {

    @BeforeEach
    fun setUp() {
        Hooks.onOperatorDebug()
    }

    @Test
    fun `producer omits the type header and keeps the message wrapper`() {
        val message = Message.create(
            MessageKey.create(3L, 10L, 20L),
            "hello",
            true,
        )
        val serializer = JsonSerializer<Message<Long, String>>(objectMapper).apply {
            setAddTypeInfo(false)
        }
        val headers = RecordHeaders()
        val body = serializer.serialize("topic", headers, message)

        assertThat(headers.lastHeader(AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME)).isNull()
        assertMessageBody(body)
    }

    @Test
    fun `consumer ignores an old anonymous type header`() {
        val message = Message.create(
            MessageKey.create(3L, 10L, 20L),
            "hello",
            true,
        )
        val serializer = JsonSerializer<Message<Long, String>>(objectMapper).apply {
            setAddTypeInfo(false)
        }
        val body = serializer.serialize("topic", RecordHeaders(), message)
        assertMessageBody(body)

        val oldHeaders = RecordHeaders().add(
            AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME,
            message.javaClass.name.toByteArray(StandardCharsets.UTF_8),
        )
        val deserializer = JsonDeserializer<Message<Long, String>>(
            Message::class.java,
            objectMapper,
        ).apply { ignoreTypeHeaders() }

        val decoded = requireNotNull(deserializer.deserialize("topic", oldHeaders, body))

        assertThat(decoded.key.id).isEqualTo(3L)
        assertThat(decoded.key.from).isEqualTo(10L)
        assertThat(decoded.key.dest).isEqualTo(20L)
        assertThat(decoded.data).isEqualTo("hello")
        assertThat(decoded.record).isTrue()
    }

    private fun assertMessageBody(body: ByteArray) {
        val root = objectMapper.readTree(body)
        assertThat(root.has("message")).isTrue()

        val value = root["message"]
        assertThat(value.has("data")).isTrue()
        assertThat(value["data"].asText()).isEqualTo("hello")
        assertThat(value["record"].asBoolean()).isTrue()
        assertThat(value["key"].has("key")).isTrue()

        val key = value["key"]["key"]
        assertThat(key["id"].asLong()).isEqualTo(3L)
        assertThat(key["from"].asLong()).isEqualTo(10L)
        assertThat(key["dest"].asLong()).isEqualTo(20L)
    }
}

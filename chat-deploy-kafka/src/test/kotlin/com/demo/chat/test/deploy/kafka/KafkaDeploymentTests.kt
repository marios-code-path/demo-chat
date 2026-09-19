package com.demo.chat.test.deploy.kafka

import com.demo.chat.ChatApp
import com.demo.chat.config.JACKSON_2_OBJECT_MAPPER
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.config.pubsub.kafka.KafkaPubSubBeans
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.pubsub.kafka.impl.KafkaTopicPubSubService
import com.demo.chat.service.core.TopicPubSubService
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.support.GenericApplicationContext
import org.springframework.kafka.support.mapping.AbstractJavaTypeMapper
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.stereotype.Controller
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import reactor.test.StepVerifier
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [ChatApp::class]
)
@DirtiesContext
@EmbeddedKafka(
    partitions = 1,
    brokerPropertiesLocation = "classpath:kafka-test.properties",
)
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-kafka",
        "app.server.proto=rsocket",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
        "app.service.core.key=memory",
        "app.service.core.pubsub=kafka",
        "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite", "app.service.composite.auth",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence",
        "app.controller.index", "app.controller.user", "app.controller.message",
        "app.controller.topic", "app.controller.pubsub",
        "app.service.security.userdetails"
    ]
)
class KafkaDeploymentTests {

    @Autowired
    private lateinit var context: GenericApplicationContext

    @Autowired
    @Qualifier(JACKSON_2_OBJECT_MAPPER)
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `kafka pubsub beans are the active implementation`() {
        assertThat(context.getBean(PubSubServiceBeans::class.java))
            .isInstanceOf(KafkaPubSubBeans::class.java)
    }

    @Test
    fun `memory pubsub beans are excluded`() {
        assertThat(context.containsBean("memoryPubSubBeans")).isFalse()
    }

    @Test
    fun `kafka deploy configuration supplied its beans`() {
        assertThat(context.containsBean("adminClient")).isTrue()
        assertThat(context.containsBean("kafkaProducerTemplate")).isTrue()
        assertThat(context.containsBean("kafkaReceiverOptions")).isTrue()
    }

    @Test
    fun `pubsub service resolves to the kafka implementation`() {
        val beans = context.getBean(PubSubServiceBeans::class.java)
        assertThat(beans.pubSubService()).isInstanceOf(KafkaTopicPubSubService::class.java)
    }

    @Test
    fun `request to query converters bean exists`() {
        assertThat(context.containsBean("requestToQueryConverters")).isTrue()
    }

    @Test
    fun `controllers are registered`() {
        assertThat(context.getBeanNamesForAnnotation(Controller::class.java).size)
            .isGreaterThan(1)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `production Kafka beans deliver a declared Message`() {
        val pubsub = context.getBean("pubSubService", TopicPubSubService::class.java)
            as TopicPubSubService<Long, String>
        val topic = System.nanoTime()
        val sender = topic + 1
        val message = Message.create(
            MessageKey.create(topic + 2, sender, topic),
            "production-payload",
            true,
        )

        try {
            StepVerifier.create(pubsub.listenTo(topic))
                .then { pubsub.open(topic).block(Duration.ofSeconds(10)) }
                .then { pubsub.sendMessage(message).block(Duration.ofSeconds(10)) }
                .assertNext { actual ->
                    assertThat(actual.key.id).isEqualTo(topic + 2)
                    assertThat(actual.key.from).isEqualTo(sender)
                    assertThat(actual.key.dest).isEqualTo(topic)
                    assertThat(actual.data).isEqualTo("production-payload")
                    assertThat(actual.record).isTrue()
                }
                .thenCancel()
                .verify(Duration.ofSeconds(15))
        } finally {
            pubsub.close(topic).block(Duration.ofSeconds(10))
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `production Kafka producer omits the anonymous type header`() {
        val pubsub = context.getBean("pubSubService", TopicPubSubService::class.java)
            as TopicPubSubService<Long, String>
        val topic = System.nanoTime()
        val topicName = topic.toString()
        val message = Message.create(
            MessageKey.create(topic + 2, topic + 1, topic),
            "production-header-payload",
            true,
        )
        val consumer = KafkaConsumer<String, ByteArray>(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to embeddedKafkaBrokers(),
                ConsumerConfig.GROUP_ID_CONFIG to "header-check-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ),
            StringDeserializer(),
            ByteArrayDeserializer(),
        )

        try {
            pubsub.open(topic).block(Duration.ofSeconds(10))
            consumer.subscribe(listOf(topicName))
            consumer.poll(Duration.ofSeconds(2))
            pubsub.sendMessage(message).block(Duration.ofSeconds(10))

            val records = consumer.poll(Duration.ofSeconds(10))
            assertThat(records).isNotEmpty
            assertThat(
                records.first().headers()
                    .lastHeader(AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME)
            ).isNull()
        } finally {
            consumer.close()
            pubsub.close(topic).block(Duration.ofSeconds(10))
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `production Kafka consumer ignores the old anonymous type header`() {
        val pubsub = context.getBean("pubSubService", TopicPubSubService::class.java)
            as TopicPubSubService<Long, String>
        val topic = System.nanoTime()
        val topicName = topic.toString()
        val sender = topic + 1
        val message = Message.create(
            MessageKey.create(topic + 2, sender, topic),
            "legacy-header-payload",
            true,
        )
        val headers = RecordHeaders()
        val body = JsonSerializer<Message<Long, String>>(objectMapper)
            .serialize(topicName, headers, message)
        assertThat(
            String(
                requireNotNull(headers.lastHeader(AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME)).value(),
                StandardCharsets.UTF_8,
            )
        ).isEqualTo(message.javaClass.name)
        val producer = KafkaProducer<String, ByteArray>(
            mapOf(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to embeddedKafkaBrokers()),
            StringSerializer(),
            ByteArraySerializer(),
        )

        try {
            StepVerifier.create(pubsub.listenTo(topic))
                .then {
                    pubsub.open(topic).block(Duration.ofSeconds(10))
                    producer.send(
                        ProducerRecord(topicName, null, null, topicName, body, headers)
                    ).get(10, java.util.concurrent.TimeUnit.SECONDS)
                }
                .assertNext { actual ->
                    assertThat(actual.key.id).isEqualTo(topic + 2)
                    assertThat(actual.key.from).isEqualTo(sender)
                    assertThat(actual.key.dest).isEqualTo(topic)
                    assertThat(actual.data).isEqualTo("legacy-header-payload")
                    assertThat(actual.record).isTrue()
                }
                .thenCancel()
                .verify(Duration.ofSeconds(15))
        } finally {
            producer.close()
            pubsub.close(topic).block(Duration.ofSeconds(10))
        }
    }

    private fun embeddedKafkaBrokers(): String = requireNotNull(
        context.environment.getProperty("spring.embedded.kafka.brokers")
    )
}

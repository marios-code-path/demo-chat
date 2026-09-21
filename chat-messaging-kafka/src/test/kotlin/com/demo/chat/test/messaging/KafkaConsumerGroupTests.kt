package com.demo.chat.test.messaging

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.config.JACKSON_2_OBJECT_MAPPER
import com.demo.chat.config.Jackson2MapperConfiguration
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.StringUtil
import com.demo.chat.domain.TypeUtil
import com.demo.chat.pubsub.kafka.impl.KafkaTopicAdmin
import com.demo.chat.pubsub.kafka.impl.KafkaTopicPubSubService
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.sender.KafkaSender
import reactor.kafka.sender.SenderOptions
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What one Kafka consumer group means for pub/sub fan-out, measured against a
 * real broker.
 *
 * **These tests measure a design property, not a defect in this module.** Each
 * one states what Kafka does. `KafkaTopicPubSubService` keeps one consumer per
 * topic per process and feeds a local multicast sink, so fan-off inside one
 * process never reaches Kafka. Fan-out **between** processes is decided
 * entirely by the consumer group id, and both the deployment and the test
 * configuration set one constant value.
 *
 * Two services here stand for two application instances. They share one
 * embedded broker, and nothing else.
 *
 * The pair under measurement is reactor-kafka 1.3.25 against kafka-clients
 * 4.1.2. reactor-kafka declares 3.9.1 and is discontinued at its 1.3 line, so
 * no release will ever target the 4.x major. A group with two members is the
 * path where a client mismatch would show, because it drives the join, the
 * assignment and the rebalance callbacks. See CHAT-hazcatpc.
 */
@SpringJUnitConfig
@DirtiesContext
@EmbeddedKafka(
    partitions = 1,
    brokerPropertiesLocation = "classpath:kafka-test.properties",
)
@ContextConfiguration(classes = [KafkaTestConfiguration::class])
class KafkaConsumerGroupTests @Autowired constructor(
    @Qualifier(JACKSON_2_OBJECT_MAPPER) private val objectMapper: ObjectMapper,
) {

    @Value("\${spring.embedded.kafka.brokers}")
    private lateinit var bootstrapServers: String

    private val typeUtil: TypeUtil<String> = StringUtil()

    private fun sender(): KafkaSender<String, Message<String, String>> =
        KafkaSender.create(
            SenderOptions.create<String, Message<String, String>>(
                mapOf(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                )
            ).withValueSerializer(
                JsonSerializer<Message<String, String>>(objectMapper).apply { setAddTypeInfo(false) }
            )
        )

    private fun receiverOptions(groupId: String): ReceiverOptions<String, Message<String, String>> =
        ReceiverOptions.create<String, Message<String, String>>(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            )
        ).withValueDeserializer(
            JsonDeserializer<Message<String, String>>(Message::class.java, objectMapper)
                .apply { ignoreTypeHeaders() }
        )

    /** One service stands for one application instance. */
    private fun instance(groupId: String): KafkaTopicPubSubService<String, String> {
        val admin = AdminClient.create(
            mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers)
        )
        return KafkaTopicPubSubService(
            sender(),
            KafkaTopicAdmin(admin, typeUtil),
            typeUtil,
            receiverOptions(groupId),
        )
    }

    /**
     * Collects what one instance delivers to a reader of [topic].
     *
     * The reader subscribes before the send, which is the rule the repaired
     * shared delivery test established. See CHAT-mumfjoau.
     */
    private fun readerOf(
        service: KafkaTopicPubSubService<String, String>,
        topic: String,
    ): CopyOnWriteArrayList<Message<String, String>> {
        val received = CopyOnWriteArrayList<Message<String, String>>()
        service.listenTo(topic).subscribe { received.add(it) }
        return received
    }

    private fun messageOn(topic: String) = Message.create(
        MessageKey.create(UUID.randomUUID().toString(), "a-user", topic),
        "a-payload",
        true,
    )

    /**
     * The reference case. Two instances in **different** groups both receive.
     *
     * This is what pub/sub fan-out requires, and it is the control for the
     * test below it. Without this case, a single-receiver result could mean a
     * broken client rather than a group rule.
     */
    @Test
    fun `two instances in different groups both receive the message`() {
        val topic = "topic-${UUID.randomUUID()}"
        val first = instance("group-${UUID.randomUUID()}")
        val second = instance("group-${UUID.randomUUID()}")

        first.open(topic).block(Duration.ofSeconds(20))
        second.open(topic).block(Duration.ofSeconds(20))

        val firstReceived = readerOf(first, topic)
        val secondReceived = readerOf(second, topic)

        // Both consumers must hold the partition before the send, or an
        // absent record would only mean the consumer joined late.
        Thread.sleep(Duration.ofSeconds(5).toMillis())

        first.sendMessage(messageOn(topic)).block(Duration.ofSeconds(20))
        Thread.sleep(Duration.ofSeconds(5).toMillis())

        assertThat(firstReceived)
            .`as`("the sending instance receives its own message")
            .hasSize(1)
        assertThat(secondReceived)
            .`as`("a second instance in its own group receives the same message")
            .hasSize(1)
    }

    /**
     * The measured case. Two instances in **one** group share the partition,
     * so exactly one of them receives.
     *
     * `KafkaDeployConfiguration.kafkaReceiverOptions` sets the constant group
     * id `chat-kafka`, and every topic carries one partition
     * (`KafkaTopicAdmin.newTopic`). A consumer group gives one partition to
     * one member. So a second instance of a kafka deployment reads nothing
     * for that topic, and its local subscribers are never fed.
     *
     * **This test asserts Kafka's rule, so it passes today and it must keep
     * passing.** It is the evidence behind the fan-out finding, and it fails
     * if someone changes the group id or the partition count without deciding
     * what fan-out should mean. CHAT-xblitvkl holds that decision. See also
     * CHAT-hazcatpc.
     */
    @Test
    fun `two instances in one group deliver the message to exactly one of them`() {
        val topic = "topic-${UUID.randomUUID()}"
        val sharedGroup = "group-${UUID.randomUUID()}"
        val first = instance(sharedGroup)
        val second = instance(sharedGroup)

        first.open(topic).block(Duration.ofSeconds(20))
        second.open(topic).block(Duration.ofSeconds(20))

        val firstReceived = readerOf(first, topic)
        val secondReceived = readerOf(second, topic)

        // The second join rebalances the group. Waiting past it is what makes
        // this a measurement of the settled assignment rather than of a race.
        Thread.sleep(Duration.ofSeconds(8).toMillis())

        first.sendMessage(messageOn(topic)).block(Duration.ofSeconds(20))
        Thread.sleep(Duration.ofSeconds(5).toMillis())

        assertThat(firstReceived.size + secondReceived.size)
            .`as`("one group holds one partition, so one member reads the record")
            .isEqualTo(1)
    }
}

package com.demo.chat.config.deploy.kafka

import com.demo.chat.config.JACKSON_2_OBJECT_MAPPER
import com.demo.chat.domain.Message
import com.demo.chat.domain.NodeId
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.sender.KafkaSender
import reactor.kafka.sender.SenderOptions

/**
 * Kafka deployment configuration — provides the beans that [KafkaPubSubBeans]
 * depends on when `app.service.core.pubsub=kafka` is set.
 *
 * Activated by the `kafka` Spring profile, which is set by the
 * `kafka-backend` Maven profile in `chat-deploy`.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"], havingValue = "kafka")
class KafkaDeployConfiguration(
    @Qualifier(JACKSON_2_OBJECT_MAPPER)
    private val objectMapper: ObjectMapper,
) {

    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private lateinit var bootstrapServers: String

    @Bean
    fun adminClient(): AdminClient = AdminClient.create(
        mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers)
    )

    @Bean
    fun kafkaProducerTemplate(): KafkaSender<String, Message<Any, Any>> {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        )
        val valueSerializer = JsonSerializer<Message<Any, Any>>(objectMapper).apply {
            setAddTypeInfo(false)
        }
        return KafkaSender.create(
            SenderOptions.create<String, Message<Any, Any>>(props)
                .withValueSerializer(valueSerializer)
        )
    }

    /**
     * The consumer group of this instance, which is `chat-kafka-<nodeId>`.
     *
     * **Every instance reads every record.** A consumer group gives one
     * partition to one member, and `KafkaTopicAdmin.newTopic` creates one
     * partition for each topic. So one shared group delivers each message to
     * exactly one instance, which is queue behaviour and not pub/sub. A chat
     * topic is pub/sub, and a second instance that read nothing would serve
     * clients that never receive a message. The owner decided this on
     * 2026-09-21. See CHAT-xblitvkl.
     *
     * **The group id follows `app.nodeid`, so it is stable across a restart.**
     * A restart therefore resumes from the committed offset. A generated id
     * would create a new group on every start, and `earliest` would then
     * replay the whole topic each time.
     *
     * **This inherits the `app.nodeid` uniqueness rule.** Two instances that
     * state one node id share one group, which restores the behaviour this
     * change removes. The store-side claim lease enforces uniqueness only when
     * a shared backend holds the keys or the persistence, so a kafka
     * deployment on memory stores depends on the operator. See
     * `docs/NODEID-CLAIM.md`.
     *
     * Read `app.nodeid` from [Environment] rather than with `@Value`, which is
     * the rule `NodeIdConfiguration` records. Spring resolves a placeholder
     * before it injects a field, so an unset property fails placeholder
     * resolution and [NodeId.parse] never reports the missing value.
     */
    fun consumerGroup(environment: Environment): String =
        CONSUMER_GROUP_PREFIX + NodeId.parse(environment.getProperty("app.nodeid")).value

    @Bean
    fun kafkaReceiverOptions(environment: Environment): ReceiverOptions<String, Message<Any, Any>> =
        ReceiverOptions.create<String, Message<Any, Any>>(mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to consumerGroup(environment),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ))
            .withValueDeserializer(
                JsonDeserializer<Message<Any, Any>>(Message::class.java, objectMapper)
                    .apply { ignoreTypeHeaders() }
            )

    companion object {
        /** Every instance consumer group starts with this. */
        const val CONSUMER_GROUP_PREFIX = "chat-kafka-"
    }
}

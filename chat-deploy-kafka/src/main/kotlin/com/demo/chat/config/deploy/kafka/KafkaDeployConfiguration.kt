package com.demo.chat.config.deploy.kafka

import com.demo.chat.domain.Message
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import reactor.kafka.receiver.ReceiverOptions
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
class KafkaDeployConfiguration {

    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private lateinit var bootstrapServers: String

    @Bean
    fun adminClient(): AdminClient = AdminClient.create(
        mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers)
    )

    @Bean
    fun kafkaProducerTemplate(): ReactiveKafkaProducerTemplate<String, Message<Any, Any>> {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
        )
        return ReactiveKafkaProducerTemplate(SenderOptions.create(props))
    }

    @Bean
    fun kafkaReceiverOptions(): ReceiverOptions<String, Message<Any, Any>> =
        ReceiverOptions.create(mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "chat-kafka",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
            JsonDeserializer.TRUSTED_PACKAGES to "com.demo.chat.domain",
        ))
}

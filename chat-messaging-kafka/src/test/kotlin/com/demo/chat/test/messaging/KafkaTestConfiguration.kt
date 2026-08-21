package com.demo.chat.test.messaging

import com.demo.chat.domain.Message
import com.demo.chat.domain.StringUtil
import com.demo.chat.domain.TypeUtil
import com.demo.chat.pubsub.kafka.impl.KafkaTopicAdmin
import com.demo.chat.pubsub.kafka.impl.KafkaTopicPubSubService
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.sender.SenderOptions

@TestConfiguration
class KafkaTestConfiguration {

    @Value("\${spring.embedded.kafka.brokers}")
    private lateinit var bootstrapServers: String

    @Bean
    fun typeUtil(): TypeUtil<String> = StringUtil()

    @Bean
    fun adminClient(): AdminClient = AdminClient.create(
        mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers)
    )

    @Bean
    fun kafkaTopicAdmin(adminClient: AdminClient, typeUtil: TypeUtil<String>): KafkaTopicAdmin<String> =
        KafkaTopicAdmin(adminClient, typeUtil)

    @Bean
    fun producerTemplate(): ReactiveKafkaProducerTemplate<String, Message<String, String>> {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
        )
        return ReactiveKafkaProducerTemplate(SenderOptions.create(props))
    }

    @Bean
    fun receiverOptions(): ReceiverOptions<String, Message<String, String>> =
        ReceiverOptions.create(mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "chat-kafka-test",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
            JsonDeserializer.TRUSTED_PACKAGES to "com.demo.chat.domain",
        ))

    @Bean
    fun kafkaPubSubService(
        producerTemplate: ReactiveKafkaProducerTemplate<String, Message<String, String>>,
        kafkaTopicAdmin: KafkaTopicAdmin<String>,
        typeUtil: TypeUtil<String>,
        receiverOptions: ReceiverOptions<String, Message<String, String>>,
    ): KafkaTopicPubSubService<String, String> =
        KafkaTopicPubSubService(producerTemplate, kafkaTopicAdmin, typeUtil, receiverOptions)
}

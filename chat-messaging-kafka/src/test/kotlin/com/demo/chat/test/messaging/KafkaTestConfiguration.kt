package com.demo.chat.test.messaging

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.config.JACKSON_2_OBJECT_MAPPER
import com.demo.chat.config.Jackson2MapperConfiguration
import com.demo.chat.domain.Message
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.sender.KafkaSender
import reactor.kafka.sender.SenderOptions

@TestConfiguration
@Import(Jackson2MapperConfiguration::class, DefaultChatJacksonModules::class)
class KafkaTestConfiguration(
    @Qualifier(JACKSON_2_OBJECT_MAPPER)
    private val objectMapper: ObjectMapper,
) {

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
    fun producerTemplate(): KafkaSender<String, Message<String, String>> {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        )
        val valueSerializer = JsonSerializer<Message<String, String>>(objectMapper).apply {
            setAddTypeInfo(false)
        }
        return KafkaSender.create(
            SenderOptions.create<String, Message<String, String>>(props)
                .withValueSerializer(valueSerializer)
        )
    }

    @Bean
    fun receiverOptions(): ReceiverOptions<String, Message<String, String>> =
        ReceiverOptions.create<String, Message<String, String>>(mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "chat-kafka-test",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ))
            .withValueDeserializer(
                JsonDeserializer<Message<String, String>>(Message::class.java, objectMapper)
                    .apply { ignoreTypeHeaders() }
            )

    @Bean
    fun kafkaPubSubService(
        producerTemplate: KafkaSender<String, Message<String, String>>,
        kafkaTopicAdmin: KafkaTopicAdmin<String>,
        typeUtil: TypeUtil<String>,
        receiverOptions: ReceiverOptions<String, Message<String, String>>,
    ): KafkaTopicPubSubService<String, String> =
        KafkaTopicPubSubService(producerTemplate, kafkaTopicAdmin, typeUtil, receiverOptions)
}

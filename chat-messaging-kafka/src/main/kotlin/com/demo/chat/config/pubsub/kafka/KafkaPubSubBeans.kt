package com.demo.chat.config.pubsub.kafka

import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.domain.Message
import com.demo.chat.domain.TypeUtil
import com.demo.chat.pubsub.kafka.impl.KafkaTopicAdmin
import com.demo.chat.pubsub.kafka.impl.KafkaTopicPubSubService
import com.demo.chat.service.core.TopicPubSubService
import org.apache.kafka.clients.admin.AdminClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.kafka.receiver.ReceiverOptions

// Configuration plus Bean, not Component. The service keeps membership state
// per instance, so every holder must get the same one. fp issue B9,
// CHAT-ouzjdxun.
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"], havingValue = "kafka")
class KafkaPubSubBeans<T, V>(
    private val producer: ReactiveKafkaProducerTemplate<String, Message<T, V>>,
    private val adminClient: AdminClient,
    private val typeUtil: TypeUtil<T>,
    private val receiverOptions: ReceiverOptions<String, Message<T, V>>
) : PubSubServiceBeans<T, V> {

    @Bean
    override fun pubSubService(): TopicPubSubService<T, V> =
        KafkaTopicPubSubService(
            producer,
            KafkaTopicAdmin(adminClient, typeUtil),
            typeUtil,
            receiverOptions
        )
}

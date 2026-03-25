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
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"], havingValue = "kafka")
class KafkaPubSubBeans<T, V>(
    private val producer: ReactiveKafkaProducerTemplate<String, Message<T, V>>,
    private val adminClient: AdminClient,
    private val typeUtil: TypeUtil<T>,
) : PubSubServiceBeans<T, V> {

    override fun pubSubService(): TopicPubSubService<T, V> =
        KafkaTopicPubSubService(
            producer,
            KafkaTopicAdmin(adminClient, typeUtil),
            typeUtil,
        )
}

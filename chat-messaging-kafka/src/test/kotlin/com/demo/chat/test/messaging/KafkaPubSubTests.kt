package com.demo.chat.test.messaging

import com.demo.chat.pubsub.kafka.impl.KafkaTopicPubSubService
import com.demo.chat.test.TestStringKeyService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Hooks
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
) : PubSubTests<String, String>(
    service,
    TestStringKeyService(),
    Supplier { "TEST" },
) {

    @BeforeEach
    fun setUp() {
        Hooks.onOperatorDebug()
    }
}

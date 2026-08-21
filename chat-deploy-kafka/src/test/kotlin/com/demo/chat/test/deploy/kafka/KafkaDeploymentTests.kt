package com.demo.chat.test.deploy.kafka

import com.demo.chat.ChatApp
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.config.pubsub.kafka.KafkaPubSubBeans
import com.demo.chat.pubsub.kafka.impl.KafkaTopicPubSubService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.support.GenericApplicationContext
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.stereotype.Controller
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource

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
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long",
        "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
        "app.service.core.key",
        "app.service.core.pubsub=kafka",
        "app.service.core.index=lucene", "app.service.core.persistence",
        "app.service.core.secrets", "app.service.composite", "app.service.composite.auth",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence",
        "app.controller.index", "app.controller.user", "app.controller.message",
        "app.controller.topic", "app.controller.pubsub",
        "app.service.security.userdetails"
    ]
)
class KafkaDeploymentTests {

    @Autowired
    private lateinit var context: GenericApplicationContext

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
}

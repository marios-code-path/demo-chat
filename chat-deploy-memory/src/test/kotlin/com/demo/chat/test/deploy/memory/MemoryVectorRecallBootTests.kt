package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.deploy.actuator.VectorIndexEndpoint
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.support.GenericApplicationContext
import org.springframework.test.context.TestPropertySource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [ChatApp::class]
)
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-vector", "app.server.proto=rsocket",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory",
        "app.service.core.pubsub=memory", "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite", "app.service.composite.auth",
        "app.service.core.vector=simple", "app.service.core.embedding=mock",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
        "app.controller.recall",
        "app.service.security.userdetails"
    ]
)
class MemoryVectorRecallBootTests {

    @Autowired
    private lateinit var context: GenericApplicationContext

    @Test
    fun recallServiceIsActive() {
        Assertions
            .assertThat(context.containsBean("recallService"))
            .isTrue
    }

    @Test
    fun messageVectorIndexerIsActive() {
        Assertions
            .assertThat(context.containsBean("messageVectorIndexer"))
            .isTrue
    }

    // The three gate tests in chat-deploy build their context by hand. This one
    // proves the bean appears in a deployment that a person can start. The
    // gate reads app.service.composite, which a deployment sets with no value.
    @Test
    fun vectorIndexEndpointIsActive() {
        Assertions
            .assertThat(context.getBeansOfType(VectorIndexEndpoint::class.java))
            .hasSize(1)
    }

    @Test
    fun contextLoads() {
    }
}

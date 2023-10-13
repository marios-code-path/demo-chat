package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.support.GenericApplicationContext
import org.springframework.stereotype.Controller
import org.springframework.test.context.TestPropertySource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [ChatApp::class]
)
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment", "app.server.proto=rsocket",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long",
        "app.service.core.key",
        "app.service.core.pubsub", "app.service.core.index", "app.service.core.persistence",
        "app.service.core.secrets", "app.service.composite", "app.service.composite.auth",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
        "app.service.security.userdetails"

    ]
)
class MemoryDeploymentTests {

    @Autowired
    private lateinit var context: GenericApplicationContext

    @Test
    fun memoryPubSubExists() {
        Assertions.assertThat(context.containsBean("memoryPubSubBeans"))
            .isTrue
    }

    @Test
    fun shouldControllersExist() {
        val controllers = context.getBeanNamesForAnnotation(Controller::class.java).size > 1

        Assertions
            .assertThat(controllers)
            .isTrue
    }

    @Test
    fun contextLoads() {
    }
}
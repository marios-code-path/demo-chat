package com.demo.chat.deploy.test

import com.demo.chat.ChatApp
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.reactive.context.ReactiveWebApplicationContext
import org.springframework.stereotype.Controller
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [ChatApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "server.port=0", "spring.rsocket.server.port=0",
        "app.service.core.key",
        "app.service.core.pubsub", "app.service.core.index", "app.service.core.persistence",
        "app.service.core.secrets", "app.service.composite", "app.service.composite.auth",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message","app.controller.topic", "app.controller.pubsub",
        "spring.config.location=classpath:/application.yml",
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml"
    ]
)
@Disabled
class MemoryAppDeploymentTests {

    @Autowired
    private lateinit var context: ReactiveWebApplicationContext

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

}
package com.demo.chat.deploy.test

import com.demo.chat.deploy.memory.App
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.reactive.context.ReactiveWebApplicationContext
import org.springframework.stereotype.Controller
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [App::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "app.primary=core", "server.port=0", "management.endpoints.enabled-by-default=false",
        "spring.shell.interactive.enabled=false", "app.service.core.key", "app.key.type=long",
        "app.service.core.pubsub", "app.service.core.index", "app.service.core.persistence",
        "app.service.core.secrets",

        "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",

        "spring.cloud.config.enabled=false", "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.host=127.0.0.1", "spring.rsocket.server.port=6790"
    ]
)
@ActiveProfiles("exec-chat")
class MemoryAppDeploymentTests {

    @Autowired
    private lateinit var context: ReactiveWebApplicationContext


    @Test
    fun memoryPubSubExists() {
        Assertions.assertThat(context.containsBean("memoryPubSub"))
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
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

@SpringBootTest(classes = [App::class])
@TestPropertySource(
    properties = [
        "app.proto=rsocket",
        "app.primary=core", "server.port=0", "management.endpoints.enabled-by-default=false",
        "spring.shell.interactive.enabled=false", "app.service.core.key=long",
        "app.service.core.pubsub", "app.service.core.index", "app.service.core.persistence",
        "app.service.core.secrets",
        "app.service.composite.user", "app.service.composite.topic", "app.service.composite.message",
    ]
)
@ActiveProfiles("exec")
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
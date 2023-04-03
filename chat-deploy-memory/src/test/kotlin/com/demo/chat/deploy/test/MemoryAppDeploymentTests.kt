package com.demo.chat.deploy.test

import com.demo.chat.deploy.memory.MemoryDeploymentApp
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.reactive.context.ReactiveWebApplicationContext
import org.springframework.stereotype.Controller
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [MemoryDeploymentApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "server.port=0", "spring.rsocket.server.port=0",
        "app.service.core.key",
        "app.service.core.pubsub", "app.service.core.index", "app.service.core.persistence",
        "app.service.core.secrets", "app.service.composite.auth", "app.service.composite.user",
        "app.service.composite.message","app.service.composite.topic", "app.service.composite",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user","spring.config.location=classpath:/application.yml"
    ]
)
@ActiveProfiles("memory")
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
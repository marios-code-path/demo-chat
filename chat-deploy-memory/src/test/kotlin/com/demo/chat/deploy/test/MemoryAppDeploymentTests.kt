package com.demo.chat.deploy.test

import com.demo.chat.deploy.memory.App
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [App::class])
@TestPropertySource(
    properties = [
        "app.proto=rsocket", "spring.cloud.consul.config.enabled=false",
        "app.primary=core", "server.port=0", "management.endpoints.enabled-by-default=false",
        "spring.shell.interactive.enabled=false", "app.service.core.key=long",
        "app.service.core.pubsub", "app.service.core.index", "app.service.core.persistence",
        "app.service.core.secrets",
        "app.service.edge.user", "app.service.edge.topic", "app.service.edge.message",
    ]
)
class MemoryAppDeploymentTests {

    @Test
    fun contextLoads() {
    }
}
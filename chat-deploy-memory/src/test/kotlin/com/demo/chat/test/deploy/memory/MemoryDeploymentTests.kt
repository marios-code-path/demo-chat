package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [ChatApp::class]
)
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long",
        "app.service.core.key",
        "app.service.core.pubsub", "app.service.core.index", "app.service.core.persistence",
        "app.service.core.secrets", "app.service.composite", "app.service.composite.auth",
        "app.service.security.userdetails"

    ]
)
class MemoryDeploymentTests {
    @Test
    fun contextLoads() {
    }
}
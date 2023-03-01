package com.demo.chat.test.init

import com.demo.chat.init.BaseApp
import com.demo.chat.test.rsocket.TestConfigurationRSocketServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [BaseApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = [
        "app.key.type=long", "app.client.protocol=rsocket", "app.primary=init",
        "app.rsocket.transport.unprotected", "app.client.rsocket.core.key",
        "app.client.rsocket.core.persistence", "app.client.rsocket.core.index", "app.client.rsocket.core.pubsub",
        "app.client.rsocket.core.secrets", "app.client.rsocket.composite.user", "app.client.rsocket.composite.message",
        "app.client.rsocket.composite.topic", "app.service.composite.auth",
        "spring.cloud.service-registry.auto-registration.enabled=false", "app.client.rsocket.discovery.default",
        "spring.cloud.consul.config.enabled=false", "spring.rsocket.server.port=0", "server.port=0",
        "spring.shell.interactive.enabled=false", "management.endpoints.enabled-by-default=false",
    ]
)
@ActiveProfiles("shell")
class ChatShellTests {

    @Test
    fun contextLoads() {
    }
}
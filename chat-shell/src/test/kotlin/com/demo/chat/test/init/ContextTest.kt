package com.demo.chat.test.init

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.shell.commands.LoginCommands
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [ChatApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = [
        // TODO: Consul still being contacted.... will need to unregister
        "app.key.type=long", "app.client.protocol=rsocket", "app.primary=test",
        "app.client.discovery=local",
        "app.rsocket.transport.security.type=unprotected", "app.client.rsocket.core.key",
        "app.client.rsocket.core.persistence", "app.client.rsocket.core.index", "app.client.rsocket.core.pubsub",
        "app.client.rsocket.core.secrets", "app.client.rsocket.composite.user", "app.client.rsocket.composite.message",
        "app.client.rsocket.composite.topic", "app.service.composite.auth",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "app.service.security.userdetails", "app.client.discovery.local.host=localhost", "app.client.discovery.local.port=6790",
        "spring.cloud.consul.config.enabled=false", "spring.rsocket.server.port=0", "server.port=0",
        "spring.shell.interactive.enabled=false", "management.endpoints.enabled-by-default=false",
    ]
)
@ActiveProfiles("shell")
class ContextTest<T> {


    @Autowired
    private lateinit var compositeServices: CompositeServiceBeans<T, String>

    @Autowired
    private lateinit var loginCommands: LoginCommands<T>

    @Test
    fun contextLoads() {

        Assertions.assertThat(compositeServices.topicService())
            .isNotNull

    }

}
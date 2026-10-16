package com.demo.chat.test.init

import com.demo.chat.ChatApp
import com.demo.chat.config.shell.deploy.ShellStateConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource

/**
 * The shell against the UUID test image. An import derives its message id as
 * a name-based UUID, so only a UUID deployment can hold it. See
 * `CHAT-bwzjstoh`.
 *
 * This base does not extend [ShellIntegrationTestBase]. Each base starts its
 * own container in its companion object, so a UUID test class starts no long
 * container.
 */
@SpringBootTest(classes = [ChatApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = [
        "app.key.type=uuid", "app.client.protocol=rsocket", "app.primary=test",
        "app.rootkeys.consume.scheme=http", "app.client.discovery=local",
        "app.rsocket.transport.security.type=unprotected", "app.client.rsocket.core.key",
        "app.client.rsocket.core.persistence", "app.client.rsocket.core.index", "app.client.rsocket.core.pubsub",
        "app.client.rsocket.core.secrets", "app.client.rsocket.composite.user", "app.client.rsocket.composite.message",
        "app.client.rsocket.composite.topic", "app.service.composite.auth=true",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "app.service.security.userdetails",
        "spring.cloud.consul.config.enabled=false", "spring.rsocket.server.port=0", "server.port=0",
        "spring.shell.interactive.enabled=false", "management.endpoints.enabled-by-default=false",
    ]
)
@ActiveProfiles("shell")
@Tag("integration")
open class UuidShellIntegrationTestBase {

    /** Every test starts with no credential and no identity. See [ShellIntegrationTestBase.clearShellLogin]. */
    @BeforeEach
    fun clearShellLogin() {
        ShellStateConfiguration.clearLogin()
    }

    companion object {
        val imageName = "chat-deploy-uuid-memory-integration-test:0.0.1"

        val container = startShellContainer(imageName)

        @JvmStatic
        @DynamicPropertySource
        fun clientPropertySetup(registry: org.springframework.test.context.DynamicPropertyRegistry) =
            registerShellClient(registry, container)
    }
}

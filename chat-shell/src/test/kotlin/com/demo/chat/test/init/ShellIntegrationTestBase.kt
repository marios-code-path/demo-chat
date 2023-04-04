package com.demo.chat.test.init

import com.demo.chat.shell.BaseApp
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import java.time.Duration

@SpringBootTest(classes = [BaseApp::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = [
        // TODO: Consul still being contacted.... will need to unregister
        "app.key.type=long", "app.client.protocol=rsocket", "app.primary=test",
        "app.rootkeys.consume.scheme=http", "app.client.discovery=local",
        "app.rsocket.transport.unprotected", "app.client.rsocket.core.key",
        "app.client.rsocket.core.persistence", "app.client.rsocket.core.index", "app.client.rsocket.core.pubsub",
        "app.client.rsocket.core.secrets", "app.client.rsocket.composite.user", "app.client.rsocket.composite.message",
        "app.client.rsocket.composite.topic", "app.service.composite.auth",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.cloud.consul.config.enabled=false", "spring.rsocket.server.port=0", "server.port=0",
        "spring.shell.interactive.enabled=false", "management.endpoints.enabled-by-default=false",
    ]
)
@ActiveProfiles("shell")
//@SingletonContainers: https://www.testcontainers.org/test_framework_integration/manual_lifecycle_control/#singleton-containers
open class ShellIntegrationTestBase {

    companion object {
        val imageName = "chat-deploy-long-memory-integration-test:0.0.1"

        val container = GenericContainer(imageName).withExposedPorts(6790, 6792)
            .apply {
                start()
                setWaitStrategy(
                    LogMessageWaitStrategy()
                        .withRegEx("*Netty RSocket started*") //Netty started on port
                        .withTimes(1)
                        .withStartupTimeout(Duration.ofSeconds(5))
                )
                withReuse(true)
            }

        @JvmStatic
        @DynamicPropertySource
        fun clientPropertySetup(registry: org.springframework.test.context.DynamicPropertyRegistry) {
            val servicePort = container.getMappedPort(6790).toString()
            val actuatorPort = container.getMappedPort(6792).toString()
            val host = container.host
            val serviceHostAndPort = "$host:$servicePort"

            val configPrefix = "app.client.discovery.config"
            for (service in listOf(
                "key",
                "persistence",
                "index",
                "pubsub",
                "secrets",
                "user",
                "message",
                "topic",
                "auth"
            )) {
                registry.add("$configPrefix.$service.dest") { serviceHostAndPort }
            }

            registry.add("app.rootkeys.consume.source") { "http://$host:$actuatorPort" }
            registry.add("app.client.discovery.local.host") { host }
            registry.add("app.client.discovery.local.port") { servicePort }
        }
    }
}
package com.demo.chat.deploy.tests

import com.demo.chat.config.TypeUtilConfiguration
import com.demo.chat.config.client.rsocket.ClientConfiguration
import com.demo.chat.config.client.rsocket.ConsulDiscoveryRequesterFactory
import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.config.secure.TransportConfiguration
import com.ecwid.consul.v1.ConsulClient
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cloud.consul.ConsulAutoConfiguration
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.consul.ConsulContainer
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.Arrays

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [
        ClientConfiguration::class,
        ConsulAutoConfiguration::class,
        ConsulDiscoveryRequesterFactory::class,
        TransportConfiguration::class,
        RSocketStrategiesAutoConfiguration::class, RSocketRequesterAutoConfiguration::class,
        TestConfigs::class,
        TypeUtilConfiguration::class
    ]
)
@TestPropertySource(
    properties = [
        "app.service.core.key", "app.key.type=long", "spring.cloud.consul.config.enabled=true",
        "spring.cloud.consul.discovery.enabled=true", "spring.cloud.discovery.enabled=true",
        "app.client.protocol=rsocket", "server.port=0", "management.endpoints.enabled-by-default=false",
        "app.rsocket.transport.unprotected",
        "app.client.rsocket.core.persistence", "app.client.rsocket.core.index", "app.client.rsocket.core.pubsub",
        "app.client.rsocket.core.secrets", "app.client.rsocket.core.key",
        "app.rsocket.client.requester.factory=consul",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.shell.interactive.enabled=false"]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
class DeployContextTests {
    companion object {
        val imageName = "consul:1.14.4"

        @Container
        val consulContainer = ConsulContainer(imageName).apply {
            withExposedPorts(8500)
            withReuse(true)
            //withLogConsumer(ContainerUtils.containerLogsConsumer(log))
            withNetwork(Network.SHARED)
            withStartupTimeout(Duration.ofSeconds(60))
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun consulProperties(registry: org.springframework.test.context.DynamicPropertyRegistry) {
            registry.add("spring.cloud.consul.host") { consulContainer.host }
            registry.add("spring.cloud.consul.port") { consulContainer.getMappedPort(8500) }

            val client = ConsulClient(consulContainer.host, consulContainer.getMappedPort(8500))

            client.agentServiceRegister(
                com.ecwid.consul.v1.agent.model.NewService().apply {
                    name = "core-service-rsocket"
                    address = "localhost"
                    port = 7901
                    id = "core-service-rsocket"
                    tags = listOf("core-service-rsocket")
                }
            )
        }
    }

    @Autowired
    private lateinit var consulClient: ConsulClient

    @BeforeAll
    fun setup() {

    }

    @Test
    fun contextLoads() {
    }
}

@TestConfiguration
@EnableConfigurationProperties(ConsulDiscoveryProperties::class)
class TestConfigs() {

}
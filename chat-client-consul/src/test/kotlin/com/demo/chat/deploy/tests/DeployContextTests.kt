package com.demo.chat.deploy.tests

import com.demo.chat.config.BaseDomainConfiguration
import com.demo.chat.config.client.rsocket.ClientConfiguration
import com.demo.chat.config.client.rsocket.ConsulDiscoveryRequesterFactory
import com.demo.chat.config.client.rsocket.DiscoveryClientConfiguration
import com.demo.chat.config.client.rsocket.RSocketPropertyConfiguration
import com.demo.chat.config.secure.TransportConfiguration
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.cloud.consul.ConsulAutoConfiguration
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [
        RSocketStrategiesAutoConfiguration::class,
        RSocketRequesterAutoConfiguration::class,
        ConsulAutoConfiguration::class,
        ClientConfiguration::class,
        TransportConfiguration::class,
        DiscoveryClientConfiguration::class, // Fix later... maybe only test does not work
        ConsulDiscoveryRequesterFactory::class,
        TestConfigs::class,
        BaseDomainConfiguration::class,
        RSocketPropertyConfiguration::class
    ]
)
@TestPropertySource(
    properties = [
        "spring.cloud.consul.config.enabled=true", "spring.cloud.consul.discovery.enabled=true",
        "spring.cloud.discovery.enabled=true", "spring.cloud.consul.enabled=true",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.config.import=optional:consul:", "spring.cloud.discovery.reactive.enabled=true",
        "management.endpoints.enabled-by-default=false", "spring.shell.interactive.enabled=false",
        "app.service.core.key", "app.key.type=long", "app.client.protocol=rsocket", "server.port=0",
        "app.rsocket.transport.unprotected", "app.client.rsocket.core.persistence",
        "app.client.rsocket.core.index", "app.client.rsocket.core.pubsub",
        "app.client.rsocket.core.secrets", "app.client.rsocket.core.key",
        "app.rsocket.client.requester.factory=consul"]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
class DeployContextTests : ConsulContainerSetup() {

    @BeforeAll
    fun setup() {
    }

    @Test
    fun contextLoads() {
    }
}

@TestConfiguration
@EnableDiscoveryClient
@EnableConfigurationProperties(ConsulDiscoveryProperties::class)
class TestConfigs(val discoveryClient: ConsulReactiveDiscoveryClient) {
}
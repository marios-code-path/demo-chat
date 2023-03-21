package com.demo.chat.deploy.tests

import com.demo.chat.config.BaseDomainConfiguration
import com.demo.chat.config.client.rsocket.ClientConfiguration
import com.demo.chat.config.client.rsocket.ConsulDiscoveryRequesterFactory
import com.demo.chat.config.client.rsocket.RSocketPropertyConfiguration
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
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [
        ClientConfiguration::class,
        ConsulAutoConfiguration::class,
        ConsulDiscoveryRequesterFactory::class,
        TransportConfiguration::class,
        RSocketStrategiesAutoConfiguration::class, RSocketRequesterAutoConfiguration::class,
        TestConfigs::class,
        BaseDomainConfiguration::class,
        RSocketPropertyConfiguration::class
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
class DeployContextTests : ConsulContainerSetup() {

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
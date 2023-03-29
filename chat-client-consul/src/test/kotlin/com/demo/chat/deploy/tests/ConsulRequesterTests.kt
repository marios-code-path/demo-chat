package com.demo.chat.deploy.tests

import com.demo.chat.config.client.rsocket.ConsulRequesterFactoryConfiguration
import com.demo.chat.config.client.rsocket.DiscoveryClientConfiguration
import com.demo.chat.service.client.transport.ClientTransportFactory
import com.ecwid.consul.v1.ConsulClient
import io.rsocket.transport.ClientTransport
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.consul.ConsulAutoConfiguration
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension


@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    ConsulAutoConfiguration::class,
    ConsulRequesterFactoryConfiguration::class,
    DiscoveryClientConfiguration::class)
@EnableConfigurationProperties(ConsulDiscoveryProperties::class)
@TestPropertySource(properties = ["app.rsocket.client.requester.factory=consul",
    "spring.cloud.consul.discovery.enabled=true" ])
class ConsulRequesterTests {  //} : ConsulContainerSetup(){

    @Autowired
    private lateinit var discovery: ConsulReactiveDiscoveryClient

    @Test
    fun `get consulFactory`() {


    }
}
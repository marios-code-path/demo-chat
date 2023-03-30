package com.demo.chat.deploy.tests

import com.demo.chat.config.client.discovery.DiscoveryClientConfiguration
import com.demo.chat.service.client.ClientProperties
import com.demo.chat.service.client.ClientProperty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.consul.ConsulAutoConfiguration
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension


@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    ConsulAutoConfiguration::class,
    DiscoveryClientConfiguration::class)
@EnableConfigurationProperties(ConsulDiscoveryProperties::class)
@TestPropertySource(properties = ["app.client.discovery=consul",
    "spring.cloud.consul.discovery.enabled=true" ])
class ConsulDiscoveryTests {  //} : ConsulContainerSetup(){

    @MockBean
    lateinit var clientProps: ClientProperties<ClientProperty>

    @Autowired
    private lateinit var discovery: ConsulReactiveDiscoveryClient

    @Test
    fun `get consulFactory`() {


    }
}

@TestConfiguration
class DiscoveryTestConfiguration {
//    @Bean
//    fun clientProperties() = ClientProperties<ClientProperty>()
}
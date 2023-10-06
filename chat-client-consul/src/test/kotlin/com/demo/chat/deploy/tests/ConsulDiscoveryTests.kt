package com.demo.chat.deploy.tests

import com.demo.chat.config.client.discovery.DiscoveryClientConfiguration
import com.demo.chat.service.client.ClientProperties
import com.demo.chat.service.client.ClientProperty
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.client.discovery.composite.reactive.ReactiveCompositeDiscoveryClientAutoConfiguration
import org.springframework.cloud.commons.util.InetUtils
import org.springframework.cloud.commons.util.InetUtilsProperties
import org.springframework.cloud.consul.ConsulAutoConfiguration
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClientConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension


@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ContextConfiguration(classes = [
    ConsulAutoConfiguration::class,
    DiscoveryClientConfiguration::class,
    DiscoveryTestConfiguration::class])
@TestPropertySource(properties = ["app.client.discovery=consul",
    "spring.cloud.consul.discovery.enabled=true", "spring.cloud.util.enabled=true",
"spring.cloud.discovery.reactive.enabled=true"])
@Disabled
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
@EnableConfigurationProperties(ConsulDiscoveryProperties::class)
class DiscoveryTestConfiguration {
//    @Bean
//    fun clientProperties() = ClientProperties<ClientProperty>()
}
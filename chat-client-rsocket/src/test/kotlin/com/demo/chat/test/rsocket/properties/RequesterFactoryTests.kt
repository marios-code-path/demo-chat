package com.demo.chat.test.rsocket.properties

import com.demo.chat.client.discovery.PropertiesBasedDiscovery
import com.demo.chat.client.rsocket.RSocketRequesterFactory
import com.demo.chat.client.rsocket.transport.InsecureConnection
import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.test.YamlFileContextInitializer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@ExtendWith(SpringExtension::class)
@SpringJUnitConfig(initializers = [RequesterFactoryTests.TestProperties::class])
@EnableConfigurationProperties(RSocketClientProperties::class)
class RequesterFactoryTests {
    class TestProperties : YamlFileContextInitializer("classpath:clientRSocketTestProperties.yaml")

    @Autowired
    private lateinit var clientProperties: RSocketClientProperties

    @Test
    fun `client properties loads`() {
        Assertions.assertThat(clientProperties.config)
            .isNotNull
            .isNotEmpty
    }

    @Test
    fun `discovery should Construct`() {
        val discovery = PropertiesBasedDiscovery(clientProperties)

        Assertions.assertThat(discovery)
            .isNotNull
    }

    @Test
    fun `requesterFactory should Construct`() {
        val discovery = PropertiesBasedDiscovery(clientProperties)

        val factory = RSocketRequesterFactory(
            discovery,
            RSocketRequester.builder(),
            InsecureConnection()
        )

        Assertions
            .assertThat(factory)
            .isNotNull
    }

    @Test
    fun `fetch an instance from discovery`() {
        val discovery = PropertiesBasedDiscovery(clientProperties)

        val connectionProperty = discovery.getServiceInstance("user").block()!!
        val prefix = clientProperties.config["user"]?.prefix

        Assertions
            .assertThat(prefix)
            .isNotNull
            .isNotEmpty
            .isEqualTo("user.")

        Assertions
            .assertThat(connectionProperty)
            .isNotNull
            .hasFieldOrPropertyWithValue("host", "127.0.0.1")
            .hasFieldOrPropertyWithValue("port", 6790)
    }
}
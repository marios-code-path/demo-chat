package com.demo.chat.test.rsocket.properties

import com.demo.chat.client.rsocket.DefaultRequesterFactory
import com.demo.chat.client.rsocket.config.RSocketClientProperties
import com.demo.chat.secure.rsocket.InsecureConnection
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
    fun `requesterFactory should Construct`() {
        val factory = DefaultRequesterFactory(
            RSocketRequester.builder(),
            InsecureConnection(), clientProperties
        )

        Assertions
            .assertThat(factory)
            .isNotNull
    }

    @Test
    fun `fetch One from RequesterFactory`() {
        val factory = DefaultRequesterFactory(
            RSocketRequester.builder(),
            InsecureConnection(), clientProperties
        )

        val connectionProperty = factory.serviceDestination("user")
        val prefix = clientProperties.config["user"]?.prefix

        Assertions
            .assertThat(prefix)
            .isNotNull
            .isNotEmpty
            .isEqualTo("user.")

        Assertions
            .assertThat(connectionProperty)
            .isNotNull
            .isNotEmpty
            .isEqualTo("127.0.0.1:6790")
    }
}
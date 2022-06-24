package com.demo.chat.test.rsocket.properties

import com.demo.chat.client.rsocket.config.ClientRSocketProperties
import com.demo.chat.client.rsocket.config.RSocketPropertyValue
import com.demo.chat.test.YamlFileContextInitializer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@ExtendWith(SpringExtension::class)
@SpringJUnitConfig(initializers = [ClientRSocketPropertiesTests.TestProperties::class])
@EnableConfigurationProperties(ClientRSocketProperties::class)
class ClientRSocketPropertiesTests {
    class TestProperties : YamlFileContextInitializer("classpath:clientRSocketTestProperties.yaml")

    @Autowired
    private lateinit var clientProperties: ClientRSocketProperties

    @Test
    fun `should load service properties`() {
        Assertions.assertThat(clientProperties.config)
            .containsKeys("key", "index", "persistence", "pubsub", "user", "message", "topic")
    }

    @Test
    fun `service property loaded`() {
        Assertions.assertThat(clientProperties.getServiceConfig("key"))
            .isInstanceOf(RSocketPropertyValue::class.java)
            .hasFieldOrPropertyWithValue("dest", "core-service-rsocket")
            .hasFieldOrPropertyWithValue("prefix", "key.")
    }
}
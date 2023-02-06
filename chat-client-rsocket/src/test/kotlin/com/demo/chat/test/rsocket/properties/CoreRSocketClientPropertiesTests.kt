package com.demo.chat.test.rsocket.properties

import com.demo.chat.client.rsocket.config.RSocketAppProperties
import com.demo.chat.client.rsocket.RSocketProperty
import com.demo.chat.test.YamlFileContextInitializer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@ExtendWith(SpringExtension::class)
@SpringJUnitConfig(initializers = [CoreRSocketClientPropertiesTests.TestProperties::class])
@EnableConfigurationProperties(RSocketAppProperties::class)
class CoreRSocketClientPropertiesTests {

    class TestProperties : YamlFileContextInitializer("classpath:appRsocketTestProperties.yaml")

    @Autowired
    private lateinit var appRsocketProperties: RSocketAppProperties

    @Test
    fun `rsocket property structure is consistent with spec`() {
        Assertions
            .assertThat(appRsocketProperties)
            .isNotNull
            .extracting("core.pubsub")
            .isInstanceOf(RSocketProperty::class.java)
            .hasNoNullFieldsOrProperties()
            .hasFieldOrPropertyWithValue("dest", "core-service-rsocket")
            .hasFieldOrPropertyWithValue("prefix", "pubsub.")
    }
}
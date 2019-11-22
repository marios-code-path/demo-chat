package com.demo.chatgateway

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.core.publisher.Mono
import java.net.URI

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WSGatewayMockTests {

    lateinit var context: AnnotationConfigApplicationContext

    lateinit var configurationProperties: WebSocketConfigurationProperties

    @BeforeAll
    fun setUpOnce() {

        context = AnnotationConfigApplicationContext(
                TestWSConfiguration::class.java
        )

        configurationProperties = context.getBean(WebSocketConfigurationProperties::class.java)
    }

    @Test
    fun `needs to connect`() {
        val client = ReactorNettyWebSocketClient()
        client.execute(URI("ws://localhost:" + configurationProperties.port + "/dist")
        ) {
            Assertions
                    .assertThat(it)
                    .isNotNull
            Mono.empty()
        }
    }

    @Test
    fun `needs to connect and receive info-alert event`() {
        val client = ReactorNettyWebSocketClient()
        client.execute(URI("ws://localhost:" + configurationProperties.port + "/userdist")
        ) {
            // push into processor the events this user should receive
            it.receive().then()
        }
    }

}
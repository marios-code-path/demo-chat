package com.demo.chat.test.gateway

import com.demo.chat.streams.gateway.App
import com.demo.chat.streams.gateway.EnricherGateway
import com.demo.chat.test.TestBase
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureWebTestClient
@SpringBootTest(classes = [App::class])
class MembershipGatewayTests : TestBase() {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var gateway: EnricherGateway

    @Test
    fun `should gateway`() {
        val echoed = gateway.doEcho("hello")
        Assertions
            .assertThat(echoed)
            .isNotNull
            .isEqualTo("OLLEH")
    }

    @Test
    fun `should http inbound webFlux`() {
        webTestClient
            .post()
            .uri("/test")
            .bodyValue("hEllO")
            .accept(MediaType.ALL)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .consumeWith { res ->
                Assertions
                    .assertThat(String(res.responseBodyContent!!))
                    .isEqualTo("OLLEH")
            }
    }
}
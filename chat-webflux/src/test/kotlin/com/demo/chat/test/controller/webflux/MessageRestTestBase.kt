package com.demo.chat.test.controller.webflux

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.controller.webflux.ChatMessageServiceController
import com.demo.chat.controller.webflux.ChatUserServiceController
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.LongCompositeServiceBeans
import com.demo.chat.test.controller.webflux.context.WithLongCustomChatUser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.http.MediaType
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

@ContextConfiguration(
    classes = [LongCompositeServiceBeans::class, WebFluxTestConfiguration::class, ChatMessageServiceController::class]
)
class LongMessageRestTests : MessageRestTestBase<Long>({ 1001L })

@WebFluxTest
@ExtendWith(RestDocumentationExtension::class, SpringExtension::class)
@TestPropertySource(properties = ["app.controller.message"])
@AutoConfigureRestDocs
open class MessageRestTestBase<T>(
    val idSupply: () -> T
) {

    @Autowired
    private lateinit var beans: CompositeServiceBeans<T, String>

    @Autowired
    private lateinit var client: WebTestClient

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = ["TEST"])
    fun `should send`() {
        val service = beans.messageService()

        BDDMockito
            .given(service.send(anyObject()))
            .willReturn(Mono.empty())

        client
            .post()
            .uri("/message/send/12345")
            .bodyValue("TESTMESSAGE")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .isEmpty
    }

    @Test
    fun `should get message by ID`() {
        val service = beans.messageService()

        BDDMockito
            .given(service.messageById(anyObject()))
            .willReturn(
                Mono.just(
                    Message.create(
                        MessageKey.create(idSupply(), idSupply(), idSupply()),
                        "TEST",
                        true
                    )
                )
            )

        client
            .get()
            .uri("/message/id/1234566")
            .exchange()
            .expectStatus().isOk
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .consumeWith { res -> println(String(res.responseBodyContent!!, Charsets.UTF_8)) }
            .jsonPath("$.message.data").isEqualTo("TEST")
    }
}
package com.demo.chat.test.controller.webflux.composite

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.controller.webflux.ChatMessageServiceController
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.client.ClientDiscovery
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.LongCompositeServiceBeans
import com.demo.chat.test.controller.webflux.TestServiceInstance
import com.demo.chat.test.controller.webflux.config.WebFluxTestConfiguration
import com.demo.chat.test.controller.webflux.config.WithLongCustomChatUser
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.contract.wiremock.restdocs.SpringCloudContractRestDocs
import org.springframework.http.MediaType
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.operation.preprocess.Preprocessors
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.net.URI

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

    @MockBean
    private lateinit var discovery: ClientDiscovery

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = ["TEST"])
    fun `should listen to topic`() {
        BDDMockito
            .given(discovery.getServiceInstance(anyObject()))
            .willReturn(Mono.just(TestServiceInstance))

        client
            .get()
            .uri("/message/topic/12345")
            .exchange()
            .expectStatus().isTemporaryRedirect
            .expectBody()
            .consumeWith { res ->
                val uri = URI.create(String(res.responseBodyContent!!, Charsets.UTF_8))

                Assertions
                    .assertThat(uri)
                    .hasScheme("ws")
            }
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "listen",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
    }

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
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "send",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .isEmpty
    }

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = ["TEST"])
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
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "byId",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .jsonPath("$.message.data").isEqualTo("TEST")
    }
}
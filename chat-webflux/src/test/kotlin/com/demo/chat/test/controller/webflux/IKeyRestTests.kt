package com.demo.chat.test.controller.webflux

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.controller.webflux.IKeyRestController
import com.demo.chat.controller.webflux.core.mapping.KindRequest
import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import com.demo.chat.test.anyObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.contract.wiremock.restdocs.SpringCloudContractRestDocs
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono


@ContextConfiguration(classes = [LongKeyTestConfiguration::class, IKeyRestController::class])
@WebFluxTest(IKeyRestController::class)
@ExtendWith(RestDocumentationExtension::class, SpringExtension::class)
@AutoConfigureRestDocs
class IKeyRestTests {

    @Autowired
    private lateinit var client: WebTestClient

    @MockBean
    private lateinit var testKeyService: TestKeyService

    @Test
    fun `create a new key`() {
        BDDMockito
            .given(testKeyService.key<Any>(anyObject()))
            .willReturn(Mono.just(Key.funKey(1001L)))

        client
            .post()
            .uri("/key/new")
            .bodyValue(KindRequest("java.lang.String"))
            //  .accept(MediaType.APPLICATION_JSON)//MediaType("text", "plain", StandardCharsets.UTF_8))
            .exchange()
            .expectStatus().isOk
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("\$.key.id").isNumber
            .consumeWith(
                document(
                    "new key",
                    SpringCloudContractRestDocs.dslContract()
                )
            )
//            .consumeWith { res ->
//                document<EntityExchangeResult<ByteArray>>("foo", SpringCloudContractRestDocs.dslContract())
//                    .accept(res)
//            }
    }
}

interface TestKeyService : IKeyService<Long>

@TestConfiguration
class LongKeyTestConfiguration {
    @Bean
    fun keyServiceBean(tk: TestKeyService): KeyServiceBeans<Long> =
        object : KeyServiceBeans<Long> {
            override fun keyService(): IKeyService<Long> =  tk 
        }
}
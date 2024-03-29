package com.demo.chat.test.controller.webflux

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.controller.webflux.SecretsRestController
import com.demo.chat.domain.Key
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.TestLongKeyServiceBeans
import com.demo.chat.test.config.TestLongSecretsStoreBeans
import com.demo.chat.test.controller.webflux.config.LongUserDetailsConfiguration
import com.demo.chat.test.controller.webflux.config.WebFluxTestConfiguration
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
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


@ContextConfiguration(classes = [TestLongSecretsStoreBeans::class, TestLongKeyServiceBeans::class,
    LongUserDetailsConfiguration::class, WebFluxTestConfiguration::class, SecretsRestController::class])
class LongSecretsRestTestBase: SecretsRestTestBase<Long>( { Key.funKey(1L) })

@Disabled
@WebFluxTest
@ExtendWith(RestDocumentationExtension::class, SpringExtension::class)
@TestPropertySource(properties = ["app.controller.secrets"])
@AutoConfigureRestDocs
open class SecretsRestTestBase<T>(
    private val keySupplier: () -> Key<T>
) {

    @Autowired
    private lateinit var beans: SecretsStoreBeans<T>

    @Autowired
    private lateinit var keyBeans: KeyServiceBeans<T>

    @Autowired
    private lateinit var client: WebTestClient

    @Test
    fun `should add credential, receive key`() {
        val service = beans.secretsStore()
        val keyService = keyBeans.keyService()

        BDDMockito
            .given(service.addCredential(anyObject()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(keyService.key<Any>(anyObject()))
            .willReturn(Mono.just(keySupplier()))

        client
            .put()
            .uri("/secrets/new")
            .bodyValue("SECRET")
            .exchange()
            .expectStatus().isCreated
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "new",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .jsonPath("\$.key.id").isNotEmpty
    }

    @Test
    fun `should add credential with key`() {
        val service = beans.secretsStore()

        BDDMockito
            .given(service.addCredential(anyObject()))
            .willReturn(Mono.empty())

        client
            .put()
            .uri("/secrets/new/12345")
            .bodyValue("SECRET")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "keyedNew",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .isEmpty
    }

    @Test
    fun `should get credential`() {
        val service = beans.secretsStore()

        BDDMockito
            .given(service.getStoredCredentials(anyObject()))
            .willReturn(Mono.just("SECRET"))

        client
            .get()
            .uri("/secrets/12345")
            .exchange()
            .expectStatus().isOk
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "cred",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .equals("SECRET")
    }

    @Test
    fun `should compare secrets`() {
        val service = beans.secretsStore()

        BDDMockito
            .given(service.compareSecret(anyObject()))
            .willReturn(Mono.just(true))

        client
            .post()
            .uri("/secrets/compare/12345")
            .bodyValue("SECRET")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "compare",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .equals("true")

    }
}
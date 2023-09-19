package com.demo.chat.test.controller.webflux

import com.demo.chat.controller.webflux.core.mapping.KindRequest
import com.demo.chat.domain.Key
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.test.anyObject
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


@Disabled
@WebFluxTest
@ExtendWith(RestDocumentationExtension::class, SpringExtension::class)
@TestPropertySource(properties = ["app.controller.persistence"])
@AutoConfigureRestDocs
open class PersistenceRestTestBase<T, E : Any>(
    val entityPath: String,
    val entitySupplier: () -> E,
    val keySupplier: () -> Key<T>,
    val requestSupplier: () -> Any,
    val persistenceStore: PersistenceStore<T, E>
) {

    @Autowired
    private lateinit var client: WebTestClient

    @Test
    fun `should add`() {
        BDDMockito.given(persistenceStore.add(anyObject()))
            .willReturn(Mono.empty())

        BDDMockito.given(persistenceStore.key())
            .willReturn(Mono.just(keySupplier()))

        client
            .put()
            .uri("/persist/${entityPath}/add")
            .bodyValue(requestSupplier())
            .exchange()
            .expectStatus()
            .isCreated
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "add",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
    }

    @Test
    fun `should remove`() {
        BDDMockito.given(persistenceStore.rem(anyObject()))
            .willReturn(Mono.empty())

        client
            .delete()
            .uri("/persist/${entityPath}/rem/1001")
            .exchange()
            .expectStatus()
            .isNoContent
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "rem",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
    }

    @Test
    fun `should get one`() {
        BDDMockito.given(persistenceStore.get(anyObject()))
            .willReturn(Mono.just(entitySupplier()))

        client
            .get()
            .uri("/persist/${entityPath}/get/1001")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "get",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
    }

    @Test
    fun `should get all`() {
        val entities = listOf(
            entitySupplier(),
            entitySupplier()
        )

        BDDMockito.given(persistenceStore.all())
            .willReturn(Flux.fromIterable(entities))

        client
            .get()
            .uri("/persist/${entityPath}/all")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "all",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
    }

    @Test
    fun `should get a new key`() {

        BDDMockito.given(persistenceStore.key())
            .willReturn(Mono.just(keySupplier()))

        client
            .post()
            .uri("/persist/${entityPath}/key")
            .bodyValue(KindRequest("java.lang.String"))
            .exchange()
            .expectStatus()
            .isCreated
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "key",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
    }
}
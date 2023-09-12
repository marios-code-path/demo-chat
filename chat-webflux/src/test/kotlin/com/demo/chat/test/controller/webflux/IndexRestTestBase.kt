package com.demo.chat.test.controller.webflux

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.service.core.IndexService
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
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Disabled
@WebFluxTest
@ExtendWith(RestDocumentationExtension::class, SpringExtension::class)
@AutoConfigureRestDocs
open class IndexRestTestBase<T, V : Any, Q : IndexSearchRequest>(
    val entityPath: String,
    val entitySupplier: () -> V,
    val keySupplier: () -> Key<T>,
    val requestSupplier: () -> Q,
    val index: IndexService<T, V, Q>
) {

    @Autowired
    private lateinit var client: WebTestClient

    @Test
    fun `should add`() {
        BDDMockito.given(index.add(anyObject()))
            .willReturn(Mono.empty())

        client
            .put()
            .uri("/index/${entityPath}/add")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(entitySupplier())
            .exchange()
            .expectStatus()
            .isCreated
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
        BDDMockito.given(index.rem(anyObject()))
            .willReturn(Mono.empty())

        client
            .delete()
            .uri("/index/${entityPath}/rem/1001")
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
    fun `should findUnique`() {
        BDDMockito.given(index.findUnique(anyObject()))
            .willReturn(Mono.just(keySupplier()))

        client
            .get()
            .uri("/index/${entityPath}/findUnique?first=name&second=test&config=1")
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
    fun `should findBy`() {
        BDDMockito.given(index.findBy(anyObject()))
            .willReturn(Flux.just(keySupplier()))

        client
            .get()
            .uri("/index/${entityPath}/findBy?first=name&second=test&config=1")
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

}
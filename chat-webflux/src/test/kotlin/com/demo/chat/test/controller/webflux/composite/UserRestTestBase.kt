package com.demo.chat.test.controller.webflux.composite

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.controller.webflux.ChatUserServiceController
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.TestLongCompositeServiceBeans
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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@ContextConfiguration(
    classes = [TestLongCompositeServiceBeans::class, WebFluxTestConfiguration::class, ChatUserServiceController::class]
)
class LongUserRestTests : UserRestTestBase<Long>(
    { 1001L },
    {
        User.create(
            Key.funKey(1001L), "TestName", "TestHandle", "http://testUri"
        )
    }
)

@Disabled
@WebFluxTest
@ExtendWith(RestDocumentationExtension::class, SpringExtension::class)
@TestPropertySource(properties = ["app.controller.user"])
@AutoConfigureRestDocs
open class UserRestTestBase<T>(
    private val idSupplier: () -> T,
    private val userSupplier: () -> User<T>
) {

    @Autowired
    private lateinit var client: WebTestClient

    @Autowired
    private lateinit var beans: CompositeServiceBeans<T, String>

    @Test
    fun `should add user`() {
        val service = beans.userService()

        BDDMockito
            .given(service.addUser(anyObject()))
            .willReturn(Mono.just(userSupplier().key))

        client
            .post()
            .uri("/user/new")
            .bodyValue(UserCreateRequest("TestName", "testHandle", "http://uri"))
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
            .jsonPath("$.key.id").isNotEmpty
    }

    @Test
    fun `should find user by name`() {
        val service = beans.userService()

        BDDMockito
            .given(service.findByUsername(anyObject()))
            .willReturn(Flux.just(userSupplier()))

        client
            .get()
            .uri("/user/handle/testhandle")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .consumeWith { req -> println("res: " + String(req.responseBodyContent!!, Charsets.UTF_8)) }
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "byHandle",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .jsonPath("$.[0].user.name").isNotEmpty
    }

    @Test
    fun `should find user by id`() {
        val service = beans.userService()

        BDDMockito
            .given(service.findByUserId(anyObject()))
            .willReturn(Mono.just(userSupplier()))

        client
            .get()
            .uri("/user/id/12345")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .consumeWith { req -> println("res: " + String(req.responseBodyContent!!, Charsets.UTF_8)) }
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "byId",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .jsonPath("$.user.name").isNotEmpty
    }
}
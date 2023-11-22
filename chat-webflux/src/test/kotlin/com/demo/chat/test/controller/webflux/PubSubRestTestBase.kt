package com.demo.chat.test.controller.webflux

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.controller.webflux.PubSubRestController
import com.demo.chat.domain.*
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.TestLongKeyServiceBeans
import com.demo.chat.test.config.TestLongPubSubBeans
import com.demo.chat.test.controller.webflux.config.LongUserDetailsConfiguration
import com.demo.chat.test.controller.webflux.config.WebFluxTestConfiguration
import com.demo.chat.test.controller.webflux.config.WithLongCustomChatUser
import com.demo.chat.test.key.MockKeyGeneratorResolver
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.cloud.contract.wiremock.restdocs.SpringCloudContractRestDocs
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.operation.preprocess.Preprocessors
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.FluxExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier


@ContextConfiguration(
    classes = [TestLongPubSubBeans::class, TestLongKeyServiceBeans::class, LongUserDetailsConfiguration::class,
        WebFluxTestConfiguration::class, PubSubRestController::class]
)
class LongPubSubRestTests : PubSubRestTestBase<Long, String>(
    { User.create(Key.funKey(1L), "Test", "Test", "Test") },
    { MessageTopic.create(Key.funKey(10L), "TestTopic") },
    { Key.funKey(1001L) },
    { 1001L }
)

@Disabled
@WebFluxTest
@ExtendWith(RestDocumentationExtension::class, SpringExtension::class, MockKeyGeneratorResolver::class)
@TestPropertySource(properties = ["app.controller.pubsub"])
@AutoConfigureRestDocs
open class PubSubRestTestBase<T : Any, V>(
    val userSupplier: () -> User<T>,
    val topicSupplier: () -> MessageTopic<T>,
    private val keySupplier: () -> Key<T>,
    private val idSupplier: () -> T
) {

    @Autowired
    private lateinit var beans: PubSubServiceBeans<T, String>

    @Autowired
    private lateinit var keyBeans: KeyServiceBeans<T>

    @Autowired
    private lateinit var client: WebTestClient

    @Autowired
    private lateinit var mapper: ObjectMapper

    @Test
    fun `should listen and receive a message TEST`() {
        val pubsubService = beans.pubSubService()

        BDDMockito
            .given(pubsubService.listenTo(anyObject()))
            .willReturn(
                Flux.just(
                    Message.create(
                        MessageKey.Factory.create(keySupplier().id, keySupplier().id, keySupplier().id),
                        "TEST", true
                    )
                ).repeat(3)
            )
//  TODO: For now, we cannot rely on webTestClient to perform SerDeser
//        val ref = object : ParameterizedTypeReference<ServerSentEvent<Message<T, String>>>() {}

        client
            .get()
            .uri("/pubsub/listen/12345")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
            .returnResult(String::class.java)
            .consumeWith { res ->
                StepVerifier.create(res.responseBody)
                    .assertNext {
                        val message = mapper.readValue<Message<T, String>>(it)

                        Assertions
                            .assertThat(message)
                            .isNotNull
                            .hasFieldOrPropertyWithValue("data", "TEST")
                    }
                    .expectNextCount(2)
                    .thenCancel()
                    .verify()

                WebTestClientRestDocumentation.document<FluxExchangeResult<String>>(
                    "pubsub.listen",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                ).accept(res)
            }

        BDDMockito.verify(pubsubService, BDDMockito.times(1)).listenTo(anyObject())
    }

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = ["TEST"])
    //@WithUserDetails("TestHandle", userDetailsServiceBeanName = "mockChatUserDetailService")
    fun `should subscribe`() {
        val pubsubService = beans.pubSubService()

        BDDMockito
            .given(pubsubService.subscribe(anyObject(), anyObject()))
            .willReturn(Mono.empty())

        client
            .post()
            .uri("/pubsub/sub/12345")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "pubsub.subscribe",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .isEmpty

    }

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = ["TEST"])
    fun `should unsubscribe`() {
        val pubsubService = beans.pubSubService()

        BDDMockito
            .given(pubsubService.unSubscribe(anyObject(), anyObject()))
            .willReturn(Mono.empty())

        client
            .delete()
            .uri("/pubsub/sub/12345")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "pubsub.unsubscribe",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .isEmpty
    }

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = ["TEST"])
    fun `should unsubscribeAll`() {
        val pubsubService = beans.pubSubService()

        BDDMockito
            .given(pubsubService.unSubscribeAll(anyObject()))
            .willReturn(Mono.empty())

        client
            .delete()
            .uri("/pubsub/sub")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "pubsub.unsubscribeAll",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .isEmpty
    }

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = ["TEST"])
    fun `should drain topic`() {
        val pubsubService = beans.pubSubService()

        BDDMockito
            .given(pubsubService.unSubscribeAllIn(anyObject()))
            .willReturn(Mono.empty())

        client
            .delete()
            .uri("/pubsub/members/12345")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "pubsub.drain",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .isEmpty
    }

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = ["TEST"])
    fun `should send`() {
        val pubsubService = beans.pubSubService()
        val keyService = keyBeans.keyService()

        val key = keySupplier()

        BDDMockito
            .given(pubsubService.sendMessage(anyObject()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(keyService.key<Any>(anyObject()))
            .willReturn(Mono.just(key))

        client
            .post()
            .uri("/pubsub/send/12345")
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue("TESTMESSAGE")
            .exchange()
            .expectStatus().isOk
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "pubsub.send",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .equals(key.id.toString())
    }

    @Test
    fun `should call exists`() {
        val pubsubService = beans.pubSubService()

        BDDMockito
            .given(pubsubService.exists(anyObject()))
            .willReturn(Mono.just(true))

        client
            .get()
            .uri("/pubsub/exists/12345")
            .exchange()
            .expectStatus().isOk
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "pubsub.exists",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .equals("true")

    }

    @Test
    fun `should open`() {
        val pubsubService = beans.pubSubService()

        BDDMockito
            .given(pubsubService.open(anyObject()))
            .willReturn(Mono.empty())

        client
            .post()
            .uri("/pubsub/pub/12345")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "pubsub.open",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .isEmpty
    }

    @Test
    fun `should close`() {
        val pubsubService = beans.pubSubService()

        BDDMockito
            .given(pubsubService.close(anyObject()))
            .willReturn(Mono.empty())

        client
            .delete()
            .uri("/pubsub/pub/12345")
            .exchange()
            .expectStatus().isNoContent
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "pubsub.close",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
    }

    @Test
    fun `should get topics by user`() {
        val pubsubService = beans.pubSubService()

        BDDMockito
            .given(pubsubService.getByUser(anyObject()))
            .willReturn(Flux.just(idSupplier()))

        client
            .get()
            .uri("/pubsub/user/1001")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "pubsub.topicsByUser",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .equals("1001")
    }

    @Test
    fun `should usersBy to get by topic`() {
        val pubsubService = beans.pubSubService()

        BDDMockito
            .given(pubsubService.getUsersBy(anyObject()))
            .willReturn(Flux.just(idSupplier()))

        client
            .get()
            .uri("/pubsub/pub/1001")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .consumeWith(
                WebTestClientRestDocumentation.document(
                    "pubsub.byTopic",
                    Preprocessors.preprocessRequest(Preprocessors.prettyPrint()),
                    Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
                    SpringCloudContractRestDocs.dslContract()
                )
            )
            .equals("1001")
    }
}
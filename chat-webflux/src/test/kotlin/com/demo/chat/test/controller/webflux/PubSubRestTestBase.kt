package com.demo.chat.test.controller.webflux

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.controller.webflux.PubSubRestController
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.LongKeyServiceBeans
import com.demo.chat.test.config.LongPubSubBeans
import com.demo.chat.test.controller.webflux.context.LongUserDetailsConfiguration
import com.demo.chat.test.controller.webflux.context.WithLongCustomChatUser
import org.junit.jupiter.api.Disabled
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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@ContextConfiguration(
    classes = [LongPubSubBeans::class, LongKeyServiceBeans::class, LongUserDetailsConfiguration::class,
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
@ExtendWith(RestDocumentationExtension::class, SpringExtension::class)
@TestPropertySource(properties = ["app.controller.pubsub"])
@AutoConfigureRestDocs
open class PubSubRestTestBase<T: Any, V>(
    val userSupplier: () -> User<T>,
    val topicSupplier: () -> MessageTopic<T>,
    private val keySupplier: () -> Key<T>,
    private val idSupplier: () -> T
) {

    @Autowired
    private lateinit var beans: PubSubServiceBeans<T, V>

    @Autowired
    private lateinit var keyBeans: KeyServiceBeans<T>

    @Autowired
    private lateinit var client: WebTestClient

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
            .post()
            .uri("/pubsub/unsub/12345")
            .exchange()
            .expectStatus().isOk
            .expectBody()
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
            .post()
            .uri("/pubsub/unsuball")
            .exchange()
            .expectStatus().isOk
            .expectBody()
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
            .post()
            .uri("/pubsub/drain/12345")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .isEmpty
    }

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = ["TEST"])
    fun `should send`() {
        val pubsubService = beans.pubSubService()
        val keyService = keyBeans.keyService()

        BDDMockito
            .given(pubsubService.sendMessage(anyObject()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(keyService.key<Any>(anyObject()))
            .willReturn(Mono.just(keySupplier()))

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
            .equals(keySupplier().id.toString())
    }

    @Test fun `should call exists`() {
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
            .equals("true")
    }

    @Test fun `should open`() {
        val pubsubService = beans.pubSubService()

        BDDMockito
            .given(pubsubService.open(anyObject()))
            .willReturn(Mono.empty())

        client
            .put()
            .uri("/pubsub/open/12345")
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .isEmpty
    }

    @Test fun `should close`() {
        val pubsubService = beans.pubSubService()

        BDDMockito
            .given(pubsubService.close(anyObject()))
            .willReturn(Mono.empty())

        client
            .delete()
            .uri("/pubsub/close/12345")
            .exchange()
            .expectStatus().isNoContent
    }

    @Test fun `should get by user`() {
        val pubsubService = beans.pubSubService()

        BDDMockito
            .given(pubsubService.getByUser(anyObject()))
            .willReturn(Flux.just(idSupplier()))

        client
            .get()
            .uri("/pubsub/byuser/1001")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .equals("1001")
    }

    @Test fun `should usersBy to get by topic`() {
        val pubsubService = beans.pubSubService()

        BDDMockito
            .given(pubsubService.getUsersBy(anyObject()))
            .willReturn(Flux.just(idSupplier()))

        client
            .get()
            .uri("/pubsub/bytopic/1001")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .equals("1001")
    }
}
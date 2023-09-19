package com.demo.chat.test.controller.webflux

import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.controller.webflux.PubSubRestController
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.test.anyObject
import com.demo.chat.test.controller.webflux.config.LongPubSubBeans
import com.demo.chat.test.controller.webflux.context.ChatUserDetailSecurityContextFactory
import com.demo.chat.test.controller.webflux.context.LongUserDetailsConfiguration
import com.demo.chat.test.controller.webflux.context.WithLongCustomChatUser
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.test.context.support.WithSecurityContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

@ContextConfiguration(classes = [LongPubSubBeans::class, LongUserDetailsConfiguration::class,
    WebFluxTestConfiguration::class, PubSubRestController::class])
class LongPubSubRestTests : PubSubRestTestBase<Long, String>(
    { User.create(Key.funKey(1L), "Test", "Test", "Test") },
    { MessageTopic.create(Key.funKey(10L), "TestTopic") }
)

@Disabled
@WebFluxTest
@ExtendWith(RestDocumentationExtension::class, SpringExtension::class)
@TestPropertySource(properties = ["app.controller.pubsub"])
@AutoConfigureRestDocs
open class PubSubRestTestBase<T, V>(
    val userSupplier: () -> User<T>,
    val topicSupplier: () -> MessageTopic<T>,
) {

    @Autowired
    private lateinit var beans: PubSubServiceBeans<T, V>

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
            .uri("/pubsub/subscribe/12345")
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
            .uri("/pubsub/unsubscribe/12345")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .isEmpty
    }

}

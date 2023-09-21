package com.demo.chat.test.controller.webflux

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.controller.webflux.ChatTopicServiceController
import com.demo.chat.controller.webflux.ChatUserServiceController
import com.demo.chat.domain.*
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.LongCompositeServiceBeans
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
    classes = [LongCompositeServiceBeans::class, WebFluxTestConfiguration::class, ChatTopicServiceController::class]
)
class LongTopicRestTests : TopicRestTestBase<Long>(
    { Key.funKey(1001L) },
    { MessageTopic.create(Key.funKey(1L), "TESTTOPIC") }
)

@Disabled
@WebFluxTest
@ExtendWith(RestDocumentationExtension::class, SpringExtension::class)
@TestPropertySource(properties = ["app.controller.topic"])
@AutoConfigureRestDocs
open class TopicRestTestBase<T>(
    val keySupplier: () -> Key<T>,
    val topicSupplier: () -> MessageTopic<T>,
    )  {

    @Autowired
    private lateinit var client: WebTestClient

    @Autowired
    private lateinit var beans: CompositeServiceBeans<T, String>

    @Test
    fun `add room`() {
        val service = beans.topicService()

        BDDMockito
            .given(service.addRoom(anyObject()))
            .willReturn(Mono.just(keySupplier()))

        client
            .post()
            .uri("/topic/new")
            .bodyValue(ByStringRequest("TESTTOPIC"))
            .exchange()
            .expectStatus().isCreated
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("\$.key.id").isNotEmpty
    }

    @Test
    fun `delete room`() {
        val service = beans.topicService()

        BDDMockito
            .given(service.deleteRoom(anyObject()))
            .willReturn(Mono.empty())

        client
            .delete()
            .uri("/topic/id/12345")
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    fun `list room`() {
        val service = beans.topicService()

        BDDMockito
            .given(service.listRooms())
            .willReturn(Flux.just(topicSupplier(), topicSupplier()))

        client
            .get()
            .uri("/topic/list")
            .exchange()
            .expectStatus().isOk
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .consumeWith { res ->
                println(String(res.responseBodyContent!!, Charsets.UTF_8))
            }
            .jsonPath("\$.[0].topic.data").isNotEmpty
    }

    @Test
    fun `get one room by id`() {
        val service = beans.topicService()

        BDDMockito
            .given(service.getRoom(anyObject()))
            .willReturn(Mono.just(topicSupplier()))

        client
            .get()
            .uri("/topic/id/12345")
            .exchange()
            .expectStatus().isOk
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("\$.topic.data").isNotEmpty
    }

    @Test
    fun `get one room by name`() {
        val service = beans.topicService()

        BDDMockito
            .given(service.getRoomByName(anyObject()))
            .willReturn(Mono.just(topicSupplier()))

        client
            .get()
            .uri("/topic/name/testtopic")
            .exchange()
            .expectStatus().isOk
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .consumeWith{ res -> println("result: " + String(res.responseBodyContent!!, Charsets.UTF_8))}
            .jsonPath("\$.topic.data").isNotEmpty
    }

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = ["TEST"])
    fun `join a room`() {
        val service = beans.topicService()

        BDDMockito
            .given(service.joinRoom(anyObject()))
            .willReturn(Mono.empty())

        client
            .put()
            .uri("/topic/join/112345")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = ["TEST"])
    fun `leave topic`() {
        val service = beans.topicService()

        BDDMockito
            .given(service.leaveRoom(anyObject()))
            .willReturn(Mono.empty())

        client
            .put()
            .uri("/topic/leave/123456")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `get topic members`() {
        val service = beans.topicService()

        BDDMockito
            .given(service.roomMembers(anyObject()))
            .willReturn(Mono.just(TopicMemberships(setOf(TopicMember("1","testHandle","http://test")))))

        client
            .get()
            .uri("/topic/members/12345")
            .exchange()
            .expectStatus().isOk
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("\$.members.[0].handle").isNotEmpty
    }

}
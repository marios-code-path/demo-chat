package com.demo.chat.test.rsocket.controller.edge

import com.demo.chat.ByIdRequest
import com.demo.chat.ChatMessage
import com.demo.chat.controller.edge.MessagingController
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.MessagePersistence
import com.demo.chat.service.PubSubService
import org.assertj.core.api.AssertionsForClassTypes
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.retrieveFlux
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*
import java.util.function.Function
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
@Import(MockCoreServicesConfiguration::class,
        EdgeMessagingControllerTests.EdgeMessagingTestConfiguration::class)
class EdgeMessagingControllerTests : RSocketControllerTestBase() {
    @Autowired
    private lateinit var messagePersistence: MessagePersistence<UUID, String>

    @Autowired
    private lateinit var topicMessaging: PubSubService<UUID, String>

    @Autowired
    private lateinit var messageIndex: MessageIndexService<UUID, String, Map<String, String>>

    @Test
    fun `should fetch a single message`() {
        BDDMockito
                .given(messagePersistence.get(anyObject()))
                .willReturn(Mono.just(randomMessage()))

        StepVerifier
                .create(
                        requester
                                .route("message-by-id")
                                .data(ByIdRequest(UUID.randomUUID()))
                                .retrieveMono(ChatMessage::class.java)
                )
                .expectSubscription()
                .assertNext {
                    AssertionsForClassTypes
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("record", true)
                            .hasFieldOrProperty("key")
                }
                .verifyComplete()
    }

    @Test
    fun `should receive messages from a random topic`() {
        BDDMockito
                .given(messagePersistence.byIds(anyObject()))
                .willReturn(Flux.fromStream(Stream.generate { randomMessage() }.limit(5)))

        BDDMockito
                .given(topicMessaging.receiveOn(anyObject()))
                .willReturn(Flux.fromStream(Stream.generate { randomMessage() }.limit(5)))

        BDDMockito
                .given(messageIndex.findBy(anyObject()))
                .willReturn(Flux.fromStream(Stream.generate { randomMessage().key }.limit(5)))

        val receiverFlux = requester
                .route("message-listen-topic")
                .data(ByIdRequest(UUID.randomUUID()))
                .retrieveFlux<ChatMessage<UUID, String>>()

        StepVerifier
                .create(receiverFlux)
                .expectSubscription()
                .expectNextCount(10)
                .thenConsumeWhile({
                    it != null
                }, {
                    AssertionsForClassTypes
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("record", true)
                            .hasFieldOrProperty("key")

                    MatcherAssert
                            .assertThat("Message begins with Hello", it.data,
                                    Matchers.startsWith("Hello"))
                })
                .expectComplete()
                .verify()
    }

    @TestConfiguration
    class EdgeMessagingTestConfiguration {
        @Bean
        fun msgIdx(t: MessageIndexService<UUID, String, Map<String, String>>) = t

        @Bean
        fun msgPersist(t: MessagePersistence<UUID, String>): MessagePersistence<UUID, String> = t

        @Bean
        fun msging(t: PubSubService<UUID, String>): PubSubService<UUID, String> = t

        @Controller
        class TestMessagingController(
                messageIdx: MessageIndexService<UUID, String, Map<String, String>>,
                msgPersist: MessagePersistence<UUID, String>,
                messaging: PubSubService<UUID, String>,
        ) : MessagingController<UUID, String, Map<String, String>>(
                messageIdx,
                msgPersist,
                messaging,
                Function { i -> mapOf(Pair(MessageIndexService.TOPIC, i.id.toString())) }
        )
    }
}
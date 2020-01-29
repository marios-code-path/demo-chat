package com.demo.chat.test.controller.app

import com.demo.chat.ChatMessage
import com.demo.chat.MessageRequest
import com.demo.chat.controller.app.TopicMessagingController
import com.demo.chat.service.ChatTopicMessagingService
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.MessagePersistence
import org.assertj.core.api.AssertionsForClassTypes
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.retrieveFlux
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
@Import(TestConfigurationRSocket::class,
        RSocketTopicMessagingTests.TestConfiguration::class)
class RSocketTopicMessagingTests : ControllerTestBase() {
    @Autowired
    private lateinit var messagePersistence: MessagePersistence<UUID, String>

    @Autowired
    private lateinit var topicMessaging: ChatTopicMessagingService<UUID, String>

    @Autowired
    private lateinit var messageIndex: MessageIndexService<UUID, String>

    @Test
    fun `should fetch a single message`() {
        BDDMockito
                .given(messagePersistence.get(anyObject()))
                .willReturn(Mono.just(randomMessage()))

        StepVerifier
                .create(
                        requestor
                                .route("message-by-id")
                                .data(MessageRequest(UUID.randomUUID()))
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

        val receiverFlux = requestor
                .route("message-listen-topic")
                .data(MessageRequest(UUID.randomUUID()))
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

    @Configuration
    class TestConfiguration {
        @Bean
        fun msgIdx(t: MessageIndexService<UUID, String>): MessageIndexService<UUID, String> = t

        @Bean
        fun msgPersist(t: MessagePersistence<UUID, String>): MessagePersistence<UUID, String> = t

        @Bean
        fun msging(t: ChatTopicMessagingService<UUID, String>): ChatTopicMessagingService<UUID, String> = t

        @Controller
        class TestTopicMessagingController(messageIdx: MessageIndexService<UUID, String>,
                                           msgPersist: MessagePersistence<UUID, String>,
                                           messaging: ChatTopicMessagingService<UUID, String>) :
                TopicMessagingController<UUID, String>(messageIdx, msgPersist, messaging)
    }
}
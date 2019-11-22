package com.demo.chat.test.controller

import com.demo.chat.ChatMessage
import com.demo.chat.MessageRequest
import com.demo.chat.MessagesRequest
import com.demo.chat.controllers.app.MessageController
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.TextMessagePersistence
import org.assertj.core.api.AssertionsForClassTypes
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
@Import(ConfigurationRSocket::class, RSocketMessagesTests.TestConfiguration::class)
class RSocketMessagesTests : ControllerTestBase() {
    val log = LoggerFactory.getLogger(this::class.simpleName)

    @Autowired
    private lateinit var messagePersistence: TextMessagePersistence

    @Autowired
    private lateinit var topicService: ChatTopicService

    @Autowired
    private lateinit var messageIndex: MessageIndexService

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
                            .hasFieldOrPropertyWithValue("visible", true)
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
                .given(topicService.receiveOn(anyObject()))
                .willReturn(Flux.fromStream(Stream.generate { randomMessage() }.limit(5)))

        BDDMockito
                .given(messageIndex.findBy(anyObject()))
                .willReturn(Flux.fromStream(Stream.generate { randomMessage().key }.limit(5)))

        val receiverFlux = requestor
                .route("message-listen-topic")
                .data(MessagesRequest(UUID.randomUUID()))
                .retrieveFlux(ChatMessage::class.java)

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
                            .hasFieldOrPropertyWithValue("visible", true)
                            .hasFieldOrProperty("key")

                    MatcherAssert
                            .assertThat("Message begins with Hello", it.value,
                                    Matchers.startsWith("Hello"))
                })
                .expectComplete()
                .verify()
    }

    @Configuration
    class TestConfiguration {
        @Controller
        class TestMessageController(
                val messageIdx: MessageIndexService,
                val messagePst: TextMessagePersistence,
                val topicSvc: ChatTopicService) : MessageController(messageIdx, messagePst, topicSvc)
    }
}
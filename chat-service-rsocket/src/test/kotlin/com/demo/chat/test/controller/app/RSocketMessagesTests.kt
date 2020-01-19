package com.demo.chat.test.controller.app

import com.demo.chat.ChatMessage
import com.demo.chat.MessageRequest
import com.demo.chat.MessagesRequest
import com.demo.chat.controller.app.MessageController
import com.demo.chat.domain.Message
import com.demo.chat.service.*
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
@Import(TestConfigurationRSocket::class, RSocketMessagesTests.TestConfiguration::class)
class RSocketMessagesTests : ControllerTestBase() {
    val log = LoggerFactory.getLogger(this::class.simpleName)

    @Autowired
    private lateinit var messagePersistence: MessagePersistence<UUID, out Any>

    @Autowired
    private lateinit var topicMessaging: ChatTopicMessagingService<UUID, out Any>

    @Autowired
    private lateinit var messageIndex: IndexService<UUID, Message<UUID, Any>, Map<String, UUID>>//MessageIndexService<UUID>

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
                .given(topicMessaging.receiveOn(anyObject()))
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
                            .assertThat("Message begins with Hello", it.data,
                                    Matchers.startsWith("Hello"))
                })
                .expectComplete()
                .verify()
    }

    @Configuration
    class TestConfiguration {
        @Controller
        class TestMessageController(messageIdx: IndexService<UUID, Message<UUID, Any>, Map<String, UUID>>,
                                    messagePst: MessagePersistence<UUID, Any>,
                                    topicSvc: ChatTopicMessagingService<UUID, Any>) : MessageController<UUID, Any>(messageIdx, messagePst, topicSvc)
    }
}
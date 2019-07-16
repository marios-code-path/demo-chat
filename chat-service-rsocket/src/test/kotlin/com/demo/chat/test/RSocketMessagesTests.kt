package com.demo.chat.test

import com.demo.chat.*
import com.demo.chat.domain.EventKey
import com.demo.chat.domain.Message
import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.TextMessageKey
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.KeyService
import com.demo.chat.service.TextMessagePersistence
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.deser.std.UUIDDeserializer
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.rsocket.RSocket
import io.rsocket.transport.netty.client.TcpClientTransport
import org.assertj.core.api.AssertionsForClassTypes
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer
import org.springframework.boot.rsocket.server.RSocketServerBootstrap
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*
import java.util.stream.Stream

@SpringBootTest(classes = [ChatServiceRsocketApplication::class])
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(RSocketTestConfig::class)
class RSocketMessagesTests {
    val log = LoggerFactory.getLogger(this::class.simpleName)

    private lateinit var socket: RSocket
    private lateinit var requestor: RSocketRequester

    @Autowired
    private lateinit var builder: RSocketRequester.Builder

    @Autowired
    private lateinit var messagePersistence: TextMessagePersistence<out TextMessage, TextMessageKey>

    @Autowired
    private lateinit var rsboot: RSocketServerBootstrap

    @Autowired
    private lateinit var keyService: KeyService

    @Autowired
    private lateinit var topicService: ChatTopicService

    private var counter = Random().nextInt()

    private fun randomMessage(): TextMessage {

        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        counter++

        val msg = TextMessage.create(messageId, roomId, userId, "Hello $counter !")
        //return TestTextMessage(TestTextMessageKey(msg.key.msgId, msg.key.userId, msg.key.topicId, msg.key.timestamp), msg.value, msg.visible)
        return msg
    }

    @BeforeEach
    fun setUp(@Autowired config: RSocketTestConfig) {
        config.rSocketInit()

        requestor = builder.connect(TcpClientTransport.create(7070))
                .block()!!
        socket = requestor.rsocket()

        BDDMockito
                .given(keyService.id())
                .willReturn(Mono.just(EventKey.create(UUID.randomUUID())))

        Hooks.onOperatorDebug()
    }

    @AfterEach
    fun tearDown(@Autowired config: RSocketTestConfig) {
        config.rSocketComplete()
    }

    @Test
    fun `should fetch a single message`() {
        BDDMockito
                .given(messagePersistence.getById(anyObject()))
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
                .given(messagePersistence.getAll(anyObject()))
                .willReturn(Flux.fromStream(Stream.generate { randomMessage() }.limit(5)))

        BDDMockito
                .given(topicService.receiveOn(anyObject()))
                .willReturn(Flux.fromStream(Stream.generate { randomMessage() }.limit(5)))

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

}
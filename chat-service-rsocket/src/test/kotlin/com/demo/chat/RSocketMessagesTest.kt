package com.demo.chat

import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.TextMessage
import com.demo.chat.service.ChatMessageService
import io.rsocket.RSocket
import io.rsocket.transport.netty.client.TcpClientTransport
import org.assertj.core.api.AssertionsForClassTypes
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.rsocket.server.RSocketServerBootstrap
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*
import java.util.function.Predicate
import java.util.stream.Stream

@SpringBootTest(classes = [ChatServiceRsocketApplication::class])
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestSetupConfig::class)
class RSocketMessagesTest {
    val log = LoggerFactory.getLogger(this::class.simpleName)

    private lateinit var socket: RSocket
    private lateinit var requestor: RSocketRequester

    @Autowired
    private lateinit var builder: RSocketRequester.Builder

    @Autowired
    private lateinit var rsboot: RSocketServerBootstrap

    @Autowired
    private lateinit var messageService: ChatMessageService<TextMessage, MessageKey>

    private var counter = Random().nextInt()

    private fun randomMessage(): TestTextMessage {

        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        counter++

        return TestTextMessage(TestTextMessageKey(
                messageId, userId, roomId, Instant.now()
        ), "Hello $counter !", true)
    }

    @BeforeEach
    fun setUp() {
        when(rsboot.isRunning) {
            false -> {
                log.warn("RSocket Service is not already running");
                rsboot.start()
            }
            else -> log.warn("RSocket Service is already running")
        }

        requestor = builder.connect(TcpClientTransport.create(7070)).block()!!
        socket = requestor.rsocket()
    }

    @AfterEach
    fun tearDown() {
        log.info("SHUTTING DOWN")
        log.warn("is rsBoot Active ${rsboot.isRunning}")
        rsboot
                .stop {
                    log.warn("now is rsBoot Active? ${rsboot.isRunning}")
                }
    }

    @Test
    fun `should fetch a single message`() {
        BDDMockito
                .given(messageService.getMessage(anyObject()))
                .willReturn(Mono.just(randomMessage()))

        StepVerifier
                .create(
                        requestor
                                .route("message-id")
                                .data(MessageRequest(UUID.randomUUID()))
                                .retrieveMono(TestTextMessage::class.java)
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
                .given(messageService.getTopicMessages(anyObject()))
                .willReturn(Flux.fromStream(Stream.generate { randomMessage() }.limit(5)))

        StepVerifier
                .create(
                        requestor
                                .route("message-list-topic")
                                .data(MessagesRequest(UUID.randomUUID()))
                                .retrieveMono(TestTextMessage::class.java)
                )
                .expectSubscription()
                .thenConsumeWhile( {
                    it!=null
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

                } )
                .expectComplete()
                .verify()
    }

}
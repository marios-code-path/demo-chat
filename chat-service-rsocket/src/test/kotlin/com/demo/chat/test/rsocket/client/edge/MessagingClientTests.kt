package com.demo.chat.test.rsocket.client.edge

import com.demo.chat.ByIdRequest
import com.demo.chat.client.rsocket.edge.MessagingClient
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.MessagePersistence
import com.demo.chat.service.PubSubService
import com.demo.chat.test.rsocket.controller.edge.EdgeMessagingControllerTests
import com.demo.chat.test.rsocket.controller.edge.MockCoreServicesConfiguration
import com.demo.chat.test.rsocket.controller.edge.anyObject
import com.demo.chat.test.rsocket.controller.core.RSocketTestBase
import org.assertj.core.api.AssertionsForClassTypes
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*
import java.util.stream.Stream

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(MockCoreServicesConfiguration::class,
        EdgeMessagingControllerTests.EdgeMessagingTestConfiguration::class
)
class MessagingClientTests : RSocketTestBase() {
    @Autowired
    private lateinit var messagePersistence: MessagePersistence<UUID, String>

    @Autowired
    private lateinit var topicMessaging: PubSubService<UUID, String>

    @Autowired
    private lateinit var messageIndex: MessageIndexService<UUID, String, Map<String, String>>

    private val svcPrefix = ""

    @Test
    fun `should fetch a single message`() {
        BDDMockito
                .given(messagePersistence.get(anyObject()))
                .willReturn(Mono.just(randomMessage()))

        val client = MessagingClient<UUID, String>(svcPrefix, requester)

        StepVerifier
                .create(
                        client.messageById(ByIdRequest(UUID.randomUUID()))
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

        val client = MessagingClient<UUID, String>(svcPrefix, requester)

        StepVerifier
                .create(client.listenTopic(ByIdRequest(UUID.randomUUID())))
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


    private var counter = Random().nextInt()

    fun randomMessage(): Message<UUID, String> {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        counter++

        return Message.create(MessageKey.create(messageId, roomId, userId), "Hello $counter !", true)
    }
}
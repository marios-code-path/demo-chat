package com.demo.chat.test.rsocket.client.composite

import com.demo.chat.client.rsocket.clients.composite.MessagingClient
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.test.TestBase
import com.demo.chat.test.rsocket.RSocketTestBase
import com.demo.chat.test.rsocket.controller.composite.MessageControllerTests
import com.demo.chat.test.rsocket.controller.composite.MockCoreServicesConfiguration
import org.assertj.core.api.AssertionsForClassTypes
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringJUnitConfig(
    classes = [
        MessageControllerTests.CompositeMessagingTestConfiguration::class,
        MockCoreServicesConfiguration::class,
    ]
)
class MessagClientTests : RSocketTestBase() {
    @Autowired
    private lateinit var messagePersistence: MessagePersistence<UUID, String>

    @Autowired
    private lateinit var topicMessaging: TopicPubSubService<UUID, String>

    @Autowired
    private lateinit var messageIndex: MessageIndexService<UUID, String, Map<String, String>>

    private val svcPrefix = ""

    @Test
    fun `should fetch a single message`() {
        BDDMockito
                .given(messagePersistence.get(TestBase.anyObject()))
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
                .given(messagePersistence.byIds(TestBase.anyObject()))
                .willReturn(Flux.fromStream(Stream.generate { randomMessage() }.limit(5)))

        BDDMockito
                .given(topicMessaging.listenTo(TestBase.anyObject()))
                .willReturn(Flux.fromStream(Stream.generate { randomMessage() }.limit(5)))

        BDDMockito
                .given(messageIndex.findBy(TestBase.anyObject()))
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
package com.demo.chat.test.messaging

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.ChatTopicMessagingService
import com.demo.chat.service.IKeyService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import java.util.function.Supplier

@Disabled
open class MessagingServiceTestBase<T, V>(val topicService: ChatTopicMessagingService<T, V>,
                                          val keySvc: IKeyService<T>,
                                          val valueSupply: Supplier<V>) {

    fun keyFlux() = Flux.merge(
            keySvc.key(String::class.java),
            keySvc.key(String::class.java),
            keySvc.key(String::class.java))

    @Test
    fun `cannot subscribe to topic not created`() {
        val steps = keyFlux()
                .collectList()
                .flatMap { keys ->
                    topicService.subscribe(keys[0].id, keys[1].id)
                }

        StepVerifier
                .create(steps)
                .verifyError()
    }

    @Test
    fun `create && join && unsubscribe && join && list user in topic`() {
        val steps = keyFlux()
                .collectList()
                .flatMapMany { keys ->
                    val userId = keys[0].id
                    val testRoom = keys[1].id

                    topicService.add(testRoom)
                            .then(topicService.subscribe(userId, testRoom))
                            .then(topicService.unSubscribe(userId, testRoom))
                            .then(topicService.subscribe(userId, testRoom))
                            .thenMany(topicService.getUsersBy(testRoom))
                }

        StepVerifier
                .create(steps)
                .expectSubscription()
                .assertNext { userId ->
                    Assertions
                            .assertThat(userId)
                            .isNotNull
                }
                .expectComplete()
                .verify(Duration.ofSeconds(2))
    }

    @Test
    fun `create && join && list member in topic`() {
        val steps = keyFlux()
                .collectList()
                .flatMapMany { keys ->
                    val userId = keys[0].id
                    val testRoom = keys[1].id
                    topicService
                            .add(testRoom)
                            .then(topicService.subscribe(userId, testRoom))
                            .thenMany(topicService.getUsersBy(testRoom))
                }

        StepVerifier
                .create(steps)
                .expectSubscription()
                .assertNext { id ->
                    Assertions
                            .assertThat(id)
                            .isNotNull
                }
                .verifyComplete()
    }

    @Test
    fun `cannot send a message to non existent topic`() {
        val steps = keyFlux()
                .collectList()
                .flatMap { keys ->
                    val userId = keys[0].id
                    val testRoom = keys[1].id
                    val msgId = keys[2].id

                    topicService.sendMessage(Message.create(MessageKey.create(msgId, userId, testRoom), valueSupply.get(), true))
                }

        StepVerifier
                .create(steps)
                .verifyError()
    }

    @Test
    fun `cannot subscribe to non existent topic `() {
        val steps = keyFlux()
                .collectList()
                .flatMap { keys ->
                    val userId = keys[0].id
                    val testRoom = keys[1].id
                    val msgId = keys[2].id

                    topicService.subscribe(userId, testRoom)
                            .flatMap {
                                topicService
                                        .sendMessage(Message.create(MessageKey.create(msgId, userId, testRoom), valueSupply.get(), true))
                            }
                }

        StepVerifier
                .create(steps)
                .verifyError()
    }

    @Test
    fun `create && exists Topic`() {
        val steps = keyFlux()
                .collectList()
                .flatMapMany { keys ->
                    val testRoom = keys[1].id

                    topicService
                            .add(testRoom)
                            .then(topicService.exists(testRoom))
                }
        StepVerifier
                .create(steps)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .`as`("topic created, exists")
                            .isTrue()
                }
                .verifyComplete()
    }


    @Test
    fun `create && join && sendMessage && listen for message`() {
        val steps = keyFlux()
                .collectList()
                .flatMapMany { keys ->
                    val userId = keys[0].id
                    val testRoom = keys[1].id
                    val msgId = keys[2].id

                    val listener = topicService.receiveOn(testRoom)
                    topicService
                            .add(testRoom)
                            .then(topicService.subscribe(userId, testRoom))
                            .then(topicService.sendMessage(Message.create(MessageKey.create(msgId, userId, testRoom), valueSupply.get(), true)))
                            .map {
                                StepVerifier
                                        .create(listener)
                                        .assertNext { msg ->
                                            Assertions
                                                    .assertThat(msg)
                                                    .isNotNull
                                                    .hasNoNullFieldsOrProperties()
                                        }
                                        .verifyComplete()
                            }
                }

        StepVerifier
                .create(steps)
                .expectSubscription()
                .expectComplete()
                .verify(Duration.ofSeconds(2))
    }
}
package com.demo.chat.test.messaging

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.TopicPubSubService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import java.util.function.Supplier

abstract class PubSubTests<T : Any, V>(
    val messaging: TopicPubSubService<T, V>,
    val keySvc: IKeyService<T>,
    val valueSupply: Supplier<V>,
) {

    /** The keys of a user, a room and a message, in that order. */
    fun keyFlux() = Flux.merge(
        keySvc.key(ChatDomain.USER),
        keySvc.key(ChatDomain.MESSAGE_TOPIC),
        keySvc.key(ChatDomain.MESSAGE)
    )

    @Test
    fun `cannot subscribe to topic not created`() {
        val steps = keyFlux()
            .collectList()
            .flatMap { keys ->
                messaging.subscribe(keys[0].id, keys[1].id)
            }

        StepVerifier
            .create(steps)
            .verifyError(ChatException::class.java)
    }

    @Test
    fun `create && join && unsubscribe && join && list user in topic`() {
        val steps = keyFlux()
            .collectList()
            .flatMapMany { keys ->
                val userId = keys[0].id
                val testRoom = keys[1].id

                messaging.open(testRoom)
                    .then(messaging.subscribe(userId, testRoom))
                    .then(messaging.unSubscribe(userId, testRoom))
                    .then(messaging.subscribe(userId, testRoom))
                    .thenMany(messaging.getUsersBy(testRoom))
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
                messaging
                    .open(testRoom)
                    .then(messaging.subscribe(userId, testRoom))
                    .thenMany(messaging.getUsersBy(testRoom))
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

                messaging.sendMessage(
                    Message.create(
                        TestKeys.message(msgId, userId, testRoom),
                        valueSupply.get(),
                        true
                    )
                )
            }

        StepVerifier
            .create(steps)
            .verifyError(ChatException::class.java)
    }

    @Test
    fun `cannot subscribe to non existent topic `() {
        val steps = keyFlux()
            .collectList()
            .flatMap { keys ->
                val userId = keys[0].id
                val testRoom = keys[1].id
                val msgId = keys[2].id

                messaging.subscribe(userId, testRoom)
                    .flatMap {
                        messaging
                            .sendMessage(
                                Message.create(
                                    TestKeys.message(msgId, userId, testRoom),
                                    valueSupply.get(),
                                    true
                                )
                            )
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

                messaging
                    .open(testRoom)
                    .then(messaging.exists(testRoom))
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


    /**
     * One message reaches a listener of the topic.
     *
     * **This test proved nothing until 2026-09-19.** The verifier sat inside
     * `.map { }` applied to the `Mono<Void>` that `sendMessage` answers. A
     * `Mono<Void>` emits no value, so the mapper never ran, and the outer
     * verifier saw only completion. A println inside it printed 0 times over
     * a full run, and a Kafka receiver pointed at a topic name that does not
     * exist left every test passing.
     *
     * The order matters. The listener subscribes first, and the send runs
     * from `then`, after the subscription exists. `listenTo` answers an
     * endless Flux, so the verifier cancels rather than waiting for a
     * completion that never arrives.
     *
     * See CHAT-mumfjoau.
     */
    @Test
    fun `create && join && sendMessage && listen for message`() {
        val keys = requireNotNull(keyFlux().collectList().block(Duration.ofSeconds(10)))
        val userId = keys[0].id
        val testRoom = keys[1].id
        val msgId = keys[2].id

        messaging
            .open(testRoom)
            .then(messaging.subscribe(userId, testRoom))
            .block(Duration.ofSeconds(10))

        val message = Message.create(
            TestKeys.message(msgId, userId, testRoom),
            valueSupply.get(),
            true
        )

        StepVerifier
            .create(messaging.listenTo(testRoom))
            .then { messaging.sendMessage(message).subscribe() }
            .assertNext { received ->
                Assertions
                    .assertThat(received)
                    .isNotNull
                    .hasNoNullFieldsOrProperties()

                Assertions
                    .assertThat(received.key.dest)
                    .`as`("the message arrives on the topic it was sent to")
                    .isEqualTo(testRoom)
            }
            .thenCancel()
            .verify(Duration.ofSeconds(10))
    }
}

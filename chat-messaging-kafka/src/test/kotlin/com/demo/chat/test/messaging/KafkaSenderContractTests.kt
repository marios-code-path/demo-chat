package com.demo.chat.test.messaging

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.StringUtil
import com.demo.chat.pubsub.kafka.impl.KafkaTopicAdmin
import com.demo.chat.pubsub.kafka.impl.KafkaTopicPubSubService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.sender.KafkaSender
import reactor.kafka.sender.SenderRecord
import reactor.kafka.sender.SenderResult
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The send contract of [KafkaTopicPubSubService], read without a broker.
 *
 * Spring Kafka 4 removes `ReactiveKafkaProducerTemplate`, and this service
 * calls `KafkaSender` directly now. These tests pin the contract that the
 * removed template gave, so a later change cannot move it in silence. See
 * CHAT-hazcatpc.
 *
 * **A broker cannot show most of this.** An empty sender result, a cancelled
 * subscription, and an unsubscribed chain are states that a healthy broker
 * never produces. `KafkaPubSubTests` covers delivery and acknowledgement
 * against an embedded broker. This class covers the seams beside it.
 */
class KafkaSenderContractTests {

    private val destination = "TEST-TOPIC"

    private val message: Message<String, String> =
        Message.create(MessageKey.create("MSG", "FROM", destination), "body", true)

    private fun senderReturning(
        results: Flux<SenderResult<String>>,
        onSend: () -> Unit = {},
    ): KafkaSender<String, Message<String, String>> {
        val sender = mock<KafkaSender<String, Message<String, String>>>()

        given(sender.send(any<Mono<SenderRecord<String, Message<String, String>, String>>>()))
            .willAnswer {
                onSend()
                results
            }

        return sender
    }

    private fun serviceWith(
        sender: KafkaSender<String, Message<String, String>>,
        topicExists: Boolean = true,
    ): KafkaTopicPubSubService<String, String> {
        val admin = mock<KafkaTopicAdmin<String>>()
        given(admin.exists(any())).willReturn(Mono.just(topicExists))

        return KafkaTopicPubSubService(
            sender,
            admin,
            StringUtil(),
            ReceiverOptions.create(emptyMap()),
        )
    }

    /** 1. Completion. One result means the broker took the record. */
    @Test
    fun `completes empty when the sender reports one result`() {
        val sender = senderReturning(Flux.just(mock<SenderResult<String>>()))

        StepVerifier.create(serviceWith(sender).sendMessage(message))
            .verifyComplete()
    }

    /** 2. Failure. An error from the sender reaches the caller unchanged. */
    @Test
    fun `reports the sender error to the caller`() {
        val failure = IllegalStateException("the broker refused the record")
        val sender = senderReturning(Flux.error(failure))

        StepVerifier.create(serviceWith(sender).sendMessage(message))
            .verifyErrorMatches { it === failure }
    }

    /**
     * 3. The empty send. `single` refuses it and `next` would accept it.
     *
     * **This test is the reason the implementation reads `single`.** A sender
     * that emits no result sent nothing. A caller that saw completion would
     * believe a lost message reached the broker.
     */
    @Test
    fun `refuses an empty sender result rather than reporting success`() {
        val sender = senderReturning(Flux.empty())

        StepVerifier.create(serviceWith(sender).sendMessage(message))
            .verifyError(NoSuchElementException::class.java)
    }

    /** 4. Cancellation. A cancelled caller cancels the send. */
    @Test
    fun `cancels the sender when the caller cancels`() {
        val cancelled = AtomicBoolean(false)
        val sender = senderReturning(
            Flux.never<SenderResult<String>>().doOnCancel { cancelled.set(true) }
        )

        StepVerifier.create(serviceWith(sender).sendMessage(message))
            .expectSubscription()
            .expectNoEvent(Duration.ofMillis(100))
            .thenCancel()
            .verify(Duration.ofSeconds(5))

        assertThat(cancelled.get())
            .`as`("the sender receives the cancellation")
            .isTrue()
    }

    /**
     * 5. Demand. Nothing is sent before the caller subscribes.
     *
     * `sendMessage` answers with a cold publisher. A caller that builds the
     * chain and never subscribes must produce no record.
     */
    @Test
    fun `sends nothing before the caller subscribes`() {
        val sends = AtomicInteger()
        val sender = senderReturning(
            Flux.just(mock<SenderResult<String>>()),
            onSend = { sends.incrementAndGet() },
        )

        val pending = serviceWith(sender).sendMessage(message)

        assertThat(sends.get())
            .`as`("no send before a subscription")
            .isZero()

        StepVerifier.create(pending).verifyComplete()

        assertThat(sends.get())
            .`as`("one send for one subscription")
            .isOne()
    }

    /** 6. An absent destination fails before the sender is called. */
    @Test
    fun `never reaches the sender when the topic is absent`() {
        val sends = AtomicInteger()
        val sender = senderReturning(
            Flux.just(mock<SenderResult<String>>()),
            onSend = { sends.incrementAndGet() },
        )

        StepVerifier.create(serviceWith(sender, topicExists = false).sendMessage(message))
            .verifyError()

        assertThat(sends.get())
            .`as`("an absent topic stops the send")
            .isZero()
    }
}

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
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.kafka.receiver.ReceiverOffset
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.receiver.ReceiverRecord
import reactor.kafka.sender.KafkaSender
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The receive contract of [KafkaTopicPubSubService], read without a broker.
 *
 * `open` subscribes to one record stream per topic. It emits each value to a
 * sink, and it acknowledges the offset by hand. These tests pin that order,
 * the termination rule, and the buffer.
 *
 * **A broker cannot show this.** `KafkaReceiver.create` is a static factory,
 * so the service takes a record function instead, and a test supplies its own
 * records. `KafkaPubSubTests` covers delivery against an embedded broker, and
 * it asserts nothing about offsets. See CHAT-hazcatpc.
 */
class KafkaReceiverContractTests {

    private val topic = "TEST-TOPIC"

    private fun awaitCount(counter: AtomicInteger, expected: Int, reason: String) {
        val deadline = System.currentTimeMillis() + 5000

        while (counter.get() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }

        assertThat(counter.get()).`as`(reason).isEqualTo(expected)
    }

    private fun messageOf(body: String): Message<String, String> =
        Message.create(MessageKey.create("MSG-$body", "FROM", topic), body, true)

    private fun recordOf(body: String, offset: ReceiverOffset): ReceiverRecord<String, Message<String, String>> {
        val record = mock<ReceiverRecord<String, Message<String, String>>>()

        given(record.value()).willReturn(messageOf(body))
        given(record.receiverOffset()).willReturn(offset)

        return record
    }

    private fun serviceReceiving(
        records: Flux<ReceiverRecord<String, Message<String, String>>>,
    ): KafkaTopicPubSubService<String, String> {
        val admin = mock<KafkaTopicAdmin<String>>()
        given(admin.create(any())).willReturn(Mono.empty())
        given(admin.exists(any())).willReturn(Mono.just(true))

        return KafkaTopicPubSubService(
            mock<KafkaSender<String, Message<String, String>>>(),
            admin,
            StringUtil(),
            ReceiverOptions.create(emptyMap()),
        ) { records }
    }

    /**
     * 1. Acknowledgement, and its order.
     *
     * The offset is acknowledged after the value reaches the sink. A reversed
     * order would acknowledge a record that no reader ever received.
     */
    @Test
    fun `acknowledges each offset after it emits the value`() {
        val offset = mock<ReceiverOffset>()
        val service = serviceReceiving(Flux.just(recordOf("one", offset)))

        service.open(topic).block(Duration.ofSeconds(5))

        StepVerifier.create(service.listenTo(topic))
            .expectNextMatches { it.data == "one" }
            .thenCancel()
            .verify(Duration.ofSeconds(5))

        verify(offset).acknowledge()
    }

    /**
     * 2. Termination after an error.
     *
     * A failed record stream ends the subscription that `open` created. The
     * source reports that end, so the proof does not depend on a record that
     * the source could never produce.
     *
     * **An earlier version of this test was a tautology.** It built the source
     * with `Flux.concat`, which stops at the error, so the record after the
     * error never existed. The test then proved only that an absent record
     * was not acknowledged. The owner review found it.
     *
     * The acknowledgement rules live in the two tests beside this one.
     */
    @Test
    fun `ends the consumer subscription when the record stream fails`() {
        val failure = IllegalStateException("the broker dropped the subscription")

        val source = Sinks.many().multicast()
            .onBackpressureBuffer<ReceiverRecord<String, Message<String, String>>>()

        val subscriptions = AtomicInteger()
        val endings = AtomicInteger()

        val service = serviceReceiving(
            source.asFlux()
                .doOnSubscribe { subscriptions.incrementAndGet() }
                .doFinally { endings.incrementAndGet() }
        )

        val opened = AtomicBoolean(false)
        service.open(topic)
            .doOnSuccess { opened.set(true) }
            .block(Duration.ofSeconds(5))

        awaitCount(subscriptions, 1, "open subscribes to the record stream once")

        assertThat(endings.get())
            .`as`("the subscription runs before the error")
            .isZero()

        source.tryEmitError(failure)

        awaitCount(endings, 1, "the error ends the consumer subscription")

        assertThat(subscriptions.get())
            .`as`("the consumer does not subscribe again after the error")
            .isOne()

        assertThat(opened.get())
            .`as`("a later stream failure never reaches the open caller")
            .isTrue()

        assertThat(source.tryEmitNext(recordOf("after", mock())))
            .`as`("no record can follow the error")
            .isEqualTo(Sinks.EmitResult.FAIL_TERMINATED)
    }

    /**
     * 3. The buffer holds records that the reader has not requested.
     *
     * The sink buffers on back pressure. A reader that requests one record
     * receives one, and the second waits rather than disappearing.
     */
    @Test
    fun `buffers a record until the reader requests it`() {
        val first = mock<ReceiverOffset>()
        val second = mock<ReceiverOffset>()

        val service = serviceReceiving(
            Flux.just(recordOf("one", first), recordOf("two", second))
        )

        service.open(topic).block(Duration.ofSeconds(5))

        StepVerifier.create(service.listenTo(topic), 1)
            .expectNextMatches { it.data == "one" }
            .expectNoEvent(Duration.ofMillis(200))
            .thenRequest(1)
            .expectNextMatches { it.data == "two" }
            .thenCancel()
            .verify(Duration.ofSeconds(5))
    }

    /** The acknowledgement follows the emission, and a mock records that order. */
    @Test
    fun `emits the value before it acknowledges the offset`() {
        val offset = mock<ReceiverOffset>()
        val record = recordOf("one", offset)
        val service = serviceReceiving(Flux.just(record))

        service.open(topic).block(Duration.ofSeconds(5))

        StepVerifier.create(service.listenTo(topic))
            .expectNextCount(1)
            .thenCancel()
            .verify(Duration.ofSeconds(5))

        inOrder(record, offset) {
            verify(record).value()
            verify(offset).acknowledge()
        }
    }
}

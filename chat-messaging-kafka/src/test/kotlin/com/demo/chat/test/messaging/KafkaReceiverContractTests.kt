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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kafka.receiver.ReceiverOffset
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.receiver.ReceiverRecord
import reactor.kafka.sender.KafkaSender
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

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
     * A failed record stream stops that consumer. The records before the
     * error still reach the reader, and no record after it does. `open`
     * already completed, so the error never reaches its caller.
     */
    @Test
    fun `stops the consumer when the record stream fails`() {
        val offset = mock<ReceiverOffset>()
        val afterFailure = mock<ReceiverOffset>()
        val failure = IllegalStateException("the broker dropped the subscription")

        val records = Flux.concat(
            Flux.just(recordOf("before", offset)),
            Flux.error(failure),
            Flux.just(recordOf("after", afterFailure)),
        )

        val service = serviceReceiving(records)

        val opened = AtomicBoolean(false)
        service.open(topic)
            .doOnSuccess { opened.set(true) }
            .block(Duration.ofSeconds(5))

        assertThat(opened.get())
            .`as`("a later stream failure never reaches the open caller")
            .isTrue()

        StepVerifier.create(service.listenTo(topic))
            .expectNextMatches { it.data == "before" }
            .expectNoEvent(Duration.ofMillis(200))
            .thenCancel()
            .verify(Duration.ofSeconds(5))

        verify(offset).acknowledge()
        verify(afterFailure, never()).acknowledge()
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

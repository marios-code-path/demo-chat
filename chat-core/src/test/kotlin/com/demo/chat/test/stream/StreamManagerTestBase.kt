package com.demo.chat.test.stream

import com.demo.chat.convert.Converter
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.impl.memory.stream.ExampleReactiveStreamManager
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.test.StepVerifier
import java.time.Duration

// TODO Test for resource cleanup!
@Disabled
open class StreamManagerTestBase<K, V>(
    val streamMan: ExampleReactiveStreamManager<K, V>,
    val codec: Converter<Unit, K>,
    val valueCodec: Converter<Unit, V>) {

    @BeforeEach
    fun setUp() {
        Hooks.onOperatorDebug()
    }

    @Test
    fun `should create a stream and flux`() {
        val streamId = codec.convert(Unit)

        streamMan.getSource(streamId)

        Assertions
                .assertThat(streamMan.getSink(streamId))
                .isNotNull
    }

    @Test
    fun `should subscribe to a stream`() {
        val streamId = codec.convert(Unit)

        val subscriber = streamMan.subscribeUpstream(streamId, Flux.empty())

        Assertions
                .assertThat(subscriber)
                .isNotNull
    }

    @Test
    fun `should subscribe to and send data`() {
        val streamId = codec.convert(Unit)

        val dataSource = Flux
                .just(Message
                        .create(MessageKey.create(codec.convert(Unit), streamId, codec.convert(Unit)),
                                valueCodec.convert(Unit), true))

        StepVerifier
                .create(streamMan.getSink(streamId))
                .then {
                    streamMan.subscribeUpstream(streamId, dataSource)
                }
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .`as`("Has state")
                            .isNotNull()
                }
                .then {
                    streamMan.close(streamId)
                }
                .expectComplete()
                .verify(Duration.ofSeconds(2))
    }

    @Test
    fun `should consume stream function succeed when no data is passed`() {
        val streamId = codec.convert(Unit)
        val consumerId = codec.convert(Unit)

        val disposable = streamMan.subscribe(streamId, consumerId)

        Assertions
                .assertThat(disposable)
                .isNotNull

        disposable.dispose()
    }

    @Test
    fun `should consumer receive a subscribed stream messages`() {
        val streamId = codec.convert(Unit)
        val consumerId = codec.convert(Unit)

        val messageId = codec.convert(Unit)
        val fromId = codec.convert(Unit)
        val valueData = valueCodec.convert(Unit)

        StepVerifier
                .create(streamMan.getSink(consumerId))
                .then {
                    streamMan.subscribe(streamId, consumerId)
                    streamMan.getSource(streamId)
                            .onNext(Message.create(
                                    MessageKey.create(messageId, fromId, streamId),
                                    valueData,
                                    false))
                }
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .`as`("Has state")
                            .isNotNull()
                }
                .then {
                    streamMan.close(consumerId)
                    streamMan.close(streamId)
                }
                .expectComplete()
                .verify(Duration.ofSeconds(2L))
    }

    @Test
    fun `should multiple consumers receive a subscribed streams messages`() {
        val streamId = codec.convert(Unit)
        val consumerId = codec.convert(Unit)
        val otherConsumerId = codec.convert(Unit)
        val messageId = codec.convert(Unit)
        val fromId = codec.convert(Unit)
        val valueData = valueCodec.convert(Unit)

        StepVerifier
                .create(Flux.merge(streamMan.getSink(consumerId), streamMan.getSink(otherConsumerId)))
                .then {
                    streamMan.subscribe(streamId, consumerId)
                    streamMan.subscribe(streamId, otherConsumerId)
                    streamMan.getSource(streamId)
                            .onNext(Message.create(
                                    MessageKey.create(messageId, fromId, streamId),
                                    valueData,
                                    false))
                }
                .expectNextCount(2)
                .then {
                    streamMan.close(otherConsumerId)
                    streamMan.close(consumerId)
                    streamMan.close(streamId)
                }
                .expectComplete()
                .verify(Duration.ofSeconds(2L))
    }

    @Test
    fun `disconnected consumers should not receive messages`() {
        val streamId = codec.convert(Unit)
        val consumerId = codec.convert(Unit)
        val otherConsumerId = codec.convert(Unit)
        val messageId = codec.convert(Unit)
        val fromId = codec.convert(Unit)
        val valueData = valueCodec.convert(Unit)
        StepVerifier
                .create(Flux.merge(streamMan.getSink(consumerId), streamMan.getSink(otherConsumerId)))
                .then {
                    streamMan.subscribe(streamId, consumerId)
                    streamMan.subscribe(streamId, otherConsumerId)
                    streamMan.getSource(streamId)
                            .onNext(Message.create(
                                    MessageKey.create(messageId, fromId, streamId),
                                    valueData,
                                    false))
                }
                .expectNextCount(2)
                .then {
                    streamMan.close(consumerId)
                    streamMan.getSource(streamId)
                            .onNext(Message.create(
                                    MessageKey.create(messageId, fromId, streamId),
                                    valueData,
                                    false))
                }
                .expectNextCount(1)
                .then {
                    streamMan.close(otherConsumerId)
                }
                .expectComplete()
                .verify(Duration.ofSeconds(2L))
    }
}
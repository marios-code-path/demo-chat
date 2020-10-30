package com.demo.chat.test.stream

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.impl.stream.ReactiveStreamManager
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.test.StepVerifier
import java.time.Duration
import java.util.*

class ReactiveStreamManagerTests {
    private val streamMan = ReactiveStreamManager<UUID, String>()

    @BeforeEach
    fun setUp() {
        Hooks.onOperatorDebug()
    }

    @Test
    fun `should create a stream and flux`() {
        val streamId = UUID.randomUUID()

        streamMan.getSource(streamId)

        Assertions
                .assertThat(streamMan.getSink(streamId))
                .isNotNull
    }

    @Test
    fun `should subscribe to a stream`() {
        val streamId = UUID.randomUUID()

        val subscriber = streamMan.subscribeUpstream(streamId, Flux.empty())

        Assertions
                .assertThat(subscriber)
                .isNotNull
    }

    @Test
    fun `should subscribe to and send data`() {
        val streamId = UUID.randomUUID()
        val dataSource = Flux
                .just(Message
                        .create(MessageKey.create(UUID.randomUUID(), streamId, UUID.randomUUID()),
                                "TEST", true))

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
        val streamId = UUID.randomUUID()
        val consumerId = UUID.randomUUID()

        val disposable = streamMan.subscribe(streamId, consumerId)

        Assertions
                .assertThat(disposable)
                .isNotNull

        disposable.dispose()
    }

    @Test
    fun `should consumer receive a subscribed stream messages`() {
        val streamId = UUID.randomUUID()
        val consumerId = UUID.randomUUID()

        StepVerifier
                .create(streamMan.getSink(consumerId))
                .then {
                    streamMan.subscribe(streamId, consumerId)
                    streamMan.getSource(streamId)
                            .onNext(Message.create(
                                    MessageKey.create(UUID.randomUUID(), UUID.randomUUID(), streamId),
                                    "TEST",
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
        val streamId = UUID.randomUUID()
        val consumerId = UUID.randomUUID()
        val otherConsumerId = UUID.randomUUID()

        StepVerifier
                .create(Flux.merge(streamMan.getSink(consumerId), streamMan.getSink(otherConsumerId)))
                .then {
                    streamMan.subscribe(streamId, consumerId)
                    streamMan.subscribe(streamId, otherConsumerId)
                    streamMan.getSource(streamId)
                            .onNext(Message.create(
                                    MessageKey.create(UUID.randomUUID(), UUID.randomUUID(), streamId),
                                    "TEST",
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
        val streamId = UUID.randomUUID()
        val consumerId = UUID.randomUUID()
        val otherConsumerId = UUID.randomUUID()

        StepVerifier
                .create(Flux.merge(streamMan.getSink(consumerId), streamMan.getSink(otherConsumerId)))
                .then {
                    streamMan.subscribe(streamId, consumerId)
                    streamMan.subscribe(streamId, otherConsumerId)
                    streamMan.getSource(streamId)
                            .onNext(Message.create(
                                    MessageKey.create(UUID.randomUUID(), UUID.randomUUID(), streamId),
                                    "TEST",
                                    false))
                }
                .expectNextCount(2)
                .then {
                    streamMan.close(consumerId)
                    streamMan.getSource(streamId)
                            .onNext(Message.create(
                                    MessageKey.create(UUID.randomUUID(), UUID.randomUUID(), streamId),
                                    "TEST",
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
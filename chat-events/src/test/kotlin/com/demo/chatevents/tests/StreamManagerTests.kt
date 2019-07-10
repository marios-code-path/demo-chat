package com.demo.chatevents.tests

import com.demo.chat.domain.JoinAlert
import com.demo.chatevents.StreamManager
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.test.StepVerifier
import java.time.Duration
import java.util.*

class StreamManagerTests {
    private val logger = LoggerFactory.getLogger(this::class.simpleName)
    private val streamMan = StreamManager()

    @BeforeEach
    fun setUp() {
        Hooks.onOperatorDebug()
    }

    @Test
    fun `should create a stream and flux`() {
        val streamId = UUID.randomUUID()

        streamMan.getStreamProcessor(streamId)

        Assertions
                .assertThat(streamMan.getStreamFlux(streamId))
                .isNotNull
    }

    @Test
    fun `should subscribe to a stream`() {
        val streamId = UUID.randomUUID()

        val subscriber = streamMan.subscribeTo(streamId, Flux.empty())

        Assertions
                .assertThat(subscriber)
                .isNotNull
    }

    @Test
    fun `should subscribe to and send data`() {
        val streamId = UUID.randomUUID()
        val dataSource = Flux.just(JoinAlert.create(UUID.randomUUID(), streamId, UUID.randomUUID()))

        StepVerifier
                .create(streamMan.getStreamFlux(streamId))
                .then {
                    streamMan.subscribeTo(streamId, dataSource)
                }
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .`as`("Has state")
                            .isNotNull()
                }
                .then {
                    streamMan.closeStream(streamId)
                }
                .expectComplete()
                .verify(Duration.ofSeconds(2))
    }

    @Test
    fun `should consume stream function succeed when no data is passed`() {
        val streamId = UUID.randomUUID()
        val consumerId = UUID.randomUUID()

        val disposable = streamMan.consumeStream(streamId, consumerId)

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
                .create(streamMan.getStreamFlux(consumerId))
                .then {
                    streamMan.consumeStream(streamId, consumerId)
                    streamMan.getStreamProcessor(streamId)
                            .onNext(JoinAlert.create(UUID.randomUUID(), streamId, UUID.randomUUID()))
                }
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .`as`("Has state")
                            .isNotNull()
                }
                .then {
                    streamMan.closeStream(consumerId)
                    streamMan.closeStream(streamId)
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
                .create(Flux.merge(streamMan.getStreamFlux(consumerId), streamMan.getStreamFlux(otherConsumerId)))
                .then {
                    streamMan.consumeStream(streamId, consumerId)
                    streamMan.consumeStream(streamId, otherConsumerId)
                    streamMan.getStreamProcessor(streamId)
                            .onNext(JoinAlert.create(UUID.randomUUID(), streamId, UUID.randomUUID()))
                }
                .expectNextCount(2)
                .then {
                    streamMan.closeStream(otherConsumerId)
                    streamMan.closeStream(consumerId)
                    streamMan.closeStream(streamId)
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
                .create(Flux.merge(streamMan.getStreamFlux(consumerId), streamMan.getStreamFlux(otherConsumerId)))
                .then {
                    streamMan.consumeStream(streamId, consumerId)
                    streamMan.consumeStream(streamId, otherConsumerId)
                    streamMan.getStreamProcessor(streamId)
                            .onNext(JoinAlert.create(UUID.randomUUID(), streamId, UUID.randomUUID()))
                }
                .expectNextCount(2)
                .then {
                    streamMan.closeStream(consumerId)
                    streamMan.getStreamProcessor(streamId)
                            .onNext(JoinAlert.create(UUID.randomUUID(), streamId, UUID.randomUUID()))
                }
                .expectNextCount(1)
                .then {
                    streamMan.closeStream(otherConsumerId)
                }
                .expectComplete()
                .verify(Duration.ofSeconds(2L))
    }
}
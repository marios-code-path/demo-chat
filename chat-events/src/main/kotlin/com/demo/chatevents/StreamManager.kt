package com.demo.chatevents

import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicMessageKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.Disposable
import reactor.core.publisher.DirectProcessor
import reactor.core.publisher.Flux
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class StreamManager {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val streamProcessors: MutableMap<UUID, DirectProcessor<Message<TopicMessageKey, Any>>> = ConcurrentHashMap()

    private val streamFluxes: MutableMap<UUID, Flux<Message<TopicMessageKey, Any>>> = ConcurrentHashMap()

    private val streamConsumers: MutableMap<UUID, MutableMap<UUID, Disposable>> = ConcurrentHashMap()



    // Does not replace existing stream! - call closeStream(uuid) then subscribeTo
    fun subscribeTo(stream: UUID, source: Flux<out Message<TopicMessageKey, Any>>): Disposable =
            source.subscribe {
                getStreamProcessor(stream).onNext(it)
            }

    private fun getMaybeConsumer(source: UUID, consumer: UUID): Optional<Disposable> =
            Optional.of(getStreamConsumers(source))
                    .map { consumerMap ->
                        consumerMap[consumer]
                    }

    // Does not replace. Disconnect, then reconnect when needed
    fun consumeStream(source: UUID, consumer: UUID) =
            getMaybeConsumer(source, consumer)
                    .orElseGet {
                        val disposable = subscribeTo(consumer, getStreamFlux(source))
                        getStreamConsumers(source)[consumer] = disposable

                        disposable
                    }

    fun disconnectFromStream(source: UUID, consumer: UUID) {
        getMaybeConsumer(source, consumer)
                .ifPresent { disposable ->
                    disposable.dispose()
                    getStreamConsumers(source).remove(consumer)
                }
    }

    fun closeStream(stream: UUID): Unit {
        Optional.ofNullable(streamProcessors[stream])
                .map {
                    it.onComplete()
                    streamProcessors.remove(stream)
                    streamFluxes.remove(stream)
                    true
                }
                .orElseGet {
                    false
                }
    }

    private fun getStreamConsumers(stream: UUID): MutableMap<UUID, Disposable> =
            streamConsumers
                    .getOrPut(stream) {
                        ConcurrentHashMap()
                    }

    fun getStreamFlux(stream: UUID): Flux<Message<TopicMessageKey, Any>> =
            streamFluxes
                    .getOrPut(stream) {
                        getStreamProcessor(stream)
                                .onBackpressureBuffer()
                                .publish()
                                .autoConnect()
                    }

    fun getStreamProcessor(stream: UUID): DirectProcessor<Message<TopicMessageKey, Any>> =
            streamProcessors
                    .getOrPut(stream) {
                        val processor = DirectProcessor.create<Message<TopicMessageKey, Any>>()

                        processor
                    }
}
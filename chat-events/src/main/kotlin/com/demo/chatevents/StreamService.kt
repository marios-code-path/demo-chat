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

class StreamService {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    // All [topic]s
    private val streamProcessors: MutableMap<UUID, DirectProcessor<Message<TopicMessageKey, Any>>> = ConcurrentHashMap()

    private val streamFluxes: MutableMap<UUID, Flux<Message<TopicMessageKey, Any>>> = ConcurrentHashMap()

    private val streamConnections: MutableMap<UUID, MutableMap<UUID, Disposable>> = ConcurrentHashMap()

    fun getStreamListeners(stream: UUID): Set<UUID> =
        getStreamConsumers(stream).keys

    private fun getStreamConsumers(stream: UUID): MutableMap<UUID, Disposable> =
            streamConnections
                    .getOrPut(stream) {
                        ConcurrentHashMap()
                    }



    private fun getListeningConsumer(stream: UUID, listener: UUID): Optional<Disposable> =
            Optional.of(getStreamConsumers(stream))
                    .map { consumerMap ->
                        consumerMap[listener]
                    }


    fun closeStream(stream: UUID) {

        val streamProcessor = getStreamProcessor(stream)

        streamProcessors.remove(stream)
        streamFluxes.remove(stream)
        streamConnections.remove(stream)

        streamProcessor.onComplete()
        
    }


    // if replacing existing stream, then reconnect all subscribers
    fun connectStream(streamId: UUID, source: Flux<Message<TopicMessageKey, Any>>): Disposable =
        source.subscribe {
            getStreamProcessor(streamId).onNext(it)
        }

    fun listenToStream(stream: UUID, listener: UUID) {
        getListeningConsumer(stream, listener)
                .ifPresent {
                    if(!it.isDisposed)
                        it.dispose()
                }

        getStreamConsumers(stream)[listener] =
                connectStream(listener, getStreamFlux(listener))

    }

    fun disconnectFromStream(stream: UUID, listener: UUID) {
        getListeningConsumer(stream, listener)
                .ifPresent { disposable ->
                    disposable.dispose()
                }
    }

    fun getStreamFlux(streamId: UUID): Flux<Message<TopicMessageKey, Any>> =
            streamFluxes
                    .getOrPut(streamId) {
                        getStreamProcessor(streamId)
                                .onBackpressureBuffer()
                                .publish()
                                .autoConnect()
                    }

    fun getStreamProcessor(streamId: UUID): DirectProcessor<Message<TopicMessageKey, Any>> =
            streamProcessors
                    .getOrPut(streamId) {
                        val processor = DirectProcessor.create<Message<TopicMessageKey, Any>>()

                        processor
                    }
}
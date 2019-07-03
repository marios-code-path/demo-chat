package com.demo.chatevents

import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicMessageKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.DirectProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

class StreamService {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    // All [topic]s
    private val streamProcessors: MutableMap<UUID, DirectProcessor<Message<TopicMessageKey, Any>>> = HashMap()

    private val streamFluxes: MutableMap<UUID, Flux<Message<TopicMessageKey, Any>>> = HashMap()
    
    fun connectStream(listeningStream: UUID, sourceStream: UUID) {
        streamFluxes
                .getOrPut(sourceStream) {
                    getStreamProcessor(sourceStream)
                            .onBackpressureBuffer()
                            .publish()
                            .autoConnect()
                }.subscribe {
                    getStreamProcessor(listeningStream).onNext(it)
                }
    }


    fun getStreamProcessor(streamId: UUID): DirectProcessor<Message<TopicMessageKey, Any>> =
            streamProcessors
                    .getOrPut(streamId) {
                        val processor = DirectProcessor.create<Message<TopicMessageKey, Any>>()

                        processor
                    }
}
package com.demo.chatevents.topic

import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicMessageKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.Disposable
import reactor.core.publisher.DirectProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxProcessor
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class TopicManager {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val streamProcessors: MutableMap<UUID, FluxProcessor<Message<TopicMessageKey, Any>, Message<TopicMessageKey, Any>>> = ConcurrentHashMap() //DirectProcessor<Message<TopicMessageKey, Any>>> = ConcurrentHashMap()

    private val streamFluxes: MutableMap<UUID, Flux<Message<TopicMessageKey, Any>>> = ConcurrentHashMap()

    private val streamConsumers: MutableMap<UUID, MutableMap<UUID, Disposable>> = ConcurrentHashMap()

    // Does not replace existing stream! - call rem(uuid) then subscribeTopicProcessor
    fun subscribeTopicProcessor(stream: UUID, source: Flux<out Message<TopicMessageKey, Any>>): Disposable =
            source.subscribe {
                getTopicProcessor(stream).onNext(it)
            }


    private fun getMaybeConsumer(source: UUID, consumer: UUID): Optional<Disposable> =
            Optional.of(getTopicConsumers(source))
                    .map { consumerMap ->
                        consumerMap[consumer]
                    }

    // Does not replace. Disconnect, then reconnect when needed
    fun subscribeTopic(source: UUID, consumer: UUID) =
            getMaybeConsumer(source, consumer)
                    .orElseGet {
                        val disposable = subscribeTopicProcessor(consumer, getTopicFlux(source))
                        getTopicConsumers(source)[consumer] = disposable

                        disposable
                    }

    fun quitTopic(source: UUID, consumer: UUID) {
        getMaybeConsumer(source, consumer)
                .ifPresent { disposable ->
                    disposable.dispose()
                    getTopicConsumers(source).remove(consumer)
                }
    }

    fun closeTopic(stream: UUID): Unit {
        if (streamProcessors.containsKey(stream)) {
            streamProcessors[stream]?.onComplete()
            streamProcessors.remove(stream)
            streamFluxes.remove(stream)
        }
    }

    private fun getTopicConsumers(stream: UUID): MutableMap<UUID, Disposable> =
            streamConsumers
                    .getOrPut(stream) {
                        ConcurrentHashMap()
                    }

    fun setTopicProcessor(stream: UUID, proc: FluxProcessor<Message<TopicMessageKey, Any>, Message<TopicMessageKey, Any>>) =
            streamProcessors.put(stream, proc)

    fun getTopicFlux(stream: UUID): Flux<Message<TopicMessageKey, Any>> =
            streamFluxes
                    .getOrPut(stream) {
                        getTopicProcessor(stream)
                                .onBackpressureBuffer()
                                .publish()
                                .autoConnect()
                    }

    fun getTopicProcessor(stream: UUID): FluxProcessor<Message<TopicMessageKey, Any>, Message<TopicMessageKey, Any>> =
            streamProcessors
                    .getOrPut(stream) {
                        val processor = DirectProcessor.create<Message<TopicMessageKey, Any>>()

                        processor
                    }
}
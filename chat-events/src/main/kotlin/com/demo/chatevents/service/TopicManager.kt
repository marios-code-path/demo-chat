package com.demo.chatevents.service

import com.demo.chat.domain.Message
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.Disposable
import reactor.core.publisher.DirectProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxProcessor
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/*
    Hopefully this class contains use-case common to the distribution of a many to many stream
    relationship.
 */
class TopicManager<T, E> {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    // TODO seek the Disposable Swap, and Composite to manage the disposable
    private val streamProcessors: MutableMap<T, FluxProcessor<Message<T, E>, Message<T, E>>> = ConcurrentHashMap() //DirectProcessor<Message<TopicMessageKey, E>>> = ConcurrentHashMap()

    private val streamFluxes: MutableMap<T, Flux<Message<T, E>>> = ConcurrentHashMap()

    private val streamConsumers: MutableMap<T, MutableMap<T, Disposable>> = ConcurrentHashMap()

    // Does not replace existing stream! - call rem(T) then subscribeTopicProcessor
    fun subscribeTopicProcessor(stream: T, source: Flux<out Message<T, E>>): Disposable =
            source.subscribe {
                getTopicProcessor(stream).onNext(it)
            }

    private fun getMaybeConsumer(source: T, consumer: T): Optional<Disposable> =
            Optional.of(getTopicConsumers(source))
                    .map { consumerMap ->
                        consumerMap[consumer]
                    }

    // This can leak sas downstream subscription activity can cause
    // this disposable to not call the dispose method ( and remove ) in this API.
    // Probably fix that by: making Flux operations commute termination to given
    // disposable.
    //
    // Does not replace. Disconnect, then reconnect when needed
    fun subscribeTopic(source: T, consumer: T) =
            getMaybeConsumer(source, consumer)
                    .orElseGet {
                        val disposable = subscribeTopicProcessor(consumer, getTopicFlux(source))
                        getTopicConsumers(source)[consumer] = disposable

                        disposable
                    }

    fun quitTopic(source: T, consumer: T) {
        getMaybeConsumer(source, consumer)
                .ifPresent { disposable ->
                    disposable.dispose()
                    getTopicConsumers(source).remove(consumer)
                }
    }

    fun closeTopic(stream: T) {
        if (streamProcessors.containsKey(stream)) {
            streamProcessors[stream]?.onComplete()
            streamProcessors.remove(stream)
            streamFluxes.remove(stream)
        }
    }

    private fun getTopicConsumers(stream: T): MutableMap<T, Disposable> =
            streamConsumers
                    .getOrPut(stream) {
                        ConcurrentHashMap()
                    }

    fun setTopicProcessor(stream: T, proc: FluxProcessor<Message<T, E>, Message<T, E>>) =
            streamProcessors.put(stream, proc)

    fun getTopicFlux(stream: T): Flux<Message<T, E>> =
            streamFluxes
                    .getOrPut(stream) {
                        getTopicProcessor(stream)
                                .onBackpressureBuffer()
                                .publish()
                                .autoConnect()
                    }

    fun getTopicProcessor(stream: T): FluxProcessor<Message<T, E>, Message<T, E>> =
            streamProcessors
                    .getOrPut(stream) {
                        val processor = DirectProcessor.create<Message<T, E>>()

                        processor
                    }
}
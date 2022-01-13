package com.demo.chat.service.impl.memory.stream

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
    Layer 4 application Pub-Sub based on Reactive concepts.
 */
/**
 * TODO: Deprecate in favour of Integration PubSubChannel fan-out.
 */
class ReactiveStreamManager<T, E> {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    // TODO seek the Disposable Swap, and Composite to manage the disposable
    private val sourceStreams: MutableMap<T, FluxProcessor<Message<T, E>, Message<T, E>>> = ConcurrentHashMap() //DirectProcessor<Message<TopicMessageKey, E>>> = ConcurrentHashMap()

    private val sinkStreams: MutableMap<T, Flux<Message<T, E>>> = ConcurrentHashMap()

    private val consumersByTopicMap: MutableMap<T, MutableMap<T, Disposable>> = ConcurrentHashMap()

    // Does not replace existing stream! - call rem(T) then subscribeTopicProcessor
    fun subscribeUpstream(stream: T, source: Flux<out Message<T, E>>): Disposable =
            source.subscribe {
                getSource(stream).onNext(it)
            }

    private fun getMaybeConsumer(source: T, consumer: T): Optional<Disposable> =
            Optional.of(getSinkConsumers(source))
                    .map { consumerMap ->
                        consumerMap[consumer]
                    }

    // This can leak as downstream subscription activity can cause
    // this disposable to not call the dispose method ( and remove ) in this API.
    // Probably fix that by: making Flux operations commute termination to given
    // disposable.
    //
    // Does not replace. Disconnect, then reconnect when needed
    fun subscribe(source: T, consumer: T): Disposable =
            getMaybeConsumer(source, consumer)
                    .orElseGet {
                        val disposable = getSink(source).subscribe { msg ->
                            getSource(consumer).onNext(msg)
                        }

                        disposable.apply {
                            getSinkConsumers(source)[consumer] = this
                        }
                    }

    fun unsubscribe(source: T, consumer: T) {
        getMaybeConsumer(source, consumer)
                .ifPresent { disposable ->
                    disposable.dispose()
                    getSinkConsumers(source).remove(consumer)
                }
    }

    fun close(stream: T) {
        if (sourceStreams.containsKey(stream)) {
            sourceStreams[stream]?.onComplete()
            sourceStreams.remove(stream)
            sinkStreams.remove(stream)
            // all disposables will onDestruct() on source.onComplete().
        }
    }

    private fun getSinkConsumers(stream: T): MutableMap<T, Disposable> =
            consumersByTopicMap
                    .getOrPut(stream) {
                        ConcurrentHashMap()
                    }

    fun setSource(stream: T, proc: FluxProcessor<Message<T, E>, Message<T, E>>) =
            sourceStreams.put(stream, proc)

    fun getSink(stream: T): Flux<Message<T, E>> =
            sinkStreams
                    .getOrPut(stream) {
                        getSource(stream)
                                .onBackpressureBuffer()
                                .publish()
                                .autoConnect()
                    }

    fun getSource(stream: T): FluxProcessor<Message<T, E>, Message<T, E>> =
            sourceStreams
                    .getOrPut(stream) {
                        val processor = DirectProcessor.create<Message<T, E>>()

                        processor
                    }
}
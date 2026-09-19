package com.demo.chat.pubsub.impl.memory.messaging

import com.demo.chat.convert.Converter
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Message
import com.demo.chat.domain.NotFoundException
import com.demo.chat.service.core.TopicPubSubService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.Limit
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.stream.StreamReceiver
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

data class KeyConfiguration(
        val topicSetKey: String,
        val prefixTopicStream: String,
        val prefixUserToTopicSubs: String,
        val prefixTopicToUserSubs: String
)

@Suppress("DuplicatedCode")
class XStreamTopicPubSubService<T : Any, E>(
    keyConfig: KeyConfiguration,
    private val stringTemplate: ReactiveRedisTemplate<String, String>,
    private val messageTemplate: ReactiveRedisTemplate<String, Message<T, E>>,
    private val stringKeyConverter: Converter<String, out T>,
    private val keyStringConverter: Converter<T, String>
) : TopicPubSubService<T, E> {

    private val replayDepth = 50
    private val logger = LoggerFactory.getLogger(this::class.simpleName)
    private val prefixUserToTopicSubs = keyConfig.prefixUserToTopicSubs
    private val prefixTopicToUserSubs = keyConfig.prefixTopicToUserSubs
    private val topicSetKey = keyConfig.topicSetKey
    private val prefixTopicStream = keyConfig.prefixTopicStream

    private val sinks: MutableMap<T, Sinks.Many<Message<T, E>>> = ConcurrentHashMap()
    private val topicReaders: ConcurrentHashMap<T, Mono<Disposable>> = ConcurrentHashMap()

    private fun topicExistsOrError(topic: T): Mono<Void> = exists(topic)
            .filter {
                it == true
            }
            .switchIfEmpty(Mono.error(NotFoundException))
            .then()

    override fun exists(topic: T): Mono<Boolean> = stringTemplate
            .opsForSet()
            .isMember(topicSetKey, topic.toString())

    // Idempotent
    override fun open(topicId: T): Mono<Void> = stringTemplate
        .opsForSet()
        .add(topicSetKey, topicId.toString())
        .then(sourceOf(topicId))

    override fun subscribe(member: T, topic: T): Mono<Void> =
            topicExistsOrError(topic)
                    .then(
                            stringTemplate
                                    .opsForSet()
                                    .add(prefixTopicToUserSubs + topic.toString(), member.toString())
                                    .handle<Long> { a, sink ->
                                        when (a) {
                                            null -> sink.error(ChatException("Unable to subscribe to stream"))
                                            else -> sink.complete()
                                        }
                                    }
                    )
                    .then(
                            stringTemplate
                                    .opsForSet()
                                    .add(prefixUserToTopicSubs + member.toString(), topic.toString())
                                    .handle<Long> { a, sink ->
                                        when (a) {
                                            null -> sink.error(ChatException("Unable to subscribe to stream"))
                                            else -> sink.complete()
                                        }
                                    }
                    )
                    .thenEmpty {
                        sinks.getOrPut(topic) { Sinks.many().multicast().onBackpressureBuffer() }
                        it.onComplete()
                    }

    override fun unSubscribe(member: T, topic: T): Mono<Void> =
            topicExistsOrError(topic)
                    .then(
                            stringTemplate
                                    .opsForSet()
                                    .remove(prefixUserToTopicSubs + member.toString(), topic.toString())
                                    .handle<Void> { a, sink ->
                                        when (a) {
                                            null -> sink.error(ChatException("Unable to unsubscribe from stream."))
                                            else -> sink.complete()
                                        }
                                    }
                    )
                    .then(
                            stringTemplate
                                    .opsForSet()
                                    .remove(prefixTopicToUserSubs + topic.toString(), member.toString())
                                    .handle<Void> { a, sink ->
                                        when (a) {
                                            null -> sink.error(ChatException("Unable to unsubscribe from stream."))
                                            else -> sink.complete()
                                        }
                                    }
                    )
                    .thenEmpty {
                        sinks[topic]?.let { }
                        it.onComplete()
                    }

    override fun unSubscribeAll(member: T): Mono<Void> =
            stringTemplate
                    .opsForSet()
                    .members(prefixUserToTopicSubs + member.toString())
                    .collectList()
                    .flatMap { topicList ->
                        Flux
                                .fromIterable(topicList)
                                .map { topicId ->
                                    unSubscribe(member, stringKeyConverter.convert(topicId))
                                }
                                .subscribeOn(Schedulers.parallel())
                                .then()
                    }

    override fun unSubscribeAllIn(topic: T): Mono<Void> =
            stringTemplate
                    .opsForSet()
                    .members(prefixTopicToUserSubs + topic.toString())
                    .collectList()
                    .flatMap { members ->
                        Flux
                                .fromIterable(members)
                                .map { member ->
                                    unSubscribe(stringKeyConverter.convert(member), topic)
                                }
                                .subscribeOn(Schedulers.parallel())
                                .then()
                    }

    override fun sendMessage(message: Message<T, E>): Mono<Void> {
        val map = mapOf(Pair("data", message))
        val streamKey = prefixTopicStream + message.key.dest.toString()
        val recordId = RecordId.autoGenerate()
        return Mono.from(topicExistsOrError(message.key.dest))
                .then(messageTemplate
                        .opsForStream<String, Message<T, E>>()
                        .add(MapRecord
                                .create(streamKey, map)
                                .withId(recordId)))
                // XTRIM MAXLEN: cap each topic stream at replayDepth entries so
                // streams do not grow without bound. Exact (not approximate)
                // trimming, because replayDepth is far smaller than a stream node.
                .then(messageTemplate
                        .opsForStream<String, Message<T, E>>()
                        .trim(streamKey, replayDepth.toLong()))
                .then()
    }

    override fun listenTo(topic: T): Flux<out Message<T, E>> =
            sinks.getOrPut(topic) {
                Sinks.many().multicast().onBackpressureBuffer()
            }.asFlux()

    private fun sourceOf(topic: T): Mono<Void> {
        val startup = topicReaders.computeIfAbsent(topic) { startReader(topic).cache() }
        return startup
            .then()
            .onErrorResume { error ->
                topicReaders.remove(topic, startup)
                Mono.error(error)
            }
    }

    private fun startReader(topic: T): Mono<Disposable> {
        val streamKey = prefixTopicStream + topic.toString()
        val context = messageTemplate.serializationContext
        val options: StreamReceiver.StreamReceiverOptions<String, MapRecord<String, String, Message<T, E>>> =
            StreamReceiver.StreamReceiverOptions.builder()
            .keySerializer<String, MapRecord<String, String, Message<T, E>>>(context.keySerializationPair)
            .hashKeySerializer<String, Message<T, E>>(context.getHashKeySerializationPair<String>())
            .hashValueSerializer<String, Message<T, E>>(context.getHashValueSerializationPair<Message<T, E>>())
            .build()
        val sink = sinks.getOrPut(topic) {
            Sinks.many().multicast().onBackpressureBuffer()
        }

        return messageTemplate.opsForStream<String, Message<T, E>>()
            .reverseRange(streamKey, Range.unbounded<String>(), Limit.limit().count(1))
            .next()
            .map { record -> record.id }
            .defaultIfEmpty(RecordId.of("0-0"))
            .map { cursor ->
                StreamReceiver.create<String, MapRecord<String, String, Message<T, E>>>(
                    messageTemplate.connectionFactory,
                    options,
                )
                    .receive(StreamOffset.create(streamKey, ReadOffset.from(cursor)))
                    .map { record -> record.value["data"]!! }
                    .subscribe(
                        { message -> sink.tryEmitNext(message) },
                        { error ->
                            topicReaders.remove(topic)
                            logger.error("The XStream reader for $topic failed", error)
                        },
                    )
            }
    }

    override fun getByUser(uid: T): Flux<T> =
            stringTemplate
                    .opsForSet()
                    .members(
                            prefixUserToTopicSubs + keyStringConverter.convert(uid)
                    )
                    .map {
                        stringKeyConverter.convert(it)
                    }

    override fun getUsersBy(topicId: T): Flux<T> =
            topicExistsOrError(topicId)
                    .thenMany(
                            stringTemplate
                                    .opsForSet()
                                    .members(
                                            prefixTopicToUserSubs + keyStringConverter.convert(topicId)
                                    )
                                    .map {
                                        stringKeyConverter.convert(it)
                                    }
                    )

    override fun close(topicId: T): Mono<Void> {
        val stopReader = topicReaders.remove(topicId)
            ?.doOnNext { reader -> reader.dispose() }
            ?.then()
            ?: Mono.empty()

        return stopReader
            .then(Mono.fromRunnable { sinks.remove(topicId)?.tryEmitComplete() })
            .then(
                messageTemplate.connectionFactory.reactiveConnection.keyCommands()
                    .del(ByteBuffer.wrap(
                        (prefixTopicStream + topicId.toString()).toByteArray(Charset.defaultCharset())
                    ))
            )
            .then()
    }
    // TODO: For multi-instance deployment, add consumer-group-based XREADGROUP
    //  per service instance. Current design uses Sinks.Many for in-process fan-out
    //  and captures the stream tail before each reader starts. Cross-process fan-out
    //  requires consumer groups with unique consumer names per instance.
}

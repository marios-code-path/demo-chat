package com.demo.chat.pubsub.impl.memory.messaging

import com.demo.chat.convert.Converter
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Message
import com.demo.chat.domain.NotFoundException
import com.demo.chat.service.core.TopicPubSubService
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.ReactiveRedisTemplate
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
class TopicPubSubTopicInventoryRedisStream<T, E>(
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
    private val topicXReads: MutableMap<T, Flux<out Message<T, E>>> = ConcurrentHashMap()

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
    override fun open(topicId: T): Mono<Void> =
            stringTemplate
                    .opsForSet()
                    .add(topicSetKey, topicId.toString())
                    .thenEmpty {
                        sourceOf(topicId)
                        it.onComplete()
                    }

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

    // Connect a Redis Stream xread to the Sinks.Many for in-process fan-out
    fun sourceOf(topic: T): Flux<out Message<T, E>> =
            topicXReads.getOrPut(topic, {
                val xread = getXReadFlux(topic)
                val sink = sinks.getOrPut(topic) {
                    Sinks.many().multicast().onBackpressureBuffer()
                }
                xread.doOnNext { msg -> sink.tryEmitNext(msg) }
                xread
            })

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

    override fun close(topicId: T): Mono<Void> = messageTemplate
            .connectionFactory
            .reactiveConnection
            .keyCommands()
            .del(ByteBuffer
                    .wrap((prefixTopicStream + topicId.toString()).toByteArray(Charset.defaultCharset())))
            .doOnNext {
                sinks.remove(topicId)?.tryEmitComplete()
            }.then()

    private fun getXReadFlux(topic: T): Flux<Message<T, E>> =
            messageTemplate
                    .opsForStream<String, Message<T, E>>()
                    .read(StreamOffset.latest(prefixTopicStream + topic.toString()))
                    .map {
                        it.value["data"]!!
                    }.doOnComplete {
                        topicXReads.remove(topic)
                    }
    // TODO: For multi-instance deployment, add consumer-group-based XREADGROUP
    //  per service instance. Current design uses Sinks.Many for in-process fan-out
    //  and StreamOffset.latest for per-listener subscription. Cross-process fan-out
    //  requires consumer groups with unique consumer names per instance.
    // TODO: sourceOf() discards the doOnNext-decorated Flux and returns the
    //  undecorated xread, and nothing subscribes to it, so the Sinks.Many is
    //  never fed from the stream. Fixing this needs a live (blocking or
    //  StreamReceiver-based) read rather than the one-shot XREAD used here.
}
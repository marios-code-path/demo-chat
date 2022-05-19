package com.demo.chat.service.impl.memory.messaging

import com.demo.chat.convert.Encoder
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicNotFoundException
import com.demo.chat.service.PubSubService
import com.demo.chat.service.impl.memory.stream.ExampleReactiveStreamManager
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.ReactiveRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.ReplayProcessor
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
class PubSubTopicExchangeRedisStream<T, E>(
    keyConfig: KeyConfiguration,
    private val stringTemplate: ReactiveRedisTemplate<String, String>,
    private val messageTemplate: ReactiveRedisTemplate<String, Message<T, E>>,
    private val stringKeyEncoder: Encoder<String, out T>,
    private val keyStringEncoder: Encoder<T, String>
) : PubSubService<T, E> {

    private val replayDepth = 50
    private val logger = LoggerFactory.getLogger(this::class.simpleName)
    private val prefixUserToTopicSubs = keyConfig.prefixUserToTopicSubs
    private val prefixTopicToUserSubs = keyConfig.prefixTopicToUserSubs
    private val topicSetKey = keyConfig.topicSetKey
    private val prefixTopicStream = keyConfig.prefixTopicStream

    private val streamManager = ExampleReactiveStreamManager<T, E>()
    private val topicXReads: MutableMap<T, Flux<out Message<T, E>>> = ConcurrentHashMap()

    private fun topicExistsOrError(topic: T): Mono<Void> = exists(topic)
            .filter {
                it == true
            }
            .switchIfEmpty(Mono.error(TopicNotFoundException))
            .then()

    override fun exists(topic: T): Mono<Boolean> = stringTemplate
            .opsForSet()
            .isMember(topicSetKey, topic.toString())

    // Idempotent
    override fun add(id: T): Mono<Void> =
            stringTemplate
                    .opsForSet()
                    .add(topicSetKey, id.toString())
                    .thenEmpty {
                        sourceOf(id)
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
                        streamManager.subscribe(topic, member)
                        it.onComplete()
                    }

    override fun unSubscribe(member: T, topic: T): Mono<Void> =
            topicExistsOrError(topic)
                    .then(
                            stringTemplate    // todo Streams entries too!
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
                            stringTemplate    // todo Streams entries too!
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
                        streamManager
                                .unsubscribe(topic, member)
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
                                    unSubscribe(member, stringKeyEncoder.encode(topicId))
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
                                    unSubscribe(stringKeyEncoder.encode(member), topic)
                                }
                                .subscribeOn(Schedulers.parallel())
                                .then()
                    }

    override fun sendMessage(message: Message<T, E>): Mono<Void> {
        val map = mapOf(Pair("data", message))
        /*
            * <li>1    Time-based T
            * <li>2    DCE security T
            * <li>3    Name-based T
            * <li>4    Randomly generated T
         */
        val recordId = RecordId.autoGenerate()
        return Mono.from(topicExistsOrError(message.key.dest))
                .thenMany(messageTemplate
                        .opsForStream<String, Message<T, E>>()
                        .add(MapRecord
                                .create(prefixTopicStream + message.key.dest.toString(), map)
                                .withId(recordId)))
                .then()
    }

    override fun receiveOn(topic: T): Flux<out Message<T, E>> =
            streamManager.getSink(topic)

    // may need to turn this into a different rturn type ( just start the source using .subscribe() )
    // Connect a Processor to a flux for message ingest ( xread -> processor )
    fun sourceOf(topic: T): Flux<out Message<T, E>> =
            topicXReads.getOrPut(topic, {
                val xread = getXReadFlux(topic)
                val reProc = ReplayProcessor.create<Message<T, E>>(replayDepth)
                streamManager.setSource(topic, reProc)
                streamManager.subscribeUpstream(topic, xread)

                xread
            })

    override fun getByUser(uid: T): Flux<T> =
            stringTemplate
                    .opsForSet()
                    .members(
                            prefixUserToTopicSubs + keyStringEncoder.encode(uid)
                    )
                    .map {
                        stringKeyEncoder.encode(it)
                    }

    override fun getUsersBy(id: T): Flux<T> =
            topicExistsOrError(id)
                    .thenMany(
                            stringTemplate
                                    .opsForSet()
                                    .members(
                                            prefixTopicToUserSubs + keyStringEncoder.encode(id)
                                    )
                                    .map {
                                        stringKeyEncoder.encode(it)
                                    }
                    )

    override fun rem(id: T): Mono<Void> = messageTemplate
            .connectionFactory
            .reactiveConnection
            .keyCommands()
            .del(ByteBuffer
                    .wrap((prefixTopicStream + id.toString()).toByteArray(Charset.defaultCharset())))
            .doOnNext {
                streamManager
                        .close(id)
            }.then()

    private fun getXReadFlux(topic: T): Flux<Message<T, E>> =
            messageTemplate
                    .opsForStream<String, Message<T, E>>()
                    .read(StreamOffset.fromStart(prefixTopicStream + topic.toString()))
                    .map {
                        it.value["data"]!!
                    }.doOnComplete {
                        topicXReads.remove(topic)
                    }
    // TODO Strategy needed to manage consumer groups and synchronous looping of Xreads
}
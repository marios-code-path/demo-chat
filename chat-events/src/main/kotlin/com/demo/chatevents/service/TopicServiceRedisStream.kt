package com.demo.chatevents.service

import com.demo.chat.codec.Codec
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicNotFoundException
import com.demo.chat.service.ChatTopicService
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.ReactiveRedisTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxProcessor
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

class TopicServiceRedisStream<T, E>(
        keyConfig: KeyConfiguration,
        private val stringTemplate: ReactiveRedisTemplate<String, String>,
        private val messageTemplate: ReactiveRedisTemplate<String, Message<T, E>>,
        val stringKeyEncoder: Codec<String, out T>,
        val keyStringEncoder: Codec<T, String>,
        val keyRecordIdCodec: Codec<T, RecordId>
) : ChatTopicService<T, E> {

    private val logger = LoggerFactory.getLogger(this::class.simpleName)
    private val prefixUserToTopicSubs = keyConfig.prefixUserToTopicSubs
    private val prefixTopicToUserSubs = keyConfig.prefixTopicToUserSubs
    private val topicSetKey = keyConfig.topicSetKey
    private val prefixTopicStream = keyConfig.prefixTopicStream

    private val streamManager = TopicManager<T, E>()
    private val topicXReads: MutableMap<T, Flux<out Message<T, E>>> = ConcurrentHashMap()

    private fun topicExistsOrError(topic: T): Mono<Void> = exists(topic)
            .filter {
                it == true
            }
            .switchIfEmpty(Mono.error(TopicNotFoundException))
            .then()

    override fun exists(id: T): Mono<Boolean> = stringTemplate
            .opsForSet()
            .isMember(topicSetKey, id.toString())

    // Idempotent
    override fun add(id: T): Mono<Void> =
            stringTemplate
                    .opsForSet()
                    .add(topicSetKey, id.toString())
                    .thenEmpty {
                        receiveSourcedEvents(id)
                        it.onComplete()
                    }

    override fun subscribe(member: T, id: T): Mono<Void> =
            topicExistsOrError(id)
                    .then(
                            stringTemplate
                                    .opsForSet()
                                    .add(prefixTopicToUserSubs + id.toString(), member.toString())
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
                                    .add(prefixUserToTopicSubs + member.toString(), id.toString())
                                    .handle<Long> { a, sink ->
                                        when (a) {
                                            null -> sink.error(ChatException("Unable to subscribe to stream"))
                                            else -> sink.complete()
                                        }
                                    }
                    )
                    .thenEmpty {
                        streamManager.subscribeTopic(id, member)
                        it.onComplete()
                    }

    override fun unSubscribe(member: T, id: T): Mono<Void> =
            topicExistsOrError(id)
                    .then(
                            stringTemplate    // todo Streams entries too!
                                    .opsForSet()
                                    .remove(prefixUserToTopicSubs + member.toString(), id.toString())
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
                                    .remove(prefixTopicToUserSubs + id.toString(), member.toString())
                                    .handle<Void> { a, sink ->
                                        when (a) {
                                            null -> sink.error(ChatException("Unable to unsubscribe from stream."))
                                            else -> sink.complete()
                                        }
                                    }
                    )
                    .thenEmpty {
                        streamManager
                                .quitTopic(id, member)
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
                                    unSubscribe(member, stringKeyEncoder.decode(topicId))
                                }
                                .subscribeOn(Schedulers.parallel())
                                .then()
                    }

    override fun unSubscribeAllIn(id: T): Mono<Void> =
            stringTemplate
                    .opsForSet()
                    .members(prefixTopicToUserSubs + id.toString())
                    .collectList()
                    .flatMap { members ->
                        Flux
                                .fromIterable(members)
                                .map { member ->
                                    unSubscribe(stringKeyEncoder.decode(member), id)
                                }
                                .subscribeOn(Schedulers.parallel())
                                .then()
                    }

    override fun sendMessage(topicMessage: Message<T, E>): Mono<Void> {
        val map = mapOf(Pair("data", topicMessage))
        /*
            * <li>1    Time-based T
            * <li>2    DCE security T
            * <li>3    Name-based T
            * <li>4    Randomly generated T
         */
        val recordId = keyRecordIdCodec.decode(topicMessage.key.id)

        return Mono.from(topicExistsOrError(topicMessage.key.dest))
                .thenMany(messageTemplate
                        .opsForStream<String, Message<T, E>>()
                        .add(MapRecord
                                .create(prefixTopicStream + topicMessage.key.dest.toString(), map)
                                .withId(recordId)))
                .then()
    }

    override fun receiveOn(streamId: T): Flux<out Message<T, E>> =
            streamManager.getTopicFlux(streamId)

    // may need to turn this into a different rturn type ( just start the source using .subscribe() )
    // Connect a Processor to a flux for message ingest ( xread -> processor )
    override fun receiveSourcedEvents(id: T): Flux<out Message<T, E>> =
            topicXReads.getOrPut(id, {
                val xread = getXReadFlux(id)
                val reProc = ReplayProcessor.create<Message<T, E>>(5)
                streamManager.setTopicProcessor(id, reProc)
                streamManager.subscribeTopicProcessor(id, xread)

                xread
            })

    override fun getByUser(uid: T): Flux<T> =
            stringTemplate
                    .opsForSet()
                    .members(
                            prefixUserToTopicSubs + keyStringEncoder.decode(uid)
                    )
                    .map {
                        stringKeyEncoder.decode(it)
                    }

    override fun getUsersBy(id: T): Flux<T> =
            topicExistsOrError(id)
                    .thenMany(
                            stringTemplate
                                    .opsForSet()
                                    .members(
                                            prefixTopicToUserSubs + keyStringEncoder.decode(id)
                                    )
                                    .map {
                                        stringKeyEncoder.decode(it)
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
                        .closeTopic(id)
            }.then()

    override fun getProcessor(id: T): FluxProcessor<out Message<T, E>, out Message<T, E>> =
            streamManager.getTopicProcessor(id)

    private fun keyExists(key: String, errorThrow: Throwable): Mono<Boolean> = stringTemplate
            .connectionFactory.reactiveConnection
            .keyCommands()
            .exists(ByteBuffer
                    .wrap((key)
                            .toByteArray(Charset.defaultCharset())))
            .filter {
                it == true
            }
            .switchIfEmpty(Mono.error(errorThrow))

    private fun getXReadFlux(topic: T): Flux<Message<T, E>> =
            messageTemplate
                    .opsForStream<String, Message<T, E>>()
                    .read(StreamOffset.fromStart(prefixTopicStream + topic.toString()))
                    .map {
                        it.value["data"]!!
                    }.doOnNext {
                        logger.info("XREAD: ${keyStringEncoder.decode(it.key.dest)}")
                    }.doOnComplete {
                        topicXReads.remove(topic)
                    }
    // TODO Strategy needed to manage consumer groups and synchronous looping of Xreads


}
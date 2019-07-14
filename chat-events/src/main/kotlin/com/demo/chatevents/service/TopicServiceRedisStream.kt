package com.demo.chatevents.service

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Message
import com.demo.chat.domain.RoomNotFoundException
import com.demo.chat.domain.TopicMessageKey
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.ChatTopicServiceAdmin
import com.demo.chatevents.topic.TopicManager
import com.demo.chatevents.topic.TopicData
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
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class KeyConfiguration(
        val topicSetKey: String,
        val prefixTopicStream: String,
        val prefixUserToTopicSubs: String,
        val prefixTopicToUserSubs: String
)

class TopicServiceRedisStream(
        keyConfig: KeyConfiguration,
        private val stringTemplate: ReactiveRedisTemplate<String, String>,
        private val messageTemplate: ReactiveRedisTemplate<String, TopicData>
) : ChatTopicService, ChatTopicServiceAdmin {

    private val logger = LoggerFactory.getLogger(this::class.simpleName)
    private val prefixUserToTopicSubs = keyConfig.prefixUserToTopicSubs
    private val prefixTopicToUserSubs = keyConfig.prefixTopicToUserSubs
    private val topicSetKey = keyConfig.topicSetKey
    private val prefixTopicStream = keyConfig.prefixTopicStream

    private val streamManager = TopicManager()
    private val topicXReads: MutableMap<UUID, Flux<out Message<TopicMessageKey, Any>>> = ConcurrentHashMap()

    private fun topicExistsOrError(topic: UUID): Mono<Void> = exists(topic)
            .filter {
                it == true
            }
            .switchIfEmpty(Mono.error(RoomNotFoundException))
            .then()

    override fun exists(id: UUID): Mono<Boolean> = stringTemplate
            .opsForSet()
            .isMember(topicSetKey, id.toString())

    // Idempotent
    override fun add(id: UUID): Mono<Void> =
            stringTemplate
                    .opsForSet()
                    .add(topicSetKey, id.toString())
                    .thenEmpty {
                        receiveSourcedEvents(id)
                        it.onComplete()
                    }

    override fun subscribe(member: UUID, id: UUID): Mono<Void> =
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

    override fun unSubscribe(member: UUID, id: UUID): Mono<Void> =
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

    override fun unSubscribeAll(member: UUID): Mono<Void> =
            stringTemplate
                    .opsForSet()
                    .members(prefixUserToTopicSubs + member.toString())
                    .collectList()
                    .flatMap { topicList ->
                        Flux
                                .fromIterable(topicList)
                                .map { topicId ->
                                    unSubscribe(member, UUID.fromString(topicId))
                                }
                                .subscribeOn(Schedulers.parallel())
                                .then()
                    }

    override fun unSubscribeAllIn(id: UUID): Mono<Void> =
            stringTemplate
                    .opsForSet()
                    .members(prefixTopicToUserSubs + id.toString())
                    .collectList()
                    .flatMap { members ->
                        Flux
                                .fromIterable(members)
                                .map { member ->
                                    unSubscribe(UUID.fromString(member), id)
                                }
                                .subscribeOn(Schedulers.parallel())
                                .then()
                    }

    override fun sendMessage(topicMessage: Message<TopicMessageKey, Any>): Mono<Void> {
        val map = mapOf(Pair("data", TopicData(topicMessage)))
        /*
            * <li>1    Time-based UUID
            * <li>2    DCE security UUID
            * <li>3    Name-based UUID
            * <li>4    Randomly generated UUID
         */
        val recordId = when (topicMessage.key.msgId.version()) {
            1 -> RecordId.of(topicMessage.key.msgId.timestamp(), topicMessage.key.msgId.clockSequence().toLong())
            else -> RecordId.autoGenerate()
        }

        return Mono.from(topicExistsOrError(topicMessage.key.topicId))
                .thenMany(messageTemplate
                        .opsForStream<String, TopicData>()
                        .add(MapRecord
                                .create(prefixTopicStream + topicMessage.key.topicId.toString(), map)
                                .withId(recordId)))
                .then()
    }

    override fun receiveOn(streamId: UUID): Flux<out Message<TopicMessageKey, Any>> =
            streamManager.getTopicFlux(streamId)

    // may need to turn this into a different rturn type ( just start the source using .subscribe() )
    // Connect a Processor to a flux for message ingest ( xread -> processor )
    override fun receiveSourcedEvents(id: UUID): Flux<out Message<TopicMessageKey, Any>> =
            topicXReads.getOrPut(id, {
                val xread = getXReadFlux(id)
                val reProc = ReplayProcessor.create<Message<TopicMessageKey, Any>>(5)
                streamManager.setTopicProcessor(id, reProc)
                streamManager.subscribeTopicProcessor(id, xread)

                xread
            })

    override fun getTopicsByUser(uid: UUID): Flux<UUID> =
            stringTemplate
                    .opsForSet()
                    .members(
                            prefixUserToTopicSubs + uid.toString()
                    )
                    .map(UUID::fromString)

    override fun getUsersBy(id: UUID): Flux<UUID> =
            topicExistsOrError(id)
                    .thenMany(
                            stringTemplate
                                    .opsForSet()
                                    .members(
                                            prefixTopicToUserSubs + id.toString()
                                    )
                                    .map(UUID::fromString)
                    )

    override fun rem(id: UUID): Mono<Void> = messageTemplate
            .connectionFactory
            .reactiveConnection
            .keyCommands()
            .del(ByteBuffer
                    .wrap((prefixTopicStream + id.toString()).toByteArray(Charset.defaultCharset())))
            .doOnNext {
                streamManager
                        .closeTopic(id)
            }.then()

    override fun getProcessor(id: UUID): FluxProcessor<out Message<TopicMessageKey, Any>, out Message<TopicMessageKey, Any>> =
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

    private fun getXReadFlux(topic: UUID): Flux<out Message<TopicMessageKey, Any>> =
            messageTemplate
                    .opsForStream<String, TopicData>()
                    .read(StreamOffset.fromStart(prefixTopicStream + topic.toString()))
                    .map {
                        it.value["data"]?.state!!
                    }.doOnNext {
                        logger.info("XREAD: ${it.key.topicId}")
                    }.doOnComplete {
                        topicXReads.remove(topic)
                    }
    // TODO Strategy needed to manage consumer groups and synchronous looping of Xreads


}
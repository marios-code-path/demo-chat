package com.demo.chatevents.service

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Message
import com.demo.chat.domain.RoomNotFoundException
import com.demo.chat.domain.TopicMessageKey
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.ChatTopicServiceAdmin
import com.demo.chatevents.topic.TopicData
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxProcessor
import reactor.core.publisher.Mono
import reactor.core.publisher.ReplayProcessor
import reactor.core.scheduler.Schedulers
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class KeyConfigurationPubSub(
        val topicSetKey: String,
        val prefixTopicKey: String,
        val prefixUserToTopicSubs: String,
        val prefixTopicToUserSubs: String
)

class TopicServiceRedisPubSub(
        keyConfig: KeyConfigurationPubSub,
        private val stringTemplate: ReactiveRedisTemplate<String, String>,
        private val messageTemplate: ReactiveRedisTemplate<String, TopicData>
) : ChatTopicService, ChatTopicServiceAdmin {

    private val logger = LoggerFactory.getLogger(this::class.simpleName)
    private val prefixUserToTopicSubs = keyConfig.prefixUserToTopicSubs
    private val prefixTopicToUserSubs = keyConfig.prefixTopicToUserSubs
    private val topicSetKey = keyConfig.topicSetKey
    private val prefixTopicKey = keyConfig.prefixTopicKey

    private val topicManager = TopicManager()
    private val topicXSource: MutableMap<UUID, Flux<out Message<TopicMessageKey, Any>>> = ConcurrentHashMap()

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
                        topicManager.subscribeTopic(id, member)
                        it.onComplete()
                    }

    override fun unSubscribe(member: UUID, id: UUID): Mono<Void> =
            topicExistsOrError(id)
                    .then(
                            stringTemplate
                                    .opsForSet()
                                    .remove(prefixUserToTopicSubs + member.toString(), id.toString())
                                    .handle<Void> { a, sink ->
                                        when (a) {
                                            null -> sink.error(ChatException("Unable to unsubscribe from id."))
                                            else -> sink.complete()
                                        }
                                    }
                    )
                    .then(
                            stringTemplate
                                    .opsForSet()
                                    .remove(prefixTopicToUserSubs + id.toString(), member.toString())
                                    .handle<Void> { a, sink ->
                                        when (a) {
                                            null -> sink.error(ChatException("Unable to unsubscribe from id."))
                                            else -> sink.complete()
                                        }
                                    }
                    )
                    .thenEmpty {
                        topicManager
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
        val topic = topicMessage.key.topicId

        return Mono.from(topicExistsOrError(topic))
                .then(messageTemplate.convertAndSend(topic.toString(), TopicData(topicMessage)))
                .then()
    }

    override fun receiveOn(streamId: UUID): Flux<out Message<TopicMessageKey, Any>> =
            topicManager.getTopicFlux(streamId)

    // may need to turn this into a different rturn type ( just start the source using .subscribe() )
    // Connect a Processor to a flux for message ingest ( xread -> processor )
    override fun receiveSourcedEvents(id: UUID): Flux<out Message<TopicMessageKey, Any>> =
            topicXSource.getOrPut(id, {
                val listen = getPubSubFluxFor(id)
                val processor = ReplayProcessor.create<Message<TopicMessageKey, Any>>(5)
                topicManager.setTopicProcessor(id, processor)
                topicManager.subscribeTopicProcessor(id, listen)

                listen
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
                    .wrap((prefixTopicKey + id.toString()).toByteArray(Charset.defaultCharset())))
            .doOnNext {
                topicManager
                        .closeTopic(id)
            }.then()

    override fun getProcessor(id: UUID): FluxProcessor<out Message<TopicMessageKey, Any>, out Message<TopicMessageKey, Any>> =
            topicManager.getTopicProcessor(id)

    private fun getPubSubFluxFor(topic: UUID): Flux<out Message<TopicMessageKey, Any>> =
            messageTemplate
                    .listenTo(ChannelTopic(topic.toString()))
                    .map {
                        it.message.state!!
                    }
                    .doOnNext {
                        logger.info("PUBSUB: ${it.key.topicId}")
                    }.doOnComplete {
                        topicXSource.remove(topic)
                    }
}
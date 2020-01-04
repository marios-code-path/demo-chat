package com.demo.chatevents.service

import com.demo.chat.codec.Codec
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicNotFoundException
import com.demo.chat.service.ChatTopicService
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
import java.util.concurrent.ConcurrentHashMap

data class KeyConfigurationPubSub(
        val topicSetKey: String,
        val prefixTopicKey: String,
        val prefixUserToTopicSubs: String,
        val prefixTopicToUserSubs: String
)

class TopicServiceRedisPubSub<T>(
        keyConfig: KeyConfigurationPubSub,
        private val stringTemplate: ReactiveRedisTemplate<String, String>,
        private val messageTemplate: ReactiveRedisTemplate<String, TopicData<T, Any>>,
        val stringKeyDecoder: Codec<String, T>,
        val keyStringEncoder: Codec<T, String>
) : ChatTopicService<T, Any> {

    private val logger = LoggerFactory.getLogger(this::class.simpleName)
    private val prefixUserToTopicSubs = keyConfig.prefixUserToTopicSubs
    private val prefixTopicToUserSubs = keyConfig.prefixTopicToUserSubs
    private val topicSetKey = keyConfig.topicSetKey
    private val prefixTopicKey = keyConfig.prefixTopicKey

    private val topicManager = TopicManager<T, Any>()
    private val topicXSource: MutableMap<T, Flux<out Message<T, out Any>>> = ConcurrentHashMap()

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
                        topicManager.subscribeTopic(id, member)
                        it.onComplete()
                    }

    override fun unSubscribe(member: T, id: T): Mono<Void> =
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

    override fun unSubscribeAll(member: T): Mono<Void> =
            stringTemplate
                    .opsForSet()
                    .members(prefixUserToTopicSubs + member.toString())
                    .collectList()
                    .flatMap { topicList ->
                        Flux
                                .fromIterable(topicList)
                                .map { topicId ->
                                    unSubscribe(member, stringKeyDecoder.decode(topicId))
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
                                    unSubscribe(stringKeyDecoder.decode(member), id)
                                }
                                .subscribeOn(Schedulers.parallel())
                                .then()
                    }

    override fun sendMessage(topicMessage: Message<T, out Any>): Mono<Void> {
        val topic = topicMessage.key.dest

        return Mono.from(topicExistsOrError(topic))
                .then(messageTemplate.convertAndSend(topic.toString(), TopicData(topicMessage)))
                .then()
    }

    override fun receiveOn(streamId: T): Flux<out Message<T, out Any>> =
            topicManager.getTopicFlux(streamId)

    // may need to turn this into a different rturn type ( just start the source using .subscribe() )
    // Connect a Processor to a flux for message ingest ( xread -> processor )
    override fun receiveSourcedEvents(id: T): Flux<out Message<T, out Any>> =
            topicXSource.getOrPut(id, {
                val listen = getPubSubFluxFor(id)
                val processor = ReplayProcessor.create<Message<T, out Any>>(5)
                topicManager.setTopicProcessor(id, processor)
                topicManager.subscribeTopicProcessor(id, listen)

                listen
            })

    override fun getByUser(uid: T): Flux<T> =
            stringTemplate
                    .opsForSet()
                    .members(
                            prefixUserToTopicSubs + keyStringEncoder.decode(uid)
                    )
                    .map {
                        stringKeyDecoder.decode(it)
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
                                        stringKeyDecoder.decode(it)
                                    }
                    )

    override fun rem(id: T): Mono<Void> = messageTemplate
            .connectionFactory
            .reactiveConnection
            .keyCommands()
            .del(ByteBuffer
                    .wrap((prefixTopicKey + id.toString()).toByteArray(Charset.defaultCharset())))
            .doOnNext {
                topicManager
                        .closeTopic(id)
            }.then()

    override fun getProcessor(id: T): FluxProcessor<out Message<T, out Any>, out Message<T, out Any>> =
            topicManager.getTopicProcessor(id)

    private fun getPubSubFluxFor(topic: T): Flux<out Message<T, out Any>> =
            messageTemplate
                    .listenTo(ChannelTopic(topic.toString()))
                    .map {
                        it.message.state!!
                    }
                    .doOnNext {
                        logger.info("PUBSUB: ${it.key.dest}")
                    }.doOnComplete {
                        topicXSource.remove(topic)
                    }
}
package com.demo.chat.service.impl.memory.messaging

import com.demo.chat.codec.Decoder
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicNotFoundException
import com.demo.chat.service.PubSubTopicExchangeService
import com.demo.chat.service.impl.stream.ReactiveStreamManager
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import reactor.core.publisher.Flux
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

@Suppress("DuplicatedCode")
class PubSubMessagingServiceRedisPubSub<T, E>(
        keyConfig: KeyConfigurationPubSub,
        private val stringTemplate: ReactiveRedisTemplate<String, String>,
        private val messageTemplate: ReactiveRedisTemplate<String, Message<T, E>>,
        private val stringKeyDecoder: Decoder<String, T>,
        private val keyStringEncoder: Decoder<T, String>
) : PubSubTopicExchangeService<T, E> {

    private val replayDepth = 50
    private val logger = LoggerFactory.getLogger(this::class.simpleName)
    private val prefixUserToTopicSubs = keyConfig.prefixUserToTopicSubs
    private val prefixTopicToUserSubs = keyConfig.prefixTopicToUserSubs
    private val topicSetKey = keyConfig.topicSetKey
    private val prefixTopicKey = keyConfig.prefixTopicKey

    private val streamMgr = ReactiveStreamManager<T, E>()
    private val topicXSource: MutableMap<T, Flux<out Message<T, E>>> = ConcurrentHashMap()

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

    override fun subscribe(member: T, topic: T): Mono<Void> = topicExistsOrError(topic)
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
                streamMgr.subscribe(topic, member)
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
                                            null -> sink.error(ChatException("Unable to unsubscribe from id."))
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
                                            null -> sink.error(ChatException("Unable to unsubscribe from id."))
                                            else -> sink.complete()
                                        }
                                    }
                    )
                    .thenEmpty {
                        streamMgr
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
                                    unSubscribe(member, stringKeyDecoder.decode(topicId))
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
                                    unSubscribe(stringKeyDecoder.decode(member), topic)
                                }
                                .subscribeOn(Schedulers.parallel())
                                .then()
                    }

    override fun sendMessage(message: Message<T, E>): Mono<Void> {
        val topic = message.key.dest

        return Mono.from(topicExistsOrError(topic))
                .then(messageTemplate.convertAndSend(topic.toString(), message))
                .then()
    }

    override fun receiveOn(topic: T): Flux<out Message<T, E>> =
            streamMgr.getSink(topic)

    // may need to turn this into a different rturn type ( just start the source using .subscribe() )
    // Connect a Processor to a flux for message ingest ( xread -> processor )
    fun sourceOf(topic: T): Flux<out Message<T, E>> =
            topicXSource.getOrPut(topic, {
                val listen = getPubSubFluxFor(topic)
                val processor = ReplayProcessor.create<Message<T, E>>(replayDepth)
                streamMgr.setSource(topic, processor)
                streamMgr.subscribeUpstream(topic, listen)

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
                streamMgr
                        .close(id)
            }.then()

    private fun getPubSubFluxFor(topic: T): Flux<out Message<T, E>> =
            messageTemplate
                    .listenTo(ChannelTopic(topic.toString()))
                    .map {
                        it.message
                    }
                    .doOnNext {
                        logger.info("PUBSUB: ${it.key.dest}")
                    }.doOnComplete {
                        topicXSource.remove(topic)
                    }
}
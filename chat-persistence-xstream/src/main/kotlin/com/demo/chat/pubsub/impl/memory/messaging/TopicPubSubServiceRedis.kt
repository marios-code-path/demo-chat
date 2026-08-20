package com.demo.chat.pubsub.impl.memory.messaging

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Message
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.TopicPubSubService
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
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
class TopicPubSubServiceRedis<T, E>(
    keyConfig: KeyConfigurationPubSub,
    private val stringTemplate: ReactiveRedisTemplate<String, String>,
    private val messageTemplate: ReactiveRedisTemplate<String, Message<T, E>>,
    private val typeUtil: TypeUtil<T>,
) : TopicPubSubService<T, E> {

    private val replayDepth = 50
    private val logger = LoggerFactory.getLogger(this::class.simpleName)
    private val prefixUserToTopicSubs = keyConfig.prefixUserToTopicSubs
    private val prefixTopicToUserSubs = keyConfig.prefixTopicToUserSubs
    private val topicSetKey = keyConfig.topicSetKey
    private val prefixTopicKey = keyConfig.prefixTopicKey

    private val sinks: MutableMap<T, Sinks.Many<Message<T, E>>> = ConcurrentHashMap()
    private val topicXSource: MutableMap<T, Flux<out Message<T, E>>> = ConcurrentHashMap()

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
                sinks[topic]?.let { /* sink already exists; subscriber gets messages via Redis pub/sub */ }
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
                        unSubscribe(member, typeUtil.fromString(topicId))
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
                        unSubscribe(typeUtil.fromString(member), topic)
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

    override fun listenTo(topic: T): Flux<out Message<T, E>> =
        sinks.getOrPut(topic) {
            Sinks.many().multicast().onBackpressureBuffer()
        }.asFlux()

    // Connect a Redis pub/sub listener to the Sinks.Many for in-process fan-out
    private fun sourceOf(topic: T): Flux<out Message<T, E>> =
        topicXSource.getOrPut(topic) {
            val listen = getPubSubFluxFor(topic)
            val sink = sinks.getOrPut(topic) {
                Sinks.many().multicast().onBackpressureBuffer()
            }
            listen.doOnNext { msg -> sink.tryEmitNext(msg) }
            listen
        }

    override fun getByUser(uid: T): Flux<T> =
        stringTemplate
            .opsForSet()
            .members(
                prefixUserToTopicSubs + typeUtil.toString(uid)
            )
            .map {
                typeUtil.fromString(it)
            }

    override fun getUsersBy(topicId: T): Flux<T> =
        topicExistsOrError(topicId)
            .thenMany(
                stringTemplate
                    .opsForSet()
                    .members(
                        prefixTopicToUserSubs + typeUtil.toString(topicId)
                    )
                    .map {
                        typeUtil.fromString(it)
                    }
            )

    override fun close(topicId: T): Mono<Void> = messageTemplate
        .connectionFactory
        .reactiveConnection
        .keyCommands()
        .del(
            ByteBuffer
                .wrap((prefixTopicKey + topicId.toString()).toByteArray(Charset.defaultCharset()))
        )
        .doOnNext {
            sinks.remove(topicId)?.tryEmitComplete()
        }.then()

    private fun getPubSubFluxFor(topic: T): Flux<out Message<T, E>> =
        messageTemplate
            .listenTo(ChannelTopic(topic.toString()))
            .map {
                it.message
            }
            .doOnComplete {
                topicXSource.remove(topic)
            }
}
package com.demo.chat.pubsub.impl.memory.messaging

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Message
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.TopicPubSubService
import org.slf4j.LoggerFactory
import reactor.core.Disposable
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
class RedisTopicPubSubService<T : Any, E>(
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
    private val sources: MutableMap<T, Disposable> = ConcurrentHashMap()

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
            .then(sourceOf(topicId))

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

    /**
     * Connects the Redis channel to the sink that `listenTo` answers.
     *
     * **This fed nothing until 2026-09-19.** The earlier version called
     * `listen.doOnNext { ... }` and discarded the result, then stored and
     * answered the undecorated flux. `doOnNext` answers a new `Flux`, so two
     * things were wrong together: nothing subscribed, and the stored flux
     * would not have fed the sink even if something had. A listener waited
     * on a sink that no publisher ever wrote to. See CHAT-scrrknxb.
     *
     * `listenToLater` answers a `Mono` that completes once Redis has the
     * SUBSCRIBE. `open` waits for it, so a send that follows `open` cannot
     * race the subscription. The plain `listenTo` would leave that race open.
     *
     * The `Disposable` is kept so `close` can end the subscription.
     */
    private fun sourceOf(topic: T): Mono<Void> {
        if (sources.containsKey(topic)) {
            return Mono.empty()
        }

        val sink = sinks.getOrPut(topic) {
            Sinks.many().multicast().onBackpressureBuffer()
        }

        return messageTemplate
            .listenToLater(ChannelTopic(topic.toString()))
            .doOnNext { channel ->
                sources[topic] = channel
                    .map { received -> received.message }
                    .subscribe(
                        { message -> sink.tryEmitNext(message) },
                        { error -> logger.error("The topic listener of $topic failed", error) },
                    )
            }
            .then()
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
            sources.remove(topicId)?.dispose()
            sinks.remove(topicId)?.tryEmitComplete()
        }.then()

}
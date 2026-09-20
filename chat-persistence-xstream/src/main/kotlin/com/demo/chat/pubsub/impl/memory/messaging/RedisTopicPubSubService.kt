package com.demo.chat.pubsub.impl.memory.messaging

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Message
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.TopicPubSubService
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

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
    /**
     * Opens the channel stream of one topic.
     *
     * Null takes the real `listenToLater`. A test supplies its own flux
     * here, so the reader failure rule can be read without breaking a
     * container. `KafkaTopicPubSubService` carries the same seam for the
     * same reason. See CHAT-czmjffen.
     */
    private val channelMessages: ((T) -> Mono<Flux<Message<T, E>>>)? = null,
) : TopicPubSubService<T, E> {

    private val replayDepth = 50
    private val logger = LoggerFactory.getLogger(this::class.simpleName)
    private val prefixUserToTopicSubs = keyConfig.prefixUserToTopicSubs
    private val prefixTopicToUserSubs = keyConfig.prefixTopicToUserSubs
    private val topicSetKey = keyConfig.topicSetKey
    private val prefixTopicKey = keyConfig.prefixTopicKey

    /**
     * The sink and the reader of one topic, held together.
     *
     * **They are one entry because they share one lifetime.** An earlier
     * version kept two maps and removed from them in turn. Between the two
     * removals a concurrent `open` could take the old sink and install a
     * new reader, and the failing handler then errored the sink that the
     * new reader had just adopted. The new reader wrote into an errored
     * sink while later listeners received a sink that no reader fed.
     *
     * One map removes one entry in one step, and the handler errors the
     * sink it already holds rather than looking one up again. The
     * interleaving has nowhere to happen. See CHAT-czmjffen.
     */
    private data class TopicSource<T : Any, E>(
        val sink: Sinks.Many<Message<T, E>>,
        val reader: Mono<Disposable>?,
    )

    private val topics: ConcurrentHashMap<T, TopicSource<T, E>> = ConcurrentHashMap()

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
            sinkOf(topic)
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
                topics[topic]?.let { /* the reader feeds every subscriber of this topic */ }
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
        sinkOf(topic).asFlux()

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
        // The reader is attached inside compute, so the sink it captures and
        // the entry it lives in are installed in one step. A concurrent
        // failure of an older entry cannot slip between them.
        val holder = AtomicReference<TopicSource<T, E>>()
        val entry = topics.compute(topic) { _, current ->
            if (current?.reader != null) {
                current
            } else {
                val sink = current?.sink ?: Sinks.many().multicast().onBackpressureBuffer()
                TopicSource(sink, startReader(topic, sink, holder).cache())
                    .also { created -> holder.set(created) }
            }
        }!!

        return requireNotNull(entry.reader)
            .then()
            .onErrorResume { error ->
                // A startup failure never reaches the reader handler, so it
                // terminates the topic here. Without this a listener waits
                // on a sink that no reader will ever feed. See CHAT-czmjffen.
                failReader(topic, entry, error)
                Mono.error(error)
            }
    }

    private fun startReader(
        topic: T,
        sink: Sinks.Many<Message<T, E>>,
        holder: AtomicReference<TopicSource<T, E>>,
    ): Mono<Disposable> {
        val messages = channelMessages?.invoke(topic) ?: listenToChannel(topic)

        return messages.map { channel ->
            channel.subscribe(
                { message -> sink.tryEmitNext(message) },
                { error -> failReader(topic, holder.get(), error) },
            )
        }
    }

    /** The sink of one topic, created with no reader when none exists yet. */
    private fun sinkOf(topic: T): Sinks.Many<Message<T, E>> = topics.computeIfAbsent(topic) {
        TopicSource(Sinks.many().multicast().onBackpressureBuffer(), null)
    }.sink

    private fun listenToChannel(topic: T): Mono<Flux<Message<T, E>>> = messageTemplate
        .listenToLater(ChannelTopic(topic.toString()))
        .map { channel -> channel.map { received -> received.message } }

    /**
     * Ends one topic after its reader failed.
     *
     * **One removal decides everything.** The entry carries the sink, so a
     * successful `remove` both retires the reader and hands this method the
     * sink to terminate. A reader that failed after a newer entry replaced
     * it removes nothing and touches nothing.
     *
     * The signal is an error rather than a completion. A completion would
     * look like a clean `close`, and a reader failure is not one. A listener
     * that simply stopped receiving could not tell a dead reader apart from
     * a quiet topic. See CHAT-czmjffen.
     */
    private fun failReader(topic: T, entry: TopicSource<T, E>?, error: Throwable) {
        val owned = entry != null && topics.remove(topic, entry)
        if (owned) {
            requireNotNull(entry).sink.tryEmitError(error)
        }
        logger.error("The topic listener of $topic failed, ownedEntry=$owned", error)
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

    override fun close(topicId: T): Mono<Void> {
        // One removal retires the reader and the sink together, for the
        // same reason failReader does. See CHAT-czmjffen.
        val retired = topics.remove(topicId)
        val stopSource = retired?.reader
            ?.doOnNext { subscription -> subscription.dispose() }
            ?.then()
            ?: Mono.empty()

        return stopSource
            .then(Mono.fromRunnable { retired?.sink?.tryEmitComplete() })
            .then(
                messageTemplate.connectionFactory.reactiveConnection.keyCommands().del(
                    ByteBuffer.wrap(
                        (prefixTopicKey + topicId.toString()).toByteArray(Charset.defaultCharset())
                    )
                )
            )
            .then()
    }

}

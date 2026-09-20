package com.demo.chat.pubsub.impl.memory.messaging

import com.demo.chat.convert.Converter
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Message
import com.demo.chat.domain.NotFoundException
import com.demo.chat.service.core.TopicPubSubService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.Limit
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.stream.StreamReceiver
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

data class KeyConfiguration(
        val topicSetKey: String,
        val prefixTopicStream: String,
        val prefixUserToTopicSubs: String,
        val prefixTopicToUserSubs: String
)

@Suppress("DuplicatedCode")
class XStreamTopicPubSubService<T : Any, E>(
    keyConfig: KeyConfiguration,
    private val stringTemplate: ReactiveRedisTemplate<String, String>,
    private val messageTemplate: ReactiveRedisTemplate<String, Message<T, E>>,
    private val stringKeyConverter: Converter<String, out T>,
    private val keyStringConverter: Converter<T, String>,
    /**
     * Opens the record stream of one topic.
     *
     * Null takes the real `StreamReceiver`. A test supplies its own flux
     * here, so the reader failure rule can be read without breaking a
     * container. `KafkaTopicPubSubService` carries the same seam for the
     * same reason. See CHAT-czmjffen.
     */
    private val streamRecords: ((String, RecordId) -> Flux<Message<T, E>>)? = null,
) : TopicPubSubService<T, E> {

    private val replayDepth = 50
    private val logger = LoggerFactory.getLogger(this::class.simpleName)
    private val prefixUserToTopicSubs = keyConfig.prefixUserToTopicSubs
    private val prefixTopicToUserSubs = keyConfig.prefixTopicToUserSubs
    private val topicSetKey = keyConfig.topicSetKey
    private val prefixTopicStream = keyConfig.prefixTopicStream

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
    override fun open(topicId: T): Mono<Void> = stringTemplate
        .opsForSet()
        .add(topicSetKey, topicId.toString())
        .then(sourceOf(topicId))

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
                        topics[topic]?.let { }
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
            sinkOf(topic).asFlux()

    private fun sourceOf(topic: T): Mono<Void> {
        // The reader error handler must remove its own entry and no other.
        // It cannot name the entry while computeIfAbsent builds it, so the
        // holder carries the reference forward. A candidate that loses the
        // race is never inserted and never subscribed, so its handler never
        // runs. See CHAT-czmjffen.
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
        val streamKey = prefixTopicStream + topic.toString()
        val context = messageTemplate.serializationContext
        val options: StreamReceiver.StreamReceiverOptions<String, MapRecord<String, String, Message<T, E>>> =
            StreamReceiver.StreamReceiverOptions.builder()
            .keySerializer<String, MapRecord<String, String, Message<T, E>>>(context.keySerializationPair)
            .hashKeySerializer<String, Message<T, E>>(context.getHashKeySerializationPair<String>())
            .hashValueSerializer<String, Message<T, E>>(context.getHashValueSerializationPair<Message<T, E>>())
            .build()
        return messageTemplate.opsForStream<String, Message<T, E>>()
            .reverseRange(streamKey, Range.unbounded<String>(), Limit.limit().count(1))
            .next()
            .map { record -> record.id }
            .defaultIfEmpty(RecordId.of("0-0"))
            .map { cursor ->
                streamRecords?.invoke(streamKey, cursor)
                    ?: receiveFrom(streamKey, cursor, options)
            }
            .map { messages ->
                messages.subscribe(
                    { message -> sink.tryEmitNext(message) },
                    { error -> failReader(topic, holder.get(), error) },
                )
            }
    }

    private fun receiveFrom(
        streamKey: String,
        cursor: RecordId,
        options: StreamReceiver.StreamReceiverOptions<String, MapRecord<String, String, Message<T, E>>>,
    ): Flux<Message<T, E>> =
        StreamReceiver.create<String, MapRecord<String, String, Message<T, E>>>(
            messageTemplate.connectionFactory,
            options,
        )
            .receive(StreamOffset.create(streamKey, ReadOffset.from(cursor)))
            .map { record -> record.value["data"]!! }

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
        logger.error("The XStream reader for $topic failed, ownedEntry=$owned", error)
    }

    /** The sink of one topic, created with no reader when none exists yet. */
    private fun sinkOf(topic: T): Sinks.Many<Message<T, E>> = topics.computeIfAbsent(topic) {
        TopicSource(Sinks.many().multicast().onBackpressureBuffer(), null)
    }.sink

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

    override fun close(topicId: T): Mono<Void> {
        // One removal retires the reader and the sink together, for the
        // same reason failReader does. See CHAT-czmjffen.
        val retired = topics.remove(topicId)
        val stopReader = retired?.reader
            ?.doOnNext { reader -> reader.dispose() }
            ?.then()
            ?: Mono.empty()

        return stopReader
            .then(Mono.fromRunnable { retired?.sink?.tryEmitComplete() })
            .then(
                messageTemplate.connectionFactory.reactiveConnection.keyCommands()
                    .del(ByteBuffer.wrap(
                        (prefixTopicStream + topicId.toString()).toByteArray(Charset.defaultCharset())
                    ))
            )
            .then()
    }
    // TODO: For multi-instance deployment, add consumer-group-based XREADGROUP
    //  per service instance. Current design uses Sinks.Many for in-process fan-out
    //  and captures the stream tail before each reader starts. Cross-process fan-out
    //  requires consumer groups with unique consumer names per instance.
}

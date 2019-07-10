package com.demo.chatevents

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Message
import com.demo.chat.domain.RoomNotFoundException
import com.demo.chat.domain.TopicMessageKey
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.ChatTopicServiceAdmin
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
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

class TopicServiceRedis(
        keyConfig: KeyConfiguration,
        private val stringTemplate: ReactiveRedisTemplate<String, String>,
        private val messageTemplate: ReactiveRedisTemplate<String, TopicData>
) : ChatTopicService, ChatTopicServiceAdmin {

    private val logger = LoggerFactory.getLogger(this::class.simpleName)
    private val prefixUserToTopicSubs = keyConfig.prefixUserToTopicSubs
    private val prefixTopicToUserSubs = keyConfig.prefixTopicToUserSubs
    private val topicSetKey = keyConfig.topicSetKey
    private val prefixTopicStream = keyConfig.prefixTopicStream

    private val streamManager = StreamManager()
    private val topicXReads: MutableMap<UUID, Flux<out Message<TopicMessageKey, Any>>> = ConcurrentHashMap()

    private fun topicExistsOrError(topic: UUID): Mono<Void> = topicExists(topic)
            .filter {
                it == true
            }
            .switchIfEmpty(Mono.error(RoomNotFoundException))
            .then()

    override fun topicExists(topic: UUID): Mono<Boolean> = stringTemplate
            .opsForSet()
            .isMember(topicSetKey, topic.toString())

    // Idempotent
    override fun createTopic(topic: UUID): Mono<Void> =
            stringTemplate
                    .opsForSet()
                    .add(topicSetKey, topic.toString())
                    .thenEmpty {
                        receiveSourcedEvents(topic)
                        it.onComplete()
                    }

    override fun subscribeToTopic(member: UUID, topic: UUID): Mono<Void> =
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
                        streamManager.consumeStream(topic, member)
                        it.onComplete()
                    }

    override fun unSubscribeFromTopic(member: UUID, topic: UUID): Mono<Void> =
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
                                .disconnectFromStream(topic, member)
                        it.onComplete()
                    }

    override fun unSubscribeFromAllTopics(member: UUID): Mono<Void> =
            stringTemplate
                    .opsForSet()
                    .members(prefixUserToTopicSubs + member.toString())
                    .collectList()
                    .flatMap { topicList ->
                        Flux
                                .fromIterable(topicList)
                                .map { topicId ->
                                    unSubscribeFromTopic(member, UUID.fromString(topicId))
                                }
                                .subscribeOn(Schedulers.parallel())
                                .then()
                    }

    override fun kickallFromTopic(topic: UUID): Mono<Void> =
            stringTemplate
                    .opsForSet()
                    .members(prefixTopicToUserSubs + topic.toString())
                    .collectList()
                    .flatMap { members ->
                        Flux
                                .fromIterable(members)
                                .map { member ->
                                    unSubscribeFromTopic(UUID.fromString(member), topic)
                                }
                                .subscribeOn(Schedulers.parallel())
                                .then()
                    }

    override fun sendMessageToTopic(topicMessage: Message<TopicMessageKey, Any>): Mono<Void> {
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

    override fun receiveEvents(streamId: UUID): Flux<out Message<TopicMessageKey, Any>> =
            streamManager.getStreamFlux(streamId)

    // may need to turn this into a different rturn type ( just start the source using .subscribe() )
    // Connect a Processor to a flux for message ingest ( xread -> processor )
    override fun receiveSourcedEvents(streamId: UUID): Flux<out Message<TopicMessageKey, Any>> =
            topicXReads.getOrPut(streamId, {
                val xread = getXReadFlux(streamId)
                val reProc = ReplayProcessor.create<Message<TopicMessageKey, Any>>(5)
                streamManager.setStreamProcessor(streamId, reProc)
                streamManager.subscribeTo(streamId, xread)

                xread
            })

    //    topicXSource
//    .getOrPut(streamId, {
//        val proc = ReplayProcessor.create<Message<TopicMessageKey, Any>>(1)
//        streamManager.setStreamProcessor(streamId, proc)
//
//        val reader = streamManager.getStreamFlux(streamId)
//        topicXSource[streamId] = reader
//
//        reader
//    })
    override fun getMemberTopics(uid: UUID): Flux<UUID> =
            stringTemplate
                    .opsForSet()
                    .members(
                            prefixUserToTopicSubs + uid.toString()
                    )
                    .map(UUID::fromString)

    override fun getTopicMembers(topic: UUID): Flux<UUID> =
            topicExistsOrError(topic)
                    .thenMany(
                            stringTemplate
                                    .opsForSet()
                                    .members(
                                            prefixTopicToUserSubs + topic.toString()
                                    )
                                    .map(UUID::fromString)
                    )

    override fun closeTopic(topic: UUID): Mono<Void> = messageTemplate
            .connectionFactory
            .reactiveConnection
            .keyCommands()
            .del(ByteBuffer
                    .wrap((prefixTopicStream + topic.toString()).toByteArray(Charset.defaultCharset())))
            .doOnNext {
                streamManager
                        .closeStream(topic)
            }.then()

    override fun getStreamProcessor(topicId: UUID): FluxProcessor<out Message<TopicMessageKey, Any>, out Message<TopicMessageKey, Any>> =
            streamManager.getStreamProcessor(topicId)

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
                    }.repeat()


}

@Configuration
class ChatEventsRedisConfiguration {
    @Bean
    fun redisConnectionFactory(): ReactiveRedisConnectionFactory = LettuceConnectionFactory()

    @Bean
    fun objectMapper(): ObjectMapper =
            jacksonObjectMapper().registerModule(KotlinModule()).apply {
                propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
                //configure(SerializationFeature.WRAP_ROOT_VALUE, true)
                registerSubtypes(JoinAlertMessage::class.java, ChatMessage::class.java, TopicData::class.java)
            }.findAndRegisterModules()!!


    @Bean
    fun topicTemplate(cf: ReactiveRedisConnectionFactory): ReactiveRedisTemplate<String, TopicData> {
        val keys = StringRedisSerializer()
        val values = Jackson2JsonRedisSerializer(TopicData::class.java)
        values.setObjectMapper(objectMapper())           // KOTLIN USERS : use setObjectMapper!

        val builder: RedisSerializationContext.RedisSerializationContextBuilder<String, TopicData> =
                RedisSerializationContext.newSerializationContext(keys)

        builder.key(keys)
        builder.value(values)
        builder.hashKey(keys)
        builder.hashValue(values)

        return ReactiveRedisTemplate(cf, builder.build())
    }

    @Bean
    fun objectTemplate(cf: ReactiveRedisConnectionFactory): ReactiveRedisTemplate<String, Object> {
        val keys = StringRedisSerializer()

        val builder: RedisSerializationContext.RedisSerializationContextBuilder<String, Object> =
                RedisSerializationContext.newSerializationContext(keys)

        val defaultSerializer = JdkSerializationRedisSerializer(this.javaClass.classLoader)

        builder.key(keys)
        builder.hashKey(keys)
        builder.hashValue(defaultSerializer)

        return ReactiveRedisTemplate(cf, builder.build())
    }

}
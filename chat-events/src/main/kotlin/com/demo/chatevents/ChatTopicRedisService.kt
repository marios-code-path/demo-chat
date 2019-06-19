package com.demo.chatevents

import com.demo.chat.domain.*
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.ChatTopicServiceAdmin
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import reactor.core.publisher.DirectProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

class ChatTopicRedisService(
        val cf: ReactiveRedisConnectionFactory,
        val stringTemplate: ReactiveRedisTemplate<String, String>,
        val messageTemplate: ReactiveRedisTemplate<String,TopicData>
) : ChatTopicService, ChatTopicServiceAdmin {
    val prefixTopicKey = "mytopiclist_"
    val prefixTopicSubsKey = "topicSubs_"
    val prefixTopicStreamKey = "topicStream_"


    override fun subscribeToTopic(member: UUID, topic: UUID): Mono<Void> =
            stringTemplate    // todo join the streams
                    .opsForSet()
                    .add(prefixTopicKey + member.toString(), topic.toString())
                    .handle { a, sink ->
                        when (a) {
                            null -> sink.error(ChatException("Unable to subscribe to stream"))
                            else -> sink.complete()
                        }
                    }

    override fun unSubscribeFromTopic(member: UUID, topic: UUID): Mono<Void> =
            stringTemplate    // todo Streams entries too!
                    .opsForSet()
                    .remove(prefixTopicKey + member.toString(), topic.toString())
                    .handle { a, sink ->
                        when (a) {
                            null -> sink.error(ChatException("Unable to unsubscribe from stream."))
                            else -> sink.complete()
                        }
                    }


    override fun unSubscribeFromAllTopics(member: UUID): Mono<Void> =
            stringTemplate    // todo Streams entries wherever
                    .opsForSet()
                    .delete(prefixTopicKey + member.toString())
                    .handle { a, sink ->
                        when (a) {
                            null -> sink.error(ChatException("Unable to unsubscribe from all streams."))
                            else -> sink.complete()
                        }
                    }

    override fun kickallFromTopic(topic: UUID): Mono<Void> =
            stringTemplate
                    .opsForSet()
                    .members(prefixTopicSubsKey + topic.toString())
                    .flatMap { member ->
                        unSubscribeFromTopic(UUID.fromString(member), topic)
                    }
                    .then(      // todo clean up streams entries too !
                            stringTemplate
                                    .opsForSet()
                                    .delete(prefixTopicSubsKey + topic.toString())
                                    .then()
                    )

    override fun sendMessageToTopic(topicMessage: Message<TopicMessageKey, Any>): Mono<Void> {
        val map = mapOf(Pair("msgId", topicMessage.key.msgId))
                  //      Pair("data", TopicData(topicMessage)))

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

        return messageTemplate
                .opsForStream<String, TopicData>()
                .add(MapRecord
                        .create(prefixTopicStreamKey + topicMessage.key.topicId.toString(), map)
                        .withId(recordId))
                .then()
    }

    override fun receiveTopicEvents(topic: UUID): Flux<out Message<TopicMessageKey, Any>> =
            messageTemplate
                    .opsForStream<String, TopicData>()
                    .read(TopicData::class.java, StreamOffset.fromStart(prefixTopicStreamKey + topic.toString()))
                    .map {
                        it as TextMessage
                    }

    override fun getMemberTopics(uid: UUID): List<UUID> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getTopicMembers(uid: UUID): List<UUID> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun closeTopic(topic: UUID): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getTopicProcessor(topicId: UUID): DirectProcessor<out Message<TopicMessageKey, Any>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("TopicData")
data class TopicData(val state: Message<out TopicMessageKey, Any>)

@JsonTypeName("ChatMessage")
data class ChatMessage(
        override val key: ChatMessageKey,
        override val value: String,
        override val visible: Boolean
) : TextMessage

data class ChatMessageKey(
        override val msgId: UUID,
        override val userId: UUID,
        override val topicId: UUID,
        override val timestamp: Instant
) : TextMessageKey

@Configuration
class ChatEventsRedisConfiguration {
    @Bean
    fun redisConnectionFactory(): ReactiveRedisConnectionFactory = LettuceConnectionFactory()

    @Bean
    fun objectMapper(): ObjectMapper =
        jacksonObjectMapper().registerModule(KotlinModule()).apply {
            propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            //configure(SerializationFeature.WRAP_ROOT_VALUE, true)
            registerSubtypes(ChatMessage::class.java, TopicData::class.java)
        }.findAndRegisterModules()!!


    @Bean
    fun someCache(cf: ReactiveRedisConnectionFactory): ReactiveRedisTemplate<String, TopicData> {
        val keys = StringRedisSerializer()
        val values = Jackson2JsonRedisSerializer(TopicData::class.java)
        values.setObjectMapper(objectMapper())           // KOTLIN USERS : use setObjectMapper!

        val builder: RedisSerializationContext.RedisSerializationContextBuilder<String,  TopicData> =
                RedisSerializationContext.newSerializationContext(keys)

        val hashValues = Jackson2JsonRedisSerializer(TopicData::class.java)
        hashValues.setObjectMapper(objectMapper())

        builder.key(keys)
        builder.value(values)
        builder.hashKey(keys)
        builder.hashValue(hashValues)

        return ReactiveRedisTemplate(cf, builder.build())
    }
}
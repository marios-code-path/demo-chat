package com.demo.chat

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.*
import com.demo.chatevents.config.TopicRedisTemplateConfiguration
import com.demo.chatevents.service.KeyConfigurationPubSub
import com.demo.chatevents.service.TopicServiceMemory
import com.demo.chatevents.service.TopicServiceRedisPubSub
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

@SpringBootApplication
@ComponentScan(excludeFilters = [
    ComponentScan.Filter(type = FilterType.ANNOTATION, value = [ExcludeFromTests::class])
])
class ChatServiceRsocketApplication

fun main(args: Array<String>) {

    runApplication<ChatServiceRsocketApplication>(*args)
}

annotation class ExcludeFromTests

@Configuration
@ExcludeFromTests
class ChatServiceModule {
    @Bean
    fun keyService(): KeyService = KeyServiceCassandra

    @Bean
    fun userPersistence(keyService: KeyService,
                        userRepo: ChatUserRepository,
                        userHandleRepo: ChatUserHandleRepository): ChatUserPersistence<out User, UserKey> =
            ChatUserPersistenceCassandra(keyService, userRepo, userHandleRepo)

    @Bean
    fun roomPersistence(keyService: KeyService,
                        roomRepo: ChatRoomRepository,
                        roomNameRepo: ChatRoomNameRepository): ChatRoomPersistence<out Room, RoomKey> =
            ChatRoomPersistenceCassandra(keyService, roomRepo)

    @Bean
    fun messagePersistence(keyService: KeyService,
                           messageRepo: ChatMessageRepository,
                           messageByTopicRepo: ChatMessageByTopicRepository): TextMessagePersistence<TextMessage, TextMessageKey> =
            TextMessagePersistenceCassandra(keyService, messageRepo, messageByTopicRepo)

    @Bean
    fun topicServiceInMemory(): ChatTopicService = TopicServiceMemory()

    //@Bean
    fun redisConnectionFactory(): ReactiveRedisConnectionFactory = LettuceConnectionFactory()

    //@Bean
    fun topicServiceRedis(): TopicServiceRedisPubSub {
        val topicConfigRedis = TopicRedisTemplateConfiguration()

        val factory = redisConnectionFactory()

        return TopicServiceRedisPubSub(
                KeyConfigurationPubSub("all_topics",
                        "st_topic_",
                        "l_user_topics_",
                        "l_topic_users_"),
                ReactiveStringRedisTemplate(factory),
                topicConfigRedis.topicTemplate(factory)
        )
    }
}
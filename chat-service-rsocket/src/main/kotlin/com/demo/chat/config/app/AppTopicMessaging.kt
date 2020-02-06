package com.demo.chat.config.app

import com.demo.chat.config.TopicMessagingConfigurationRedis
import com.demo.chat.service.ChatTopicMessagingService
import com.demo.chatevents.config.ConfigurationTopicRedis
import com.demo.chatevents.service.TopicMessagingServiceMemory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import java.util.*

open class AppTopicMessaging(msg: String) {
    @Profile("memory-topics")
    @Bean
    open fun topicMessagingInMemory(): ChatTopicMessagingService<UUID, String> = TopicMessagingServiceMemory()

    @Profile("redis-topics")
    @Bean
    open fun topicMessagingRedis(props: ConfigurationTopicRedis): ChatTopicMessagingService<*, *> =
            TopicMessagingConfigurationRedis(props).topicMessagingRedis()
}
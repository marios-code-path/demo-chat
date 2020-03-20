package com.demo.deploy.config.app

import com.demo.chat.service.ChatTopicMessagingService
import com.demo.chatevents.config.ConfigurationRedisTemplate
import com.demo.chatevents.service.TopicMessagingServiceMemory
import com.demo.deploy.config.TopicMessagingConfigurationRedis
import java.util.*

open class AppTopicMessagingMemory {
    open fun topicMessagingInMemory(): ChatTopicMessagingService<UUID, String> = TopicMessagingServiceMemory()
}

open class AppTopicMessagingRedis {
    open fun topicMessagingRedis(props: ConfigurationRedisTemplate): ChatTopicMessagingService<*, *> =
            TopicMessagingConfigurationRedis(props).topicMessagingRedisPubSub()
}
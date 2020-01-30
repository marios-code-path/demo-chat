package com.demo.chat

import com.demo.chat.config.*
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.ChatTopicMessagingService
import com.demo.chat.service.IKeyService
import com.demo.chatevents.config.ConfigurationPropertiesTopicRedis
import com.demo.chatevents.service.TopicMessagingServiceMemory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.*
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import java.util.*

@SpringBootApplication
@ComponentScan(excludeFilters = [
    ComponentScan.Filter(type = FilterType.ANNOTATION, value = [ExcludeFromTests::class])
])
class ChatServiceRsocketApplication {
    @Configuration
    class UUIDKeyPersistence :
            KeyPersistenceConfigurationCassandra<UUID>(UUIDKeyGeneratorCassandra())

    @Configuration
    class PersistenceCassandra(
            keyService: IKeyService<UUID>,
            userRepo: ChatUserRepository<UUID>,
            topicRepo: TopicRepository<UUID>,
            messageRepo: ChatMessageRepository<UUID>,
            membershipRepo: TopicMembershipRepository<UUID>
    ) : PersistenceConfigurationCassandra<UUID>(keyService, userRepo, topicRepo, messageRepo, membershipRepo)

    @Configuration
    class IndexCassandra(
            cassandra: ReactiveCassandraTemplate,
            userHandleRepo: ChatUserHandleRepository<UUID>,
            roomRepo: TopicRepository<UUID>,
            nameRepo: TopicByNameRepository<UUID>,
            byMemberRepo: TopicMembershipByMemberRepository<UUID>,
            byMemberOfRepo: TopicMembershipByMemberOfRepository<UUID>,
            byUserRepo: ChatMessageByUserRepository<UUID>,
            byTopicRepo: ChatMessageByTopicRepository<UUID>
    ) : IndexConfigurationCassandra<UUID>(cassandra, userHandleRepo, roomRepo, nameRepo, byMemberRepo, byMemberOfRepo, byUserRepo, byTopicRepo)

    @Bean
    @Profile("memory-topics")
    fun topicMessagingInMemory(): ChatTopicMessagingService<UUID, String> =
            TopicMessagingServiceMemory()

    @Configuration
    @Profile("redis-topics")
    class TopicMessagingRedisConfiguration(props: ConfigurationPropertiesTopicRedis): TopicMessagingConfigurationRedis(props)
}

@EnableConfigurationProperties(CassandraProperties::class, ConfigurationPropertiesRedisTopics::class)
@Configuration
class ServiceDiscoveryConfiguration {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<ChatServiceRsocketApplication>(*args)
        }
    }
}

annotation class ExcludeFromTests
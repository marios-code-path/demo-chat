package com.demo.chat.deploy.cassandra

import com.demo.chat.codec.EmptyStringCodec
import com.demo.chat.controller.edge.MessagingController
import com.demo.chat.controller.edge.TopicController
import com.demo.chat.controller.edge.UserController
import com.demo.chat.controller.service.IndexServiceController
import com.demo.chat.controller.service.KeyServiceController
import com.demo.chat.controller.service.PersistenceServiceController
import com.demo.chat.deploy.codec.UUIDKeyGeneratorCassandra
import com.demo.chat.deploy.config.CassandraIndexFactory
import com.demo.chat.deploy.config.CassandraKeyServiceFactory
import com.demo.chat.deploy.config.CassandraPersistenceFactory
import com.demo.chat.deploy.config.client.RSocketClientFactory
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.*
import com.demo.chat.service.impl.memory.messaging.MemoryPubSubTopicExchange
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import java.util.*

// TODO Ssssoooooooo I dont know how to get our rsocket server
// to register with Consul, so I'm sending a tag instead
// using spring.rsocket.server.port as the source
@SpringBootApplication(excludeName = ["com.demo.chat.deploy"])
@EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.repository.cassandra"])
@EnableConfigurationProperties(CassandraProperties::class)
@Import(RSocketClientFactory::class)
class ChatServiceCassandraApplication {
    val logger = LoggerFactory.getLogger(this::class.java)

    @Profile("client-key")
    @Bean
    fun keyPersistenceClient(clientFactory: RSocketClientFactory): IKeyService<UUID> = clientFactory.keyClient()

    @Profile("cassandra-key")
    @Bean
    fun keyServiceCassandra(t: ReactiveCassandraTemplate) =
            CassandraKeyServiceFactory(t, UUIDKeyGeneratorCassandra()).keyService()

    @Profile("memory-messaging")
    @Bean
    fun memoryMessaging(): PubSubTopicExchangeService<UUID, String> = MemoryPubSubTopicExchange<UUID, String>()

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<ChatServiceCassandraApplication>(*args)
        }
    }
}

@Profile("ctrl-service-key")
@Controller
@MessageMapping("key")
class KeyServiceRSocket(t: ReactiveCassandraTemplate) : KeyServiceController<UUID>(
        CassandraKeyServiceFactory(t, UUIDKeyGeneratorCassandra()).keyService())

@Profile("cassandra-persistence")
@Configuration
class PersistenceControllerConfig(keyService: IKeyService<UUID>,
                                  userRepo: ChatUserRepository<UUID>,
                                  topicRepo: TopicRepository<UUID>,
                                  messageRepo: ChatMessageRepository<UUID>,
                                  membershipRepo: TopicMembershipRepository<UUID>
) : CassandraPersistenceFactory<UUID>(keyService, userRepo, topicRepo, messageRepo, membershipRepo) {
    @Profile("ctrl-persist-user")
    @Controller
    @MessageMapping("user")
    class UserPersistenceRSocket(t: UserPersistence<UUID>) : PersistenceServiceController<UUID, User<UUID>>(t)

    @Profile("ctrl-persist-message")
    @Controller
    @MessageMapping("message")
    class MessagePersistenceRSocket(t: MessagePersistence<UUID, String>) : PersistenceServiceController<UUID, Message<UUID, String>>(t)

    @Profile("ctrl-persist-topic")
    @Controller
    @MessageMapping("topic")
    class TopicPersistenceRSocket(t: TopicPersistence<UUID>) : PersistenceServiceController<UUID, MessageTopic<UUID>>(t)

    @Profile("ctrl-persist-membership")
    @Controller
    @MessageMapping("membership")
    class MembershipPersistenceRSocket(t: MembershipPersistence<UUID>) : PersistenceServiceController<UUID, TopicMembership<UUID>>(t)
}

@Profile("cassandra-index")
@Configuration
class IndexControllerConfig(
        cassandra: ReactiveCassandraTemplate,
        userHandleRepo: ChatUserHandleRepository<UUID>,
        roomRepo: TopicRepository<UUID>,
        nameRepo: TopicByNameRepository<UUID>,
        byMemberRepo: TopicMembershipByMemberRepository<UUID>,
        byMemberOfRepo: TopicMembershipByMemberOfRepository<UUID>,
        byUserRepo: ChatMessageByUserRepository<UUID>,
        byTopicRepo: ChatMessageByTopicRepository<UUID>
) : CassandraIndexFactory<UUID>(cassandra, userHandleRepo, roomRepo, nameRepo, byMemberRepo, byMemberOfRepo, byUserRepo, byTopicRepo) {
    @Profile("ctrl-index-user")
    @Controller
    @MessageMapping("index.user")
    class UserIndexRSocket(t: UserIndexService<UUID>) : IndexServiceController<UUID, User<UUID>, Map<String, String>>(t)

    @Profile("ctrl-index-message")
    @Controller
    @MessageMapping("index.message")
    class MessageIndexRSocket(t: MessageIndexService<UUID, String>) : IndexServiceController<UUID, Message<UUID, String>, Map<String, UUID>>(t)

    @Profile("ctrl-index-topic")
    @Controller
    @MessageMapping("index.topic")
    class TopicIndexRSocket(t: TopicIndexService<UUID>) : IndexServiceController<UUID, MessageTopic<UUID>, Map<String, String>>(t)

    @Profile("ctrl-index-membership")
    @Controller
    @MessageMapping("index.membership")
    class MembershipIndexRSocket(t: MembershipIndexService<UUID>) : IndexServiceController<UUID, TopicMembership<UUID>, Map<String, UUID>>(t)
}

@Profile("ctrl-edge-user")
@Controller
@MessageMapping("edge.user")
class EdgeControllerUserConfig(userPersistence: UserPersistence<UUID>,
                               userIndex: UserIndexService<UUID>) : UserController<UUID>(userPersistence, userIndex)

@Profile("ctrl-edge-topic")
@Controller
@MessageMapping("edge.topic")
class EdgeControllerTopicConfig(topicP: TopicPersistence<UUID>,
                                topicInd: TopicIndexService<UUID>,
                                topicSvc: PubSubTopicExchangeService<UUID, String>,
                                userP: UserPersistence<UUID>,
                                membershipP: MembershipPersistence<UUID>,
                                membershipInd: MembershipIndexService<UUID>) :
        TopicController<UUID, String>(topicP, topicInd, topicSvc, userP, membershipP, membershipInd, EmptyStringCodec())

@Profile("ctrl-edge-messaging")
@Controller
@MessageMapping("edge.com.demo.chat.test.messaging")
class EdgeControllerMessagingConfig(messageIdx: MessageIndexService<UUID, String>,
                                    msgPersist: MessagePersistence<UUID, String>,
                                    messaging: PubSubTopicExchangeService<UUID, String>) :
        MessagingController<UUID, String>(messageIdx, msgPersist, messaging)
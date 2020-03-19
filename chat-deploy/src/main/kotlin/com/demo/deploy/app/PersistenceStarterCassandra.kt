package com.demo.deploy.app

import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.config.ClusterConfigurationCassandra
import com.demo.chat.controller.service.KeyServiceController
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.IKeyService
import com.demo.deploy.config.*
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories
import org.springframework.stereotype.Controller
import java.util.*

// TODO SSo ooooooo  IDont know how to get our rsocket server
// to register with Consul, so I'm sending a tag instead
// using command line to drop in the port.
@SpringBootApplication(excludeName = ["com.demo.deploy"])
@EnableConfigurationProperties(CassandraProperties::class)
class ChatServiceCassandraApplication {
    val logger = LoggerFactory.getLogger(this::class.java)

    @Bean
    fun jackson(): JacksonModules = JacksonModules(JsonNodeAnyCodec, JsonNodeAnyCodec)

    @Bean
    fun serialization(): JacksonConfiguration = JacksonConfiguration()

    @Profile("client")
    @Bean
    fun keyPersistenceClient(clientFactory: PersistenceClientFactory): IKeyService<UUID> = clientFactory.keyClient()

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<ChatServiceCassandraApplication>(*args)
        }
    }
}


@Profile("cassandra-persistence")
@Configuration
class AppPersistenceFactory(keyService: IKeyService<UUID>,
                            userRepo: ChatUserRepository<UUID>,
                            topicRepo: TopicRepository<UUID>,
                            messageRepo: ChatMessageRepository<UUID>,
                            membershipRepo: TopicMembershipRepository<UUID>
) : CassandraPersistenceFactory<UUID>(keyService, userRepo, topicRepo, messageRepo, membershipRepo)

@Profile("cassandra-index")
@Configuration
class IndexCassandraIndexFactory(
        cassandra: ReactiveCassandraTemplate,
        userHandleRepo: ChatUserHandleRepository<UUID>,
        roomRepo: TopicRepository<UUID>,
        nameRepo: TopicByNameRepository<UUID>,
        byMemberRepo: TopicMembershipByMemberRepository<UUID>,
        byMemberOfRepo: TopicMembershipByMemberOfRepository<UUID>,
        byUserRepo: ChatMessageByUserRepository<UUID>,
        byTopicRepo: ChatMessageByTopicRepository<UUID>
) : CassandraIndexFactory<UUID>(cassandra, userHandleRepo, roomRepo, nameRepo, byMemberRepo, byMemberOfRepo, byUserRepo, byTopicRepo)

@Profile("cassandra-key")
@Controller
class AAppKeyServiceConfiguration(template: ReactiveCassandraTemplate) :
        KeyServiceController<UUID>(CassandraKeyServiceFactory(template, UUIDKeyGeneratorCassandra()).keyService())

@Configuration
@EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.repository.cassandra"])
class ConfigurationCassandra(props: CassandraProperties) : ClusterConfigurationCassandra(props)
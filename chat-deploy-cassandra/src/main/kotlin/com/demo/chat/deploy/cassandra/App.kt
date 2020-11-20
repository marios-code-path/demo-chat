package com.demo.chat.deploy.cassandra

import com.demo.chat.deploy.codec.UUIDKeyGeneratorCassandra
import com.demo.chat.deploy.config.CassandraIndexServiceConfiguration
import com.demo.chat.deploy.config.CassandraPersistenceServiceConfiguration
import com.demo.chat.deploy.config.SerializationConfiguration
import com.demo.chat.deploy.config.client.CoreServiceClientBeans
import com.demo.chat.deploy.config.client.CoreServiceClientFactory
import com.demo.chat.deploy.config.client.RSocketClientProperties
import com.demo.chat.deploy.config.controllers.IndexControllersConfiguration
import com.demo.chat.deploy.config.controllers.KeyControllersConfiguration
import com.demo.chat.deploy.config.controllers.PersistenceControllersConfiguration
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.IKeyService
import com.demo.chat.service.persistence.KeyServiceCassandra
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories
import java.util.*

@ConfigurationProperties("app.client.rsocket")
@ConstructorBinding
class AppRSocketClientProperties(
        override val key: String = "",
        override val index: String = "",
        override val persistence: String = "",
        override val messaging: String = "",
) : RSocketClientProperties

@Configuration
class AppRSocketClientConfiguration(clients: CoreServiceClientFactory) : CoreServiceClientBeans<UUID, String>(clients)

@EnableConfigurationProperties(AppRSocketClientProperties::class, CassandraProperties::class)
@SpringBootApplication(excludeName = ["com.demo.chat.deploy"])
@EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.repository.cassandra"])
@Import(RSocketRequesterAutoConfiguration::class,
        SerializationConfiguration::class,
        CoreServiceClientFactory::class)
class App {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service", name = ["index"])
    class IndexConfiguration {
        @Configuration
        class IndexServiceConfiguration(
                cassandra: ReactiveCassandraTemplate,
                userHandleRepo: ChatUserHandleRepository<UUID>,
                roomRepo: TopicRepository<UUID>,
                nameRepo: TopicByNameRepository<UUID>,
                byMemberRepo: TopicMembershipByMemberRepository<UUID>,
                byMemberOfRepo: TopicMembershipByMemberOfRepository<UUID>,
                byUserRepo: ChatMessageByUserRepository<UUID>,
                byTopicRepo: ChatMessageByTopicRepository<UUID>,
        ) : CassandraIndexServiceConfiguration<UUID>(cassandra, userHandleRepo, roomRepo, nameRepo, byMemberRepo, byMemberOfRepo, byUserRepo, byTopicRepo) {

            @Configuration
            class AppIndexControllers : IndexControllersConfiguration()
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service", name = ["persistence"])
    class PersistenceConfiguration {
        @Configuration
        class PersistenceServiceConfiguration(
                keyService: IKeyService<UUID>,
                userRepo: ChatUserRepository<UUID>,
                topicRepo: TopicRepository<UUID>,
                messageRepo: ChatMessageRepository<UUID>,
                membershipRepo: TopicMembershipRepository<UUID>,
        ) : CassandraPersistenceServiceConfiguration<UUID>(keyService, userRepo, topicRepo, messageRepo, membershipRepo)

        @Configuration
        class AppPersistenceControllerConfiguration : PersistenceControllersConfiguration()
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service", name = ["key"])
    class KeyConfiguration {
        @Bean
        fun keyService(t: ReactiveCassandraTemplate): IKeyService<UUID> =
                KeyServiceCassandra(t, UUIDKeyGeneratorCassandra())

        @Configuration
        class KeyControllers : KeyControllersConfiguration()
    }
}
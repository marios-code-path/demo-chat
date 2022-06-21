package com.demo.chat.deploy.cassandra

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.demo.chat.config.CoreClientBeans
import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.deploy.config.AstraConfiguration
import com.demo.chat.deploy.config.CassandraIndexServiceConfiguration
import com.demo.chat.deploy.config.CassandraPersistenceServiceConfiguration
import com.demo.chat.deploy.config.ContactPointConfiguration
import com.demo.chat.deploy.config.client.AppClientBeansConfiguration
import com.demo.chat.client.rsocket.config.CoreRSocketClients
import com.demo.chat.deploy.config.client.consul.ConsulRequesterFactory
import com.demo.chat.controller.config.IndexControllersConfiguration
import com.demo.chat.controller.config.KeyControllersConfiguration
import com.demo.chat.controller.config.PersistenceControllersConfiguration
import com.demo.chat.deploy.config.properties.AppRSocketProperties
import com.demo.chat.domain.serializers.DefaultChatJacksonModules
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.IKeyService
import com.demo.chat.service.persistence.KeyServiceCassandra
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories
import java.util.*

@Configuration
class AppRSocketClientBeansConfiguration(clients: CoreClientBeans<UUID, String, Map<String, String>>) :
    AppClientBeansConfiguration<UUID, String, Map<String, String>>(clients, ParameterizedTypeReference.forType(UUID::class.java))

@EnableConfigurationProperties(AppRSocketProperties::class)
@SpringBootApplication(excludeName = ["com.demo.chat.deploy"])
@EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.repository.cassandra"])
@Import(
    RSocketRequesterAutoConfiguration::class,
    DefaultChatJacksonModules::class,
    ConsulRequesterFactory::class,
    CoreRSocketClients::class
)
class App {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }

    @Configuration
    @Profile("cassandra-astra")
    class AstraClusterConfiguration(
        props: CassandraProperties,
        @Value("\${astra.secure-connect-bundle}")
        connectPath: String,
    ) : AstraConfiguration(props, connectPath)

    @Configuration
    @Profile("cassandra-default", "default")
    class DefaultClusterConfiguration(
        props: CassandraProperties
    ) : ContactPointConfiguration(props)

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"])
    class IndexConfiguration {
        @Configuration
        class CassandraBackedIndexBeans(
            cassandra: ReactiveCassandraTemplate,
            userHandleRepo: ChatUserHandleRepository<UUID>,
            roomRepo: TopicRepository<UUID>,
            nameRepo: TopicByNameRepository<UUID>,
            byMemberRepo: TopicMembershipByMemberRepository<UUID>,
            byMemberOfRepo: TopicMembershipByMemberOfRepository<UUID>,
            byUserRepo: ChatMessageByUserRepository<UUID>,
            byTopicRepo: ChatMessageByTopicRepository<UUID>,
        ) : CassandraIndexServiceConfiguration<UUID>(
            cassandra,
            userHandleRepo,
            roomRepo,
            nameRepo,
            byMemberRepo,
            byMemberOfRepo,
            byUserRepo,
            byTopicRepo,
            UUID::fromString
        ) {

            @Configuration
            class AppIndexControllers : IndexControllersConfiguration()
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"])
    class PersistenceConfiguration {
        @Configuration
        class PersistenceConfiguration(
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
    @ConditionalOnProperty(prefix = "app.service.core", name = ["key"])
    class KeyConfiguration {
        @Configuration
        class CassandraKeyBeans(val t: ReactiveCassandraTemplate) : KeyServiceBeans<UUID> {
            override fun keyService(): IKeyService<UUID> = KeyServiceCassandra(t, Uuids::timeBased)
        }

        @Configuration
        class KeyControllers : KeyControllersConfiguration()
    }
}
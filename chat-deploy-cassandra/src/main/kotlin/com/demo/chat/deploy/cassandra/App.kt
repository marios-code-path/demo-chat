package com.demo.chat.deploy.cassandra

import com.datastax.oss.driver.api.core.config.DefaultDriverOption
import com.datastax.oss.driver.api.core.uuid.Uuids
import com.demo.chat.deploy.config.CassandraIndexServiceConfiguration
import com.demo.chat.deploy.config.CassandraPersistenceServiceConfiguration
import com.demo.chat.deploy.config.AstraConfiguration
import com.demo.chat.deploy.config.JacksonConfiguration
import com.demo.chat.deploy.config.client.CoreServiceClientBeans
import com.demo.chat.deploy.config.client.CoreServiceClientFactory
import com.demo.chat.deploy.config.client.consul.ConsulRequesterFactory
import com.demo.chat.deploy.config.controllers.core.IndexControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.KeyControllersConfiguration
import com.demo.chat.deploy.config.controllers.core.PersistenceControllersConfiguration
import com.demo.chat.deploy.config.core.KeyServiceFactory
import com.demo.chat.deploy.config.properties.AppConfigurationProperties
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.IKeyService
import com.demo.chat.service.persistence.KeyServiceCassandra
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer
import org.springframework.boot.autoconfigure.cassandra.DriverConfigLoaderBuilderCustomizer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.*
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories
import java.nio.file.Paths
import java.util.*

@Configuration
class AppRSocketClientConfiguration(clients: CoreServiceClientFactory) : CoreServiceClientBeans<UUID, String, Map<String, String>>(clients)

@EnableConfigurationProperties(AppConfigurationProperties::class)
@SpringBootApplication(excludeName = ["com.demo.chat.deploy"])
@EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.repository.cassandra"])
@Import(RSocketRequesterAutoConfiguration::class,
        JacksonConfiguration::class,
        ConsulRequesterFactory::class,
        CoreServiceClientFactory::class)
class App {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<App>(*args)
        }
    }

    @Configuration
    @Profile("cassandra-astra")
    class ClusterConfiguration(
            props: CassandraProperties,
            @Value("\${astra.secure-connect-bundle:none}")
            connectPath: String,
    ) : AstraConfiguration(props, connectPath)

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"])
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
        ) : CassandraIndexServiceConfiguration<UUID>(cassandra, userHandleRepo, roomRepo, nameRepo, byMemberRepo, byMemberOfRepo, byUserRepo, byTopicRepo, UUID::fromString) {

            @Configuration
            class AppIndexControllers : IndexControllersConfiguration()
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"])
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
    @ConditionalOnProperty(prefix = "app.service.core", name = ["key"])
    class KeyConfiguration {
        @Configuration
        class CassandraKeyServiceFactory(val t: ReactiveCassandraTemplate) : KeyServiceFactory<UUID> {
            override fun keyService(): IKeyService<UUID> = KeyServiceCassandra(t,Uuids::timeBased )
        }

        @Configuration
        class KeyControllers : KeyControllersConfiguration()
    }
}
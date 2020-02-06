package com.demo.chat.app

import com.demo.chat.config.*
import com.demo.chat.config.app.AppIndex
import com.demo.chat.config.app.AppPersistence
import com.demo.chat.config.app.AppTopicMessaging
import com.demo.chat.controller.rsocket.KeyServiceRSocket
import com.demo.chat.controller.service.KeyServiceController
import com.demo.chat.service.IKeyService
import com.demo.chatevents.config.ConfigurationPropertiesTopicRedis
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.*
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories
import org.springframework.stereotype.Controller
import java.util.*

/*

configuration {
    enable-cassandra-cluster
    enable-cassandra-persistence
    enable-redis-topics
    enable-
}
 */
@SpringBootApplication
@ComponentScan(excludeFilters = [
    ComponentScan.Filter(type = FilterType.ANNOTATION, value = [ExcludeFromTests::class])
])
class ChatServiceRSocketApplication {

    @Configuration
    class AppSerializationConfigurationJackson : SerializationConfigurationJackson()

    @Profile("cassandra-cluster")
    @Configuration
    class AppCassandraClusterConfiguration(cassandraProps: ConfigurationPropertiesCassandra) : CassandraConfiguration(cassandraProps)

    @Profile("redis-topics")
    @Configuration
    class AppRedisConfiguration(props: ConfigurationPropertiesTopicRedis,
                                mapper: ObjectMapper) : ConnectionConfigurationRedis(props, mapper)

    @Profile("cassandra-key")
    @Configuration
    @Controller
    class AppKeyServiceConfiguration(template: ReactiveCassandraTemplate) :
            KeyServiceController<UUID>(KeyServiceConfigurationCassandra(template, UUIDKeyGeneratorCassandra()).keyService())

    @Profile("cassandra-persistence")
    @Configuration
    class AppPersistenceConfiguration : AppPersistence("cassandra-persistence")

    @Profile("cassandra-index")
    @Configuration
    class AppIndexConfiguration : AppIndex("cassandra-index")

    @Configuration
    class AppTopicMessagingConfiguration : AppTopicMessaging("memory-topics")

    @Configuration
    @EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.repository.cassandra"])
    class RepositoryConfigurationCassandra
}

@ConstructorBinding
@ConfigurationProperties("redis-topics")
data class ConfigurationPropertiesRedisTopics(override val host: String = "127.0.0.1",
                                              override val port: Int = 6379) : ConfigurationPropertiesTopicRedis


@ConstructorBinding
@ConfigurationProperties("cassandra-repo")
data class CassandraProperties(override val contactPoints: String,
                               override val port: Int,
                               override val keyspace: String,
                               override val basePackages: String,
                               override val jmxReporting: Boolean) : ConfigurationPropertiesCassandra

@EnableConfigurationProperties(CassandraProperties::class, ConfigurationPropertiesRedisTopics::class)
@Configuration
class ServiceDiscoveryConfiguration {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<ChatServiceRSocketApplication>(*args)
        }
    }
}

annotation class ExcludeFromTests
package com.demo.chat

import com.demo.chat.config.*
import com.demo.chat.config.app.AppIndex
import com.demo.chat.config.app.AppPersistence
import com.demo.chat.config.app.AppTopicMessaging
import com.demo.chatevents.config.ConfigurationPropertiesTopicRedis
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.*
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import java.util.*

@SpringBootApplication
@ComponentScan(excludeFilters = [
    ComponentScan.Filter(type = FilterType.ANNOTATION, value = [ExcludeFromTests::class])
])
class ChatServiceRSocketApplication {

    @Configuration
    class AppSerializationConfigurationJackson : SerializationConfigurationJackson()

    @Configuration
    class AppCassandraConfiguration(cassandraProps: ConfigurationPropertiesCassandra) : CassandraConfiguration(cassandraProps)

    @Configuration
    class AppRedisConfiguration(props: ConfigurationPropertiesTopicRedis,
                                mapper: ObjectMapper) : ConnectionConfigurationRedis(props, mapper)

    @Configuration
    class AppKeyPersistenceConfiguration(template: ReactiveCassandraTemplate) : KeyPersistenceConfigurationCassandra<UUID>(template, UUIDKeyGeneratorCassandra())


    @Configuration
    class AppPersistenceConfiguration : AppPersistence("cassandra")

    @Configuration
    class AppIndexConfiguration : AppIndex("cassandra")

    @Configuration
    class AppTopicMessagingConfiguration : AppTopicMessaging("memory")

    // TODO implement rsocket exposure from here
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
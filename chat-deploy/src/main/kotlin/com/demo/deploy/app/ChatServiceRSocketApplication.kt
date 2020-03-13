package com.demo.deploy.app

import com.demo.chat.client.rsocket.*
import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.config.*
import com.demo.deploy.config.app.AppIndex
import com.demo.deploy.config.app.AppPersistence
import com.demo.deploy.config.app.AppTopicMessaging
import com.demo.chat.controller.service.KeyServiceController
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.service.IKeyService
import com.demo.chat.service.PersistenceStore
import com.demo.chatevents.config.ConfigurationPropertiesRedisCluster
import com.demo.chatevents.config.ConfigurationTopicRedis
import com.demo.deploy.config.*
import com.ecwid.consul.v1.ConsulClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.context.annotation.*
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.stereotype.Controller
import reactor.core.publisher.Mono
import java.util.*
import java.util.function.Supplier

/* TODO USE Command line arguments from spring boot app startup to set runtime
        behavior instead of profiles!
configuration {
    enable-cassandra-cluster
    enable-cassandra-persistence
    enable-redis-topics
    enable-
} // How to register individual rsocket controller
 */
@SpringBootApplication
@ComponentScan(excludeFilters = [
    ComponentScan.Filter(type = FilterType.ANNOTATION, value = [ExcludeFromTests::class])
])
class ChatServiceRSocketApplication {
    val logger = LoggerFactory.getLogger(this::class.java)

    @Bean
    fun KickStarter(ctx: AnnotationConfigApplicationContext): ApplicationRunner = ApplicationRunner {args ->
        ctx.registerBean(JacksonModules::class.java, jacksonSupplier())
        ctx.registerBean(SerializationConfigurationJackson::class.java, serializationSupplier())
    }

    fun jacksonSupplier() = Supplier {
        JacksonModules(JsonNodeAnyCodec, JsonNodeAnyCodec)
    }

    fun serializationSupplier() = Supplier {
        SerializationConfigurationJackson()
    }




    @Profile("redis-topics")
    @Configuration
    class AppRedisConfiguration(props: ConfigurationPropertiesRedisCluster) : ConnectionConfigurationRedis(props)

    @Profile("redis-cluster")
    @Bean
    fun configurationTopicRedis(factory: ReactiveRedisConnectionFactory,
                                mapper: ObjectMapper): ConfigurationTopicRedis =
            ConfigurationTopicRedis(factory, mapper)

    @Profile("cassandra-key-uuid")
    @Configuration
    @Controller
    class AppKeyServiceConfiguration(template: ReactiveCassandraTemplate) :
            KeyServiceController<UUID>(KeyServiceConfigurationCassandra(template, UUIDKeyGeneratorCassandra()).keyService())

    @Configuration
    class RSocketRequesterAutoConfig : RSocketRequesterAutoConfiguration()

    @Profile("client")
    @Configuration
    class AppClient(val builder: RSocketRequester.Builder,
                    client: ConsulClient,
                    props: ConsulDiscoveryProperties) {
        val discovery: ReactiveDiscoveryClient = ConsulReactiveDiscoveryClient(client, props)
        val logger = LoggerFactory.getLogger(this::class.java)

        fun requester(servicePrefix: String): RSocketRequester = discovery
                .getInstances("${servicePrefix}-service-rsocket")
                .map {
                    val rsocketPort = it.port - 1

                    builder
                            .connectTcp(it.host, rsocketPort)
                            .log()
                            .block()!!
                }
                .switchIfEmpty(Mono.error(RuntimeException("Cannot discover $servicePrefix Service")))
                .blockFirst()!!

        @Profile("client-key")
        @Bean
        fun <T> keyClient(): IKeyService<T> = KeyClient(requester("key"))

        @Profile("client-user")
        @Bean
        fun <T> userClient(): PersistenceStore<T, User<T>> = UserPersistenceClient(requester("user"))

        @Profile("client-message")
        @Bean
        fun <T, V> messageClient(): PersistenceStore<T, Message<T, V>> = MessagePersistenceClient(requester("message"))

        @Profile("client-message-topic")
        @Bean
        fun <T> messageTopicClient(): PersistenceStore<T, MessageTopic<T>> = MessageTopicPersistenceClient(requester("message-topic"))

        @Profile("client-topic-membership")
        @Bean
        fun <T> topicMembershipClient(): PersistenceStore<T, TopicMembership<T>> = MembershipPersistenceClient(requester("topic-membership"))
    }

    @Profile("cassandra-persistence")
    @Configuration
    class AppPersistenceConfiguration : AppPersistence("cassandra-persistence")

    @Profile("cassandra-index")
    @Configuration
    class AppIndexConfiguration : AppIndex("cassandra-index")

    @Configuration
    class AppTopicMessagingConfiguration : AppTopicMessaging("topics")
}

@Profile("redis-cluster")
@ConstructorBinding
@ConfigurationProperties("redis-topics")
data class ConfigurationPropertiesRedisTopics(override val host: String = "127.0.0.1",
                                              override val port: Int = 6379) : ConfigurationPropertiesRedisCluster

@Profile("cassandra-cluster")
@ConstructorBinding
@ConfigurationProperties("spring.data.cassandra")
data class CassandraProperties(override val contactPoints: String,
                               override val port: Int,
                               override val keyspacename: String,
                               override val basePackages: String,
                               override val jmxReporting: Boolean) : ConfigurationPropertiesCassandra

@EnableConfigurationProperties(CassandraProperties::class, ConfigurationPropertiesRedisTopics::class)
class DiscoveryConfiguration {

}
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
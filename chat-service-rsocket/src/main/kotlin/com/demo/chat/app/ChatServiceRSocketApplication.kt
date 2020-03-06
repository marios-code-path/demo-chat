package com.demo.chat.app

import com.demo.chat.client.rsocket.KeyClient
import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.config.*
import com.demo.chat.config.app.AppIndex
import com.demo.chat.config.app.AppPersistence
import com.demo.chat.config.app.AppTopicMessaging
import com.demo.chat.controller.service.KeyServiceController
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.service.IKeyService
import com.demo.chatevents.config.ConfigurationPropertiesTopicRedis
import com.ecwid.consul.v1.ConsulClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
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
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler
import org.springframework.stereotype.Controller
import reactor.core.publisher.Mono
import java.util.*

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

   @Configuration
   class AppJacksonModules : JacksonModules(JsonNodeAnyCodec, JsonNodeAnyCodec)

   @Configuration
   class AppSerializationConfigurationJackson : SerializationConfigurationJackson()

    @Bean
    fun serverMessageHandler(strategies: RSocketStrategies): RSocketMessageHandler {
        val handler = RSocketMessageHandler()
        handler.rSocketStrategies = strategies
        handler.afterPropertiesSet()
        return handler
    }
    @Profile("cassandra-cluster")
    @Configuration
    class ClusterConfigurationCassandra(cassandraProps: ConfigurationPropertiesCassandra) : CassandraConfiguration(cassandraProps)

    @Configuration
    @EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.repository.cassandra"])
    class RepositoryConfigurationCassandra

    @Profile("redis-topics")
    @Configuration
    class AppRedisConfiguration(props: ConfigurationPropertiesTopicRedis,
                                mapper: ObjectMapper) : ConnectionConfigurationRedis(props, mapper)

    @Profile("cassandra-key")
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
                .switchIfEmpty(Mono.error(RuntimeException("Cannot discover Key Service")))
                .blockFirst()!!

        @Profile("client-key")
        @Bean
        fun run(svc: IKeyService<UUID>): ApplicationRunner = ApplicationRunner {
            svc.key(UUID::class.java)
                    .doOnNext {
                        logger.info("KEY FOUND: ${it.id}")
                    }
                    .block()
        }

        @Profile("client-key")
        @Bean
        fun keyClient(): IKeyService<UUID> = KeyClient(requester("key"))
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

@ConstructorBinding
@ConfigurationProperties("redis-topics")
data class ConfigurationPropertiesRedisTopics(override val host: String = "127.0.0.1",
                                              override val port: Int = 6379) : ConfigurationPropertiesTopicRedis

@ConstructorBinding
@ConfigurationProperties("spring.data.cassandra")
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